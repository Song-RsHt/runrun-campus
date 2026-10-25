package com.zhangxuan.runruncampus.courier;

import com.zhangxuan.runruncampus.admin.AdminAuditService;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import com.zhangxuan.runruncampus.order.OrderProofStorage;
import org.springframework.mock.web.MockMultipartFile;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourierCertificationServiceTest {
    @Mock
    private CourierCertificationRepository certificationRepository;
    @Mock
    private AdminAuditService auditService;
    @Mock
    private OrderProofStorage storage;

    private CourierCertificationService certificationService;
    private AppUser courier;
    private AppUser admin;

    @BeforeEach
    void setUp() {
        certificationService = new CourierCertificationService(certificationRepository, auditService, storage);
        courier = user(1L, UserRole.COURIER);
        courier.markNewCourierUnapproved();
        admin = user(9L, UserRole.ADMIN);
    }

    @Test
    void newCourierStartsNotApplied() {
        when(certificationRepository.findByCourier(courier)).thenReturn(Optional.empty());

        CourierCertificationService.CertificationResponse response = certificationService.overview(courier);

        assertEquals(CourierCertificationStatus.NOT_APPLIED, response.status());
        assertFalse(response.approved());
    }

    @Test
    void courierCanSubmitPhotoOnlyApplication() {
        when(certificationRepository.findLockedByCourier(courier)).thenReturn(Optional.empty());
        when(certificationRepository.save(any(CourierCertification.class))).thenAnswer(invocation -> {
            CourierCertification application = invocation.getArgument(0);
            ReflectionTestUtils.setField(application, "id", 11L);
            return application;
        });

        CourierCertificationService.CertificationResponse response = certificationService.submit(courier);

        assertEquals(11L, response.id());
        assertEquals(null, response.realName());
        assertEquals(null, response.studentId());
        assertEquals(null, response.campus());
        assertEquals(null, response.validUntil());
        assertEquals(CourierCertificationStatus.PENDING, response.status());
        assertFalse(response.approved());
    }

    @Test
    void adminCanApprovePendingApplication() {
        CourierCertification application = application(11L);
        application.attachSubmittedPhoto("card-test.jpg", "image/jpeg");
        when(certificationRepository.findLockedById(11L)).thenReturn(Optional.of(application));

        CourierCertificationService.CertificationResponse response = certificationService.approve(admin, 11L);

        assertEquals(CourierCertificationStatus.APPROVED, response.status());
        assertTrue(response.approved());
        assertTrue(courier.isCourierApproved());
        assertEquals(admin.getDisplayName(), response.reviewerName());
    }

    @Test
    void missingStudentCardPhotoCannotBeApproved() {
        CourierCertification application = application(11L);
        when(certificationRepository.findLockedById(11L)).thenReturn(Optional.of(application));

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> certificationService.approve(admin, 11L));

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        assertFalse(courier.isCourierApproved());
    }

    @Test
    void legacyExpiryDoesNotBlockPhotoOnlyApproval() {
        CourierCertification application = application(11L);
        application.declareValidity(LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).minusDays(1));
        application.attachSubmittedPhoto("card-test.jpg", "image/jpeg");
        when(certificationRepository.findLockedById(11L)).thenReturn(Optional.of(application));

        CourierCertificationService.CertificationResponse response = certificationService.approve(admin, 11L);

        assertEquals(CourierCertificationStatus.APPROVED, response.status());
        assertEquals(null, response.approvedValidUntil());
    }

    @Test
    void existingApprovedCourierKeepsEligibilityUntilSupplementReviewed() {
        courier.approveCourier();
        when(certificationRepository.findLockedByCourier(courier)).thenReturn(Optional.empty());
        when(certificationRepository.save(any(CourierCertification.class)))
                .thenAnswer(call -> call.getArgument(0));

        var response = certificationService.submit(courier);

        assertEquals(CourierCertificationStatus.PENDING, response.status());
        assertTrue(response.approved());
        assertTrue(certificationService.isEligible(courier));
    }

    @Test
    void validPhotoUploadMarksPendingApplicationReadyForReview() {
        CourierCertification application = application(11L);
        when(certificationRepository.findLockedById(11L)).thenReturn(Optional.of(application));
        MockMultipartFile file = new MockMultipartFile("file", "card.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2});

        var response = certificationService.uploadStudentCard(courier, 11L, file);

        assertTrue(response.photoUploaded());
    }

    @Test
    void legacyExpiryNoLongerRevokesEligibility() {
        CourierCertification application = application(11L);
        application.declareValidity(LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).minusDays(1));
        application.attachSubmittedPhoto("expired-card.jpg", "image/jpeg");
        application.approve(admin);
        when(certificationRepository.findByCourier(courier)).thenReturn(Optional.of(application));

        assertTrue(certificationService.isEligible(courier));
    }

    @Test
    void onlyAdministratorMayReadSubmittedStudentCardPhoto() {
        CourierCertification application = application(11L);
        application.attachSubmittedPhoto("card-test.jpg", "image/jpeg");
        when(certificationRepository.findById(11L)).thenReturn(Optional.of(application));
        try { when(storage.read("card-test.jpg")).thenReturn(new byte[]{1, 2, 3}); }
        catch (java.io.IOException exception) { throw new AssertionError(exception); }

        assertEquals("image/jpeg", certificationService.studentCardPhoto(admin, 11L).contentType());
        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> certificationService.studentCardPhoto(courier, 11L));
        assertEquals(HttpStatus.FORBIDDEN, failure.getStatusCode());
    }

    @Test
    void adminCanRejectPendingApplication() {
        CourierCertification application = application(11L);
        when(certificationRepository.findLockedById(11L)).thenReturn(Optional.of(application));

        CourierCertificationService.CertificationResponse response = certificationService.reject(admin, 11L, "  学号信息不完整  ");

        assertEquals(CourierCertificationStatus.REJECTED, response.status());
        assertEquals("学号信息不完整", response.rejectionReason());
        assertFalse(courier.isCourierApproved());
    }

    @Test
    void rejectedCourierCanResubmitApplication() {
        CourierCertification application = application(11L);
        application.attachSubmittedPhoto("rejected-card.jpg", "image/jpeg");
        application.reject(admin, "资料不完整");
        when(certificationRepository.findLockedByCourier(courier)).thenReturn(Optional.of(application));

        CourierCertificationService.CertificationResponse response = certificationService.submit(courier);

        assertEquals(CourierCertificationStatus.PENDING, response.status());
        assertEquals(null, response.realName());
        assertEquals(null, response.studentId());
        assertEquals(null, response.campus());
        assertEquals(null, response.rejectionReason());
        assertFalse(response.photoUploaded());
        assertEquals(null, application.getSubmittedPhotoKey());
    }

    @Test
    void customerCannotSubmitCourierApplication() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> certificationService.submit(user(2L, UserRole.CUSTOMER)));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    private CourierCertification application(Long id) {
        CourierCertification application = new CourierCertification(courier, "张同学", "20260001", "主校区");
        ReflectionTestUtils.setField(application, "id", id);
        return application;
    }

    private AppUser user(Long id, UserRole role) {
        AppUser user = new AppUser("1380000000" + id, "password-hash", "测试账号" + id, role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
