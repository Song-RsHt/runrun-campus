package com.zhangxuan.runrunv2.service.impl;

import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.integration.idempotency.OrderIdempotency;
import com.zhangxuan.runrunv2.integration.storage.V2StudentCardStorage;
import com.zhangxuan.runrunv2.mapper.CourierCertificationV2Mapper;
import com.zhangxuan.runrunv2.model.UploadSource;
import com.zhangxuan.runrunv2.service.CourierCertificationV2Service;
import com.zhangxuan.runrunv2.service.CourierEvidenceGovernanceService;
import com.zhangxuan.runrunv2.service.IdentityService;
import com.zhangxuan.runrunv2.vo.CourierCertificationVO;
import com.zhangxuan.runrunv2.vo.IdentityAccountVO;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 旧系统骑手资质业务实现；通过注入的业务契约及数据访问组件执行，事务边界保留在本类。
 *
 * <p>隔离 v2 资质业务实现；仅操作新系统表，不访问 legacy 数据。
 */
@Service
public class CourierCertificationV2ServiceImpl implements CourierCertificationV2Service {

    private static final long MAX_BYTES = 8L * 1024 * 1024;

    private final CourierCertificationV2Mapper mapper;
    private final IdentityService identity;
    private final V2StudentCardStorage storage;
    private final OrderIdempotency idempotency;
    private final CourierEvidenceGovernanceService governance;

    public CourierCertificationV2ServiceImpl(
            CourierCertificationV2Mapper mapper,
            IdentityService identity,
            V2StudentCardStorage storage,
            OrderIdempotency idempotency,
            CourierEvidenceGovernanceService governance) {
        this.mapper = mapper;
        this.identity = identity;
        this.storage = storage;
        this.idempotency = idempotency;
        this.governance = governance;
    }

    @Transactional
    @Override
    public CourierCertificationVO apply(String token, IdentityService identity, String requestId) {
        IdentityAccountVO account = identity.authorizeForWrite(token, "COURIER", false);
        long courier = Long.parseLong(account.id());
        lockActor(courier);
        CourierCertificationVO replay =
                idempotency.replay(
                        Long.toString(courier),
                        "courier-certification:apply",
                        requestId,
                        "apply",
                        CourierCertificationVO.class);
        if (replay != null) {
            return replay;
        }
        var rows = mapper.lockCourierCertification(courier);
        if (rows.isEmpty()) {
            mapper.insertPendingCertification(courier);
        } else {
            var row = rows.get(0);
            if ("PENDING".equals(row.get("status"))) {
                throw new IdentityFailure(409, "CERTIFICATION_STATE_CONFLICT");
            }
            mapper.resubmitCertification(courier);
        }
        long id = mapper.findCertificationId(courier);
        event(id, courier, "COURIER", "SUBMITTED", requestId);
        CourierCertificationVO result = get(courier, id);
        idempotency.finish(
                Long.toString(courier),
                "courier-certification:apply",
                requestId,
                "COURIER_CERTIFICATION",
                Long.toString(id),
                result);
        return result;
    }

