package com.zhangxuan.runruncampus.courier;

import com.zhangxuan.runruncampus.order.OrderProofStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class CourierCertificationMaintenance {
    private static final Logger LOG = LoggerFactory.getLogger(CourierCertificationMaintenance.class);
    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int PHOTO_RETENTION_DAYS = 30;

    private final CourierCertificationRepository certifications;
    private final OrderProofStorage storage;
    private final EvidenceDeletionAuditRepository deletionAudits;
    private final EvidenceHoldRepository holds;

    public CourierCertificationMaintenance(CourierCertificationRepository certifications,
                                            OrderProofStorage storage) {
        this.certifications = certifications;
        this.storage = storage;
        this.deletionAudits = null;
        this.holds = null;
    }

    public CourierCertificationMaintenance(CourierCertificationRepository certifications,
                                            OrderProofStorage storage,
                                            EvidenceDeletionAuditRepository deletionAudits) {
        this.certifications = certifications; this.storage = storage; this.deletionAudits = deletionAudits;
        this.holds = null;
    }

    public CourierCertificationMaintenance(CourierCertificationRepository certifications, OrderProofStorage storage,
                                            EvidenceDeletionAuditRepository deletionAudits, EvidenceHoldRepository holds) {
        this.certifications=certifications; this.storage=storage; this.deletionAudits=deletionAudits; this.holds=holds;
    }

    @Scheduled(cron = "${app.courier-certification.maintenance-cron:0 0 9 * * *}", zone = "Asia/Shanghai")
    @Transactional
    public void run() {
        run(LocalDate.now(CAMPUS_ZONE));
    }

    void run(LocalDate today) {
        cleanPhotos(today);
        retryFailedDeletes();
    }

    private void retryFailedDeletes() {
        if (deletionAudits == null) return;
        for (EvidenceDeletionAudit audit : deletionAudits.findDue(java.time.Instant.now())) {
            String key = audit.getResourceStorageKey();
            if (key == null) continue;
            audit.deleting();
            try { storage.delete(key); audit.succeeded(); }
            catch (IOException e) { audit.failed("STORAGE_DELETE_FAILED"); }
            deletionAudits.save(audit);
        }
    }

    private void cleanPhotos(LocalDate today) {
        LocalDate cutoff = today.minusDays(PHOTO_RETENTION_DAYS);
        var reviewedBefore = cutoff.plusDays(1).atStartOfDay(CAMPUS_ZONE).toInstant();
        for (CourierCertification certification : certifications.findApprovedPhotosToClean(reviewedBefore)) {
            if (delete(certification.getApprovedPhotoKey(), certification.getId())) {
                certification.clearApprovedPhoto();
            }
        }
        for (CourierCertification certification : certifications.findRejectedPhotosToClean(reviewedBefore)) {
            if (delete(certification.getSubmittedPhotoKey(), certification.getId())) {
                certification.clearSubmittedPhoto();
            }
        }
    }

    private boolean delete(String key, Long certificationId) {
        if (holds != null && holds.existsActive(certificationId)) return false;
        EvidenceDeletionAudit audit = null;
        if (deletionAudits != null) {
            var certification = certifications.findById(certificationId).orElse(null);
            if (certification != null) {
                audit = deletionAudits.save(new EvidenceDeletionAudit(certification, "STUDENT_CARD",
                        certification.getApprovedPhotoResourceId() != null ? certification.getApprovedPhotoResourceId() :
                                certification.getSubmittedPhotoResourceId(), key, java.time.Instant.now()));
                audit.deleting();
            }
        }
        try {
            storage.delete(key);
            if (audit != null) { audit.succeeded(); deletionAudits.save(audit); }
            return true;
        } catch (IOException exception) {
            if (audit != null) { audit.failed("STORAGE_DELETE_FAILED"); deletionAudits.save(audit); }
            LOG.warn("学生证材料清理失败，certificationId={}", certificationId, exception);
            return false;
        }
    }
}
