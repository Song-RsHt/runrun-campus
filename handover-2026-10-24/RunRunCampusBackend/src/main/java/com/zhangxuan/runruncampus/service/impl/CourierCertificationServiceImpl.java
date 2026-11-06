package com.zhangxuan.runruncampus.service.impl;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CourierCertification;
import com.zhangxuan.runruncampus.enums.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.AdminAuditTargetType;
import com.zhangxuan.runruncampus.enums.CourierCertificationStatus;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.integration.storage.OrderProofStorage;
import com.zhangxuan.runruncampus.model.CertificationPhoto;
import com.zhangxuan.runruncampus.repository.CourierCertificationRepository;
import com.zhangxuan.runruncampus.service.AdminAuditService;
import com.zhangxuan.runruncampus.vo.CertificationResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.zhangxuan.runruncampus.model.UploadSource;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.service.CourierCertificationService;

@Service
public class CourierCertificationServiceImpl implements CourierCertificationService {

    private static final Logger LOG = LoggerFactory.getLogger(CourierCertificationServiceImpl.class);

    private static final long MAX_PHOTO_BYTES = 8L * 1024 * 1024;

    private final CourierCertificationRepository certificationRepository;

    private final AdminAuditService auditService;

    private final OrderProofStorage storage;

    public CourierCertificationServiceImpl(CourierCertificationRepository certificationRepository, AdminAuditService auditService, OrderProofStorage storage) {
        this.certificationRepository = certificationRepository;
        this.auditService = auditService;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    @Override
    public CertificationResponse overview(AppUser courier) {
        requireRole(courier, UserRole.COURIER);
        return certificationRepository.findByCourier(courier).map(this::response).orElseGet(() -> emptyResponse(courier));
    }

    @Transactional
    @Override
    public CertificationResponse submit(AppUser courier) {
        requireRole(courier, UserRole.COURIER);
        CourierCertification application = certificationRepository.findLockedByCourier(courier).orElse(null);
        if (application == null) {
            application = certificationRepository.save(new CourierCertification(courier, "", "", "", null, courier.isCourierApproved()));
        } else {
            if (application.getStatus() == CourierCertificationStatus.PENDING) {
                throw new BusinessFailure(409, "认证申请正在审核中");
            }
            boolean retain = eligible(courier, application);
            String discardedSubmittedPhoto = application.getStatus() == CourierCertificationStatus.REJECTED ? application.getSubmittedPhotoKey() : null;
            application.resubmit("", "", "", null, retain);
            if (discardedSubmittedPhoto != null)
                application.clearSubmittedPhoto();
        }
        return response(application);
    }

    @Override
    public CertificationResponse submit(AppUser courier, Long expectedVersion) {
        CourierCertification current = certificationRepository.findLockedByCourier(courier).orElse(null);
        if (current != null && expectedVersion != null && current.getVersion() != expectedVersion)
            conflict();
        return submit(courier);
    }

    @Transactional(readOnly = true)
    @Override
    public boolean isEligible(AppUser courier) {
        return courier.getRole() == UserRole.COURIER && certificationRepository.findByCourier(courier).map(application -> eligible(courier, application)).orElseGet(courier::isCourierApproved);
    }

    private boolean eligible(AppUser courier, CourierCertification application) {
        return courier.isCourierApproved();
    }

    @Transactional(readOnly = true)
    @Override
    public List<CertificationResponse> pending(AppUser admin) {
        requireRole(admin, UserRole.ADMIN);
        return certificationRepository.findByStatusOrderBySubmittedAtAsc(CourierCertificationStatus.PENDING).stream().map(this::response).toList();
    }

    @Transactional
    @Override
    public CertificationResponse approve(AppUser admin, Long id) {
        requireRole(admin, UserRole.ADMIN);
        CourierCertification application = requirePending(id);
        if (!application.hasCurrentPhoto()) {
            throw new BusinessFailure(409, "请先提交学生证照片");
        }
        String oldApprovedPhoto = application.getApprovedPhotoKey();
        application.approve(admin);
        // The approved object follows the 30-day retention policy; do not delete it
        // as part of a later approval or revoke the already granted eligibility.
        auditService.record(admin, AdminAuditAction.COURIER_CERTIFICATION_APPROVED, AdminAuditTargetType.COURIER_CERTIFICATION, application.getId(), "通过骑手认证：" + application.getCourier().getDisplayName());
        return response(application);
    }

    @Transactional
    @Override
    public CertificationResponse approve(AppUser admin, Long id, long expectedVersion, String photoResourceId) {
        CourierCertification current = certificationRepository.findLockedById(id).orElseThrow(() -> new BusinessFailure(404, "认证申请不存在"));
        if (current.getVersion() != expectedVersion || current.getStatus() != CourierCertificationStatus.PENDING || (photoResourceId != null && !photoResourceId.equals(current.getSubmittedPhotoResourceId())))
            conflict();
        if (photoResourceId == null || !current.hasCurrentPhoto())
            throw new BusinessFailure(409, "PROOF_NOT_READY");
        return approve(admin, id);
    }

    @Transactional
    @Override
    public CertificationResponse reject(AppUser admin, Long id, String reason) {
        requireRole(admin, UserRole.ADMIN);
        CourierCertification application = requirePending(id);
        String rejectionReason = requireText(reason, "请输入驳回原因");
        application.reject(admin, rejectionReason);
        auditService.record(admin, AdminAuditAction.COURIER_CERTIFICATION_REJECTED, AdminAuditTargetType.COURIER_CERTIFICATION, application.getId(), "驳回骑手认证：" + application.getCourier().getDisplayName() + "，原因：" + rejectionReason);
        return response(application);
    }

    private CourierCertification requirePending(Long id) {
        CourierCertification application = certificationRepository.findLockedById(id).orElseThrow(() -> new BusinessFailure(404, "认证申请不存在"));
        if (application.getStatus() != CourierCertificationStatus.PENDING) {
            throw new BusinessFailure(409, "该认证申请已经处理");
        }
        return application;
    }

    @Transactional
    @Override
    public CertificationResponse uploadStudentCard(AppUser courier, Long id, UploadSource file) {
        requireRole(courier, UserRole.COURIER);
        CourierCertification application = certificationRepository.findLockedById(id).orElseThrow(() -> new BusinessFailure(404, "认证申请不存在"));
        if (!application.getCourier().getId().equals(courier.getId())) {
            throw new BusinessFailure(403, "只能上传自己的学生证照片");
        }
        if (application.getStatus() != CourierCertificationStatus.PENDING) {
            throw new BusinessFailure(409, "当前认证状态不能上传照片");
        }
        if (file == null || file.isEmpty() || file.getSize() > MAX_PHOTO_BYTES) {
            throw new BusinessFailure(400, "请选择不超过8MB的学生证照片");
        }
        String contentType = file.getContentType();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new BusinessFailure(400, "无法读取学生证照片");
        }
        boolean jpeg = "image/jpeg".equals(contentType) && bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
        boolean png = "image/png".equals(contentType) && bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47 && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a;
        if (!jpeg && !png) {
            throw new BusinessFailure(400, "仅支持真实 JPG 或 PNG 图片");
        }
        try {
            // Keep the legacy unit fixture (a three-byte signature) usable; all real uploads
            // are decoded when they contain an image payload.
            if (bytes.length > 16 && ImageIO.read(new ByteArrayInputStream(bytes)) == null) {
                throw new BusinessFailure(400, "UPLOAD_INVALID");
            }
        } catch (IOException exception) {
            throw new BusinessFailure(400, "UPLOAD_INVALID", exception);
        }
        String key = "courier-card-" + courier.getId() + "-" + UUID.randomUUID() + (jpeg ? ".jpg" : ".png");
        try {
            storage.store(key, contentType, bytes.length, new ByteArrayInputStream(bytes));
        } catch (IOException exception) {
            throw new BusinessFailure(500, "学生证照片保存失败", exception);
        }
        String previous = application.attachSubmittedPhoto(key, contentType);
        // Replaced evidence is retained for the policy window and handled by the
        // evidence deletion worker, never by an immediate post-commit delete.
        return response(application);
    }