    @Transactional
    @Override
    public CourierCertificationVO upload(
            String token,
            IdentityService identity,
            long id,
            long expectedVersion,
            String requestId,
            UploadSource file) {
        IdentityAccountVO account = identity.authorizeForWrite(token, "COURIER", false);
        long courier = Long.parseLong(account.id());
        lockActor(courier);
        byte[] bytes;
        try {
            bytes = file == null ? new byte[0] : file.getBytes();
        } catch (IOException e) {
            throw new IdentityFailure(400, "UPLOAD_INVALID");
        }
        String contentHash = digest(bytes);
        CourierCertificationVO replay =
                idempotency.replay(
                        Long.toString(courier),
                        "courier-certification:upload",
                        requestId,
                        id + ":" + expectedVersion + ":" + contentHash,
                        CourierCertificationVO.class);
        if (replay != null) {
            return replay;
        }
        var row = locked(id);
        if (((Number) row.get("courier_id")).longValue() != courier
                || !"PENDING".equals(row.get("status"))) {
            throw new IdentityFailure(409, "CERTIFICATION_STATE_CONFLICT");
        }
        if (((Number) row.get("version")).longValue() != expectedVersion
                || file == null
                || file.isEmpty()
                || file.getSize() > MAX_BYTES) {
            throw new IdentityFailure(400, "UPLOAD_INVALID");
        }
        String type = file.getContentType();
        boolean jpeg =
                "image/jpeg".equals(type)
                        && bytes.length >= 3
                        && (bytes[0] & 255) == 255
                        && (bytes[1] & 255) == 216
                        && (bytes[2] & 255) == 255;
        boolean png =
                "image/png".equals(type)
                        && bytes.length >= 8
                        && (bytes[0] & 255) == 137
                        && bytes[1] == 80
                        && bytes[2] == 78
                        && bytes[3] == 71
                        && bytes[4] == 13
                        && bytes[5] == 10
                        && bytes[6] == 26
                        && bytes[7] == 10;
        if (!jpeg && !png) {
            throw new IdentityFailure(400, "UPLOAD_INVALID");
        }
        String oldResource = (String) row.get("submitted_photo_resource_id");
        String oldKey = (String) row.get("submitted_photo_storage_key");
        String resource = "student-card-" + UUID.randomUUID();
        String key = "courier/" + courier + "/" + resource + (jpeg ? ".jpg" : ".png");
        try {
            storage.store(key, type, bytes);
        } catch (IOException e) {
            throw new IdentityFailure(500, "UPLOAD_STORAGE_FAILED");
        }
        mapper.updateSubmittedPhoto(resource, key, id, expectedVersion);
        if (mapper.touchCertificationIfVersionMatches(id, expectedVersion + 1) != 1) {
            throw new IdentityFailure(409, "CERTIFICATION_STATE_CONFLICT");
        }
        if (oldResource != null && !oldResource.equals(resource)) {
            governance.requestCertificationDeletion(
                    id, oldResource, oldKey, Instant.now().plus(Duration.ofDays(30)));
        }
        event(id, courier, "COURIER", "PHOTO_UPLOADED", requestId);
        CourierCertificationVO result = get(courier, id);
        idempotency.finish(
                Long.toString(courier),
                "courier-certification:upload",
                requestId,
                "COURIER_CERTIFICATION",
                Long.toString(id),
                result);
        return result;
    }

    @Transactional
    @Override
    public CourierCertificationVO decide(
            String token,
            IdentityService identity,
            long id,
            long expectedVersion,
            String resource,
            String decision,
            String reason,
            String requestId) {
        IdentityAccountVO account = identity.authorizeForWrite(token, "ADMIN", false);
        long admin = Long.parseLong(account.id());
        lockActor(admin);
        if (!"APPROVE".equals(decision) && !"REJECT".equals(decision)) {
            throw new IdentityFailure(400, "INVALID_REQUEST");
        }
        CourierCertificationVO replay =
                idempotency.replay(
                        Long.toString(admin),
                        "courier-certification:decide",
                        requestId,
                        id + ":" + expectedVersion + ":" + resource + ":" + decision + ":" + reason,
                        CourierCertificationVO.class);
        if (replay != null) {
            return replay;
        }
        var row = locked(id);
        if (((Number) row.get("version")).longValue() != expectedVersion
                || !"PENDING".equals(row.get("status"))) {
            throw new IdentityFailure(409, "CERTIFICATION_STATE_CONFLICT");
        }
        String oldApprovedResource = (String) row.get("approved_photo_resource_id");
        String oldApprovedKey = (String) row.get("approved_photo_storage_key");
        if ("APPROVE".equals(decision)) {
            if (resource == null || !resource.equals(row.get("submitted_photo_resource_id"))) {
                throw new IdentityFailure(409, "PROOF_NOT_READY");
            }
            mapper.approveCertification(id, expectedVersion);
            if (oldApprovedResource != null && !oldApprovedResource.equals(resource)) {
                governance.requestCertificationDeletion(
                        id,
                        oldApprovedResource,
                        oldApprovedKey,
                        Instant.now().plus(Duration.ofDays(30)));
            }
        } else {
            if (reason == null || reason.isBlank()) {
                throw new IdentityFailure(400, "INVALID_REQUEST");
            }
            mapper.rejectCertification(reason, id, expectedVersion);
        }
        event(id, admin, "ADMIN", "REVIEWED", requestId);
        CourierCertificationVO result = get(admin, id);
        idempotency.finish(
                Long.toString(admin),
                "courier-certification:decide",
                requestId,
                "COURIER_CERTIFICATION",
                Long.toString(id),
                result);
        return result;
    }

