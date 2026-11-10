package com.zhangxuan.runruncampus.service.courier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhangxuan.runruncampus.entity.user.AppUser;
import com.zhangxuan.runruncampus.enums.user.UserRole;
import com.zhangxuan.runruncampus.integration.web.MultipartUploads;
import com.zhangxuan.runruncampus.repository.user.UserRepository;
import com.zhangxuan.runruncampus.support.PostgresSpringContextTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

@SpringBootTest(
        webEnvironment = WebEnvironment.NONE,
        properties = "app.proof-storage.local-directory=target/test-cert-proofs")
@ActiveProfiles("test")
@Testcontainers
@Transactional
class CourierCertificationPostgresIntegrationTest extends PostgresSpringContextTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired CourierCertificationService service;

    @Autowired UserRepository users;

    @Autowired JdbcTemplate jdbc;

    @Test
    void newCourierNeedsOnlyStoredPhotoBeforeApproval() {
        AppUser courier = new AppUser(phone(), "hash", "新骑手", UserRole.COURIER);
        courier.markNewCourierUnapproved();
        courier = users.saveAndFlush(courier);
        AppUser administrator =
                users.saveAndFlush(new AppUser(phone(), "hash", "审核员", UserRole.ADMIN));
        var pending = service.submit(courier);
        byte[] image = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2, 3};
        var uploaded =
                service.uploadStudentCard(
                        courier,
                        pending.id(),
                        MultipartUploads.adapt(
                                new MockMultipartFile("file", "card.jpg", "image/jpeg", image)));
        var photo = service.studentCardPhoto(administrator, pending.id());
        var approved = service.approve(administrator, pending.id());
        assertTrue(uploaded.photoUploaded());
        assertArrayEquals(image, photo.bytes());
        assertEquals(null, approved.approvedValidUntil());
        assertTrue(service.isEligible(courier));
        assertEquals(
                1,
                jdbc.queryForObject(
                        """
select count(*) from courier_certifications
where id = ? and approved_photo_key is not null and approved_valid_until is null
""",
                        Integer.class,
                        pending.id()));
    }

    private String phone() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 11);
    }
}