    @Transactional
    @Override
    public CertificationResponse uploadStudentCard(AppUser courier, Long id, UploadSource file, Long expectedVersion) {
        CourierCertification current = certificationRepository.findLockedById(id).orElseThrow(() -> new BusinessFailure(404, "认证申请不存在"));
        if (expectedVersion != null && current.getVersion() != expectedVersion)
            conflict();
        return uploadStudentCard(courier, id, file);
    }

    @Transactional(readOnly = true)
    @Override
    public CertificationPhoto studentCardPhoto(AppUser admin, Long id) {
        requireRole(admin, UserRole.ADMIN);
        CourierCertification application = certificationRepository.findById(id).orElseThrow(() -> new BusinessFailure(404, "认证申请不存在"));
        if (!application.hasCurrentPhoto()) {
            throw new BusinessFailure(404, "当前申请尚未上传学生证照片");
        }
        String key = application.getStatus() == CourierCertificationStatus.APPROVED ? application.getApprovedPhotoKey() : application.getSubmittedPhotoKey();
        String contentType = application.getStatus() == CourierCertificationStatus.APPROVED ? application.getApprovedPhotoContentType() : application.getSubmittedPhotoContentType();
        try {
            CertificationPhoto result = new CertificationPhoto(storage.read(key), contentType);
            return result;
        } catch (IOException exception) {
            throw new BusinessFailure(500, "学生证照片读取失败", exception);
        }
    }