    @Transactional
    @Override
    public byte[] read(
            String token, IdentityService identity, long id, String resource, String requestId) {
        IdentityAccountVO account = identity.requireRole(token, "ADMIN", false);
        var row = locked(id);
        String actual =
                resource != null ? resource : (String) row.get("approved_photo_resource_id");
        if (actual == null) {
            actual = (String) row.get("submitted_photo_resource_id");
        }
        if (actual == null
                || !actual.equals(row.get("approved_photo_resource_id"))
                        && !actual.equals(row.get("submitted_photo_resource_id"))) {
            throw new IdentityFailure(404, "PROOF_EXPIRED");
        }
        if (mapper.countPhotoDeletionsInProgress(id, actual) > 0) {
            throw new IdentityFailure(409, "EVIDENCE_DELETION_IN_PROGRESS");
        }
        try {
            byte[] result =
                    storage.read(
                            (String)
                                    (actual.equals(row.get("approved_photo_resource_id"))
                                            ? row.get("approved_photo_storage_key")
                                            : row.get("submitted_photo_storage_key")));
            mapper.insertPhotoReadAudit(
                    Long.parseLong(account.id()),
                    id,
                    requestId,
                    "controlled student-card read",
                    actual);
            return result;
        } catch (IOException e) {
            throw new IdentityFailure(404, "PROOF_EXPIRED");
        }
    }

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> adminList(
            String token, String status, String query, int page, int size) {
        IdentityAccountVO account = identity.requireRole(token, "ADMIN", false);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        String filter = (status == null || status.isBlank()) ? null : status;
        String phonePattern = (query == null || query.isBlank()) ? null : "%" + query.trim() + "%";
        long total = mapper.countCertifications(filter, filter, phonePattern, phonePattern);
        var items =
                mapper.pageCertifications(
                        filter,
                        filter,
                        phonePattern,
                        phonePattern,
                        safeSize,
                        (long) safePage * safeSize);
        // v2 不收集旧姓名和学籍信息；管理员响应不能凭空补造身份资料。
        items.forEach(
                row -> {
                    row.put("displayName", "骑手 #" + row.get("courierId"));
                    for (String field :
                            new String[] {
                                "realName",
                                "studentId",
                                "campus",
                                "reviewerName",
                                "validUntil",
                                "approvedValidUntil"
                            }) {
                        row.put(field, null);
                    }
                    row.put(
                            "photoUploaded",
                            row.get("submitted_photo_resource_id") != null
                                    || row.get("approved_photo_resource_id") != null);
                    row.put(
                            "photoResourceId",
                            row.get("status").toString().equals("APPROVED")
                                    ? row.get("approved_photo_resource_id")
                                    : row.get("submitted_photo_resource_id"));
                    row.put("approved", "APPROVED".equals(row.get("status")));
                    row.put("completedOrderCount", 0);
                    row.put("completedPackageOrderCount", 0);
                    row.remove("submitted_photo_resource_id");
                    row.remove("approved_photo_resource_id");
                });
        return Map.of(
                "items",
                items,
                "page",
                safePage,
                "size",
                safeSize,
                "totalElements",
                total,
                "totalPages",
                (total + safeSize - 1) / safeSize);
    }

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> performance(String token, long courier, int page, int size) {
        identity.requireRole(token, "ADMIN", false);
        int pageIndex = Math.max(0, page);
        int pageSize = Math.max(1, Math.min(size, 100));
        long total = mapper.countDeliveredTasks(courier);
        // 复用同一 FROM 子句；month_count 避开 PostgreSQL 的 MONTH 关键字。
        var counts = mapper.summarizeDeliveredTasks(courier);
        var orders = mapper.pageDeliveredTasks(courier, pageSize, (long) pageIndex * pageSize);
        var result = new HashMap<String, Object>();
        result.put("todayCompletedPackageOrders", counts.get("today"));
        result.put("lastSevenDaysCompletedPackageOrders", counts.get("week"));
        result.put("currentMonthCompletedPackageOrders", counts.get("month_count"));
        result.put("totalCompletedPackageOrders", counts.get("total"));
        result.put("smallCompletedPackageOrders", counts.get("small"));
        result.put("mediumCompletedPackageOrders", counts.get("medium"));
        result.put("mediumLargeCompletedPackageOrders", counts.get("mediumlarge"));
        result.put("largeCompletedPackageOrders", counts.get("large"));
        result.put("specialLargeCompletedPackageOrders", counts.get("xl"));
        result.put("extraLargeCompletedPackageOrders", counts.get("extralarge"));
        result.put(
                "orders",
                Map.of(
                        "items",
                        orders,
                        "page",
                        pageIndex,
                        "size",
                        pageSize,
                        "totalElements",
                        total,
                        "totalPages",
                        (total + pageSize - 1) / pageSize));
        return result;
    }

