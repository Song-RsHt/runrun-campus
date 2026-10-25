package com.zhangxuan.runruncampus.courier;

import com.zhangxuan.runruncampus.order.OrderProofStorage;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourierCertificationMaintenanceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

    @Mock CourierCertificationRepository certifications;
    @Mock OrderProofStorage storage;

    private CourierCertificationMaintenance maintenance;
    private AppUser administrator;

    @BeforeEach
    void setUp() {
        maintenance = new CourierCertificationMaintenance(certifications, storage);
        administrator = user(99L, UserRole.ADMIN);
    }

    @Test
    void cleansExpiredApprovedAndOldRejectedPhotosButKeepsCertificationData() throws Exception {
        CourierCertification expired = approved(1L, 11L, "approved.jpg", TODAY.minusDays(30));
        CourierCertification rejected = rejected(2L, 12L, "submitted.jpg", TODAY.minusDays(30));
        when(certifications.findApprovedPhotosToClean(any(Instant.class))).thenReturn(List.of(expired));
        when(certifications.findRejectedPhotosToClean(any(Instant.class))).thenReturn(List.of(rejected));

        maintenance.run(TODAY);

        verify(storage).delete("approved.jpg");
        verify(storage).delete("submitted.jpg");
        assertNull(expired.getApprovedPhotoKey());
        assertNull(rejected.getSubmittedPhotoKey());
        assertEquals("202600012", rejected.getStudentId());
        assertEquals(CourierCertificationStatus.REJECTED, rejected.getStatus());
    }

    @Test
    void keepsPhotoReferenceWhenObjectDeletionFails() throws Exception {
        CourierCertification expired = approved(1L, 11L, "retry.jpg", TODAY.minusDays(30));
        when(certifications.findApprovedPhotosToClean(any(Instant.class))).thenReturn(List.of(expired));
        when(certifications.findRejectedPhotosToClean(any(Instant.class))).thenReturn(List.of());
        doThrow(new IOException("temporary failure")).when(storage).delete("retry.jpg");

        maintenance.run(TODAY);

        assertEquals("retry.jpg", expired.getApprovedPhotoKey());
    }

    private CourierCertification approved(Long certificationId, Long courierId, String key, LocalDate reviewedDate) {
        AppUser courier = user(courierId, UserRole.COURIER);
        courier.markNewCourierUnapproved();
        CourierCertification certification = new CourierCertification(
                courier, "骑手" + courierId, "2026000" + courierId, "主校区");
        ReflectionTestUtils.setField(certification, "id", certificationId);
        certification.attachSubmittedPhoto(key, "image/jpeg");
        certification.approve(administrator);
        ReflectionTestUtils.setField(certification, "reviewedAt",
                reviewedDate.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant());
        return certification;
    }

    private CourierCertification rejected(Long certificationId, Long courierId, String key, LocalDate reviewedDate) {
        AppUser courier = user(courierId, UserRole.COURIER);
        courier.markNewCourierUnapproved();
        CourierCertification certification = new CourierCertification(
                courier, "骑手" + courierId, "2026000" + courierId, "主校区");
        ReflectionTestUtils.setField(certification, "id", certificationId);
        certification.attachSubmittedPhoto(key, "image/jpeg");
        certification.reject(administrator, "材料不清晰");
        ReflectionTestUtils.setField(certification, "reviewedAt",
                reviewedDate.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant());
        return certification;
    }

    private AppUser user(Long id, UserRole role) {
        AppUser user = new AppUser("1380000" + String.format("%04d", id), "hash", "测试账号" + id, role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
