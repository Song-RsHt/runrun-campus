package com.zhangxuan.runruncampus.service.courier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.zhangxuan.runruncampus.entity.courier.CourierCertification;
import com.zhangxuan.runruncampus.entity.user.AppUser;
import com.zhangxuan.runruncampus.enums.user.UserRole;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.integration.storage.OrderProofStorage;
import com.zhangxuan.runruncampus.integration.web.MultipartUploads;
import com.zhangxuan.runruncampus.repository.courier.CourierCertificationRepository;
import com.zhangxuan.runruncampus.service.admin.AdminAuditService;
import com.zhangxuan.runruncampus.service.impl.courier.CourierCertificationServiceImpl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

class CourierCertificationGovernanceTest {

    @Test
    void staleReviewVersionIsRejected() {
        var repo = Mockito.mock(CourierCertificationRepository.class);
        var storage = Mockito.mock(OrderProofStorage.class);
        var service =
                new CourierCertificationServiceImpl(
                        repo, Mockito.mock(AdminAuditService.class), storage);
        var courier = new AppUser("13800000001", "hash", "rider", UserRole.COURIER);
        ReflectionTestUtils.setField(courier, "id", 1L);
        var admin = new AppUser("13800000002", "hash", "admin", UserRole.ADMIN);
        ReflectionTestUtils.setField(admin, "id", 2L);
        var c = new CourierCertification(courier, "", "", "");
        ReflectionTestUtils.setField(c, "id", 3L);
        c.attachSubmittedPhoto("k", "image/jpeg");
        when(repo.findLockedById(3L)).thenReturn(Optional.of(c));
        var ex =
                Assertions.assertThrows(
                        BusinessFailure.class,
                        () -> service.approve(admin, 3L, 99L, c.getSubmittedPhotoResourceId()));
        assertEquals(409, ex.status());
    }

    @Test
    void oversizedFileIsRejected() {
        var repo = Mockito.mock(CourierCertificationRepository.class);
        var storage = Mockito.mock(OrderProofStorage.class);
        var service =
                new CourierCertificationServiceImpl(
                        repo, Mockito.mock(AdminAuditService.class), storage);
        var courier = new AppUser("13800000001", "hash", "rider", UserRole.COURIER);
        ReflectionTestUtils.setField(courier, "id", 1L);
        var c = new CourierCertification(courier, "", "", "");
        ReflectionTestUtils.setField(c, "id", 3L);
        when(repo.findLockedById(3L)).thenReturn(Optional.of(c));
        var file =
                new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[8 * 1024 * 1024 + 1]);
        var ex =
                Assertions.assertThrows(
                        BusinessFailure.class,
                        () -> service.uploadStudentCard(courier, 3L, MultipartUploads.adapt(file)));
        assertEquals(400, ex.status());
    }
}