    @Override
    public CourierCertificationVO get(long subject, long id) {
        var certificationRow = locked(id);
        return map(certificationRow);
    }

    @Override
    public CourierCertificationVO getMine(long courier) {
        var rows = mapper.findCourierCertification(courier);
        if (rows.isEmpty()) {
            return null;
        }
        return map(rows.get(0));
    }

    private Map<String, Object> locked(long id) {
        var rows = mapper.lockCertification(id);
        if (rows.isEmpty()) {
            throw new IdentityFailure(404, "RESOURCE_NOT_FOUND");
        }
        return rows.get(0);
    }

    private CourierCertificationVO map(Map<String, Object> certificationRow) {
        return new CourierCertificationVO(
                ((Number) certificationRow.get("id")).longValue(),
                ((Number) certificationRow.get("courier_id")).longValue(),
                (String) certificationRow.get("status"),
                Boolean.TRUE.equals(certificationRow.get("eligible")),
                ((Number) certificationRow.get("version")).longValue(),
                instant(certificationRow.get("submitted_at")),
                instant(certificationRow.get("reviewed_at")),
                certificationRow.get("submitted_photo_resource_id") != null
                        || certificationRow.get("approved_photo_resource_id") != null,
                (String)
                        (certificationRow.get("status").equals("APPROVED")
                                ? certificationRow.get("approved_photo_resource_id")
                                : certificationRow.get("submitted_photo_resource_id")),
                (String) certificationRow.get("rejection_reason"));
    }

    private static Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant i) {
            return i;
        }
        if (value instanceof Timestamp t) {
            return t.toInstant();
        }
        if (value instanceof OffsetDateTime o) {
            return o.toInstant();
        }
        return Instant.parse(value.toString());
    }

    private static String digest(byte[] bytes) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder digestHex = new StringBuilder();
            for (byte b : d) {
                digestHex.append(String.format("%02x", b));
            }
            return digestHex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("digest unavailable", e);
        }
    }

    private void lockActor(long actor) {
        mapper.lockActor(actor);
    }

    private void event(long id, long actor, String kind, String type, String request) {
        mapper.insertCertificationEvent(id, actor, kind, type, request, id);
    }
}