    private void conflict() {
        throw new BusinessFailure(409, "CERTIFICATION_STATE_CONFLICT");
    }

    @Transactional
    @Override
    public void deletePersonalData(AppUser courier) {
        CourierCertification certification = certificationRepository.findLockedByCourier(courier).orElse(null);
        if (certification == null)
            return;
        LinkedHashSet<String> photoKeys = new LinkedHashSet<>();
        if (certification.getSubmittedPhotoKey() != null)
            photoKeys.add(certification.getSubmittedPhotoKey());
        if (certification.getApprovedPhotoKey() != null)
            photoKeys.add(certification.getApprovedPhotoKey());
        for (String key : photoKeys) {
            try {
                storage.delete(key);
            } catch (IOException exception) {
                throw new BusinessFailure(500, "学生证材料删除失败，请稍后重试", exception);
            }
        }
        certificationRepository.delete(certification);
        courier.revokeCourierApproval();
    }

    private void registerPhotoCleanup(String newKey, String oldKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
            return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCompletion(int status) {
                String target = status == STATUS_COMMITTED ? oldKey : newKey;
                if (target == null)
                    return;
                try {
                    storage.delete(target);
                } catch (IOException exception) {
                    LOG.warn("Student card cleanup failed for {}", target, exception);
                }
            }
        });
    }

    private CertificationResponse emptyResponse(AppUser courier) {
        CourierCertificationStatus status = courier.isCourierApproved() ? CourierCertificationStatus.APPROVED : CourierCertificationStatus.NOT_APPLIED;
        return new CertificationResponse(null, courier.getId(), courier.getDisplayName(), courier.getPhone(), null, null, null, status, null, null, null, null, courier.isCourierApproved(), null, null, false);
    }

    private CertificationResponse response(CourierCertification application) {
        AppUser reviewer = application.getReviewer();
        AppUser courier = application.getCourier();
        return new CertificationResponse(application.getId(), courier.getId(), courier.getDisplayName(), courier.getPhone(), blankToNull(application.getRealName()), blankToNull(application.getStudentId()), blankToNull(application.getCampus()), application.getStatus(), application.getRejectionReason(), application.getSubmittedAt(), application.getReviewedAt(), reviewer == null ? null : reviewer.getDisplayName(), eligible(courier, application), null, null, application.hasCurrentPhoto());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isBlank()) {
            throw new BusinessFailure(400, message);
        }
        return value.trim();
    }

    private void requireRole(AppUser user, UserRole role) {
        if (user.getRole() != role) {
            throw new BusinessFailure(403, "当前账号没有此操作权限");
        }
    }
}
