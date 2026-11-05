package com.zhangxuan.runrunv2;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangxuan.runrunv2.dto.response.identity.LoginResult;
import com.zhangxuan.runrunv2.integration.storage.V2StudentCardStorage;
import com.zhangxuan.runrunv2.integration.wechat.WechatGateway;
import com.zhangxuan.runrunv2.integration.wechat.WechatSubscriptionGateway;
import com.zhangxuan.runrunv2.model.notification.DeliveryResult;
import com.zhangxuan.runrunv2.service.courier.CourierCertificationV2Service;
import com.zhangxuan.runrunv2.service.evidence.CourierEvidenceGovernanceService;
import com.zhangxuan.runrunv2.service.identity.DeploymentIdentityService;
import com.zhangxuan.runrunv2.service.identity.IdentityService;
import com.zhangxuan.runrunv2.service.notification.NotificationOutboxWorker;
import com.zhangxuan.runrunv2.service.notification.NotificationService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real controllers, services, transactions, private files and the complete delivered database
 * chain.
 */
@SpringBootTest(
    classes = IdentityApplication.class,
    properties = {
      "spring.config.name=application-v2-test",
      "rrc.identity.environment=test",
      "rrc.identity.app-id=synthetic-app",
      "rrc.identity.app-secret=synthetic-secret",
      "rrc.notifications.worker-delay-ms=86400000",
      "rrc.evidence.cleanup-delay-ms=86400000"
    })
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class V2FeatureWiringIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

  @TempDir static Path evidenceRoot;

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) throws Exception {
    Path root = Path.of("../..").toAbsolutePath().normalize();
    try (var connection =
        DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
      for (String name : Files.readAllLines(root.resolve("deploy/v2-test/migrations.txt"))) {
        if (name.isBlank()) continue;
        try (var sql = connection.createStatement()) {
          sql.execute(Files.readString(root.resolve("server/db/migrations/up").resolve(name)));
        }
      }
    }
    registry.add("spring.datasource.url", PG::getJdbcUrl);
    registry.add("spring.datasource.username", PG::getUsername);
    registry.add("spring.datasource.password", PG::getPassword);
    registry.add(
        "rrc.identity.encryption-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
    registry.add(
        "rrc.identity.hash-key",
        () ->
            Base64.getEncoder()
                .encodeToString(
                    "synthetic-different-hash-key-000001"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    registry.add("rrc.evidence.root", () -> evidenceRoot.toString());
  }

  @Autowired IdentityService identity;

  @Autowired DeploymentIdentityService deployment;

  @Autowired CourierCertificationV2Service certifications;

  @Autowired CourierEvidenceGovernanceService governance;

  @Autowired NotificationService notifications;

  @Autowired NotificationOutboxWorker worker;

  @Autowired JdbcTemplate db;

  @Autowired MockMvc http;

  @Autowired ObjectMapper json;

  @MockitoBean WechatGateway wechat;

  @MockitoBean WechatSubscriptionGateway subscriptions;

  @MockitoSpyBean V2StudentCardStorage storage;

  String courierToken, adminToken, userToken;
  long courierId, certificationId;

  @BeforeEach
  void accounts() {
    db.execute("TRUNCATE runrun_v2.app_users,runrun_v2.identity_tickets RESTART IDENTITY CASCADE");
    when(wechat.exchangeLoginCode(anyString()))
        .thenAnswer(i -> "synthetic-openid:" + i.getArgument(0));
    when(wechat.exchangePhoneCode(anyString())).thenAnswer(i -> i.getArgument(0));
    long deployer =
        Long.parseLong(
            deployment.bootstrap(
                "synthetic test deployment", "CREATE DEPLOYMENT IDENTITY", "bootstrap"));
    var courier = register("courier", "13800000001", "COURIER");
    courierToken = courier.token();
    courierId = Long.parseLong(courier.account().id());
    certificationId = certifications.getMine(courierId).id();
    var admin = register("admin", "13800000002", "USER");
    long adminId = Long.parseLong(admin.account().id());
    deployment.provisionAdmin(
        deployer, adminId, 0, "synthetic admin", "CREATE ADMIN " + adminId, "create-admin");
    deployment.changeAccess(
        deployer, adminId, true, 1, "synthetic enable", "ENABLE " + adminId, "enable-admin");
    adminToken = identity.login("admin").token();
    userToken = register("user", "13800000003", "USER").token();
  }

  private LoginResult register(String code, String phone, String role) {
    return identity.bindPhone(identity.login(code).loginTicket(), phone, role);
  }

  private JsonNode apply() throws Exception {
    return json.readTree(
        http.perform(
                post("/api/v2/courier/certification/applications")
                    .header("Authorization", "Bearer " + courierToken)
                    .header("Idempotency-Key", "apply"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private byte[] png() throws IOException {
    var bytes = new ByteArrayOutputStream();
    assertTrue(ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes));
    return bytes.toByteArray();
  }

  private JsonNode upload(JsonNode application, byte[] photo) throws Exception {
    var response =
        http.perform(
                multipart(
                        "/api/v2/courier/certification/applications/{id}/student-card",
                        certificationId)
                    .file(new MockMultipartFile("file", "synthetic.png", "image/png", photo))
                    .param("expectedVersion", application.get("version").asText())
                    .header("Authorization", "Bearer " + courierToken)
                    .header("Idempotency-Key", "upload"))
            .andReturn();
    assertEquals(
        200,
        response.getResponse().getStatus(),
        () -> String.valueOf(response.getResolvedException()));
    return json.readTree(response.getResponse().getContentAsString());
  }

  @Test
  void multipartTextVersionUploadReadAndApprovalUseRealWiring() throws Exception {
    byte[] photo = png();
    JsonNode uploaded = upload(apply(), photo);
    assertTrue(uploaded.get("photoUploaded").asBoolean());
    String resource = uploaded.get("photoResourceId").asText();
    http.perform(
            get("/api/v2/courier/certification/{id}/student-card", certificationId)
                .param("photoResourceId", resource)
                .accept("image/png")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(content().bytes(photo));
    assertEquals(
        1L,
        db.queryForObject(
            "SELECT count(*) FROM runrun_v2.admin_audit_logs WHERE"
                + " action='COURIER_CERTIFICATION_PHOTO_READ'",
            Long.class));
    String decision =
        json.writeValueAsString(
            Map.of(
                "decision",
                "APPROVE",
                "expectedVersion",
                uploaded.get("version").asLong(),
                "photoResourceId",
                resource,
                "reason",
                "synthetic approval"));
    String route = "/api/v2/courier/certification/" + certificationId + "/decision";
    for (int attempt = 0; attempt < 2; attempt++)
      http.perform(
              post(route)
                  .contentType("application/json")
                  .content(decision)
                  .header("Authorization", "Bearer " + adminToken)
                  .header("Idempotency-Key", "decision"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("APPROVED"))
          .andExpect(jsonPath("$.eligible").value(true));
    http.perform(
            get("/api/v2/courier/certification/{id}/student-card", certificationId)
                .param("photoResourceId", resource)
                .header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());
    assertEquals(
        3L,
        db.queryForObject(
            "SELECT count(*) FROM runrun_v2.courier_certification_events WHERE certification_id=?",
            Long.class,
            certificationId));
  }

  @Test
  void malformedMultipartFieldsAreValidationErrorsWithoutWritingAFile() throws Exception {
    JsonNode application = apply();
    String route =
        "/api/v2/courier/certification/applications/" + certificationId + "/student-card";
    var photo = new MockMultipartFile("file", "synthetic.png", "image/png", png());
    http.perform(
            multipart(route)
                .file(photo)
                .param("expectedVersion", "invalid")
                .header("Authorization", "Bearer " + courierToken)
                .header("Idempotency-Key", "invalid-version"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    http.perform(
            multipart(route)
                .file(photo)
                .header("Authorization", "Bearer " + courierToken)
                .header("Idempotency-Key", "missing-version"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    http.perform(
            multipart(route)
                .param("expectedVersion", application.get("version").asText())
                .header("Authorization", "Bearer " + courierToken)
                .header("Idempotency-Key", "missing-file"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    assertEquals(application.get("version").asLong(), certifications.getMine(courierId).version());
    assertFalse(certifications.getMine(courierId).photoUploaded());
    verify(storage, never()).store(anyString(), anyString(), any());
  }

  @Test
  void certificationHoldControllerPersistsAndReplaysBothOperations() throws Exception {
    String body =
        json.writeValueAsString(
            Map.of(
                "certificationId",
                certificationId,
                "reason",
                "synthetic legal hold",
                "reviewDueAt",
                "2030-01-02T03:04:05Z"));
    String route = "/api/v2/admin/evidence-holds";
    String response =
        http.perform(
                post(route)
                    .contentType("application/json")
                    .content(body)
                    .header("Authorization", "Bearer " + adminToken)
                    .header("Idempotency-Key", "hold"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long hold = json.readTree(response).get("id").asLong();
    http.perform(
            post(route)
                .contentType("application/json")
                .content(body)
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", "hold"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(hold));
    for (int attempt = 0; attempt < 2; attempt++)
      http.perform(
              post(route + "/{id}/release", hold)
                  .header("Authorization", "Bearer " + adminToken)
                  .header("Idempotency-Key", "release"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(hold));
    assertEquals(
        1L,
        db.queryForObject(
            "SELECT count(*) FROM runrun_v2.evidence_holds WHERE released_at IS NOT NULL",
            Long.class));
    assertEquals(
        2L,
        db.queryForObject(
            "SELECT count(*) FROM runrun_v2.admin_audit_logs WHERE action IN"
                + " ('EVIDENCE_HOLD_CREATED','EVIDENCE_HOLD_RELEASED')",
            Long.class));
  }

  @Test
  void notificationsKeepRecipientEventChannelPayloadAndRetryFailure() throws Exception {
    db.update("UPDATE runrun_v2.notification_outbox SET status='SENT'");
    long id =
        db.queryForObject(
            "SELECT"
                + " runrun_v2.enqueue_notification(?,NULL,'CERTIFICATION_UPDATED','synthetic-notification','{}'::jsonb,'{\"entityId\":\"synthetic\"}'::jsonb)",
            Long.class,
            courierId);
    var deliveries = worker.claim(10);
    assertEquals(1, deliveries.size());
    var delivery = deliveries.get(0);
    assertEquals(courierId, delivery.recipientId());
    assertEquals("synthetic-notification", delivery.eventKey());
    assertEquals("WECHAT", delivery.channel());
    assertEquals(Map.of("entityId", "synthetic"), delivery.payload());
    worker.complete(delivery.id(), false, "WECHAT_NOT_CONFIGURED");
    assertEquals(
        "FAILED",
        db.queryForObject(
            "SELECT status FROM runrun_v2.notification_outbox WHERE id=?",
            String.class,
            delivery.id()));
    assertNotNull(
        db.queryForObject(
            "SELECT next_attempt_at FROM runrun_v2.notification_outbox WHERE id=?",
            Object.class,
            delivery.id()));
    db.update(
        "UPDATE runrun_v2.notification_outbox SET next_attempt_at=now() WHERE id=?", delivery.id());
    when(subscriptions.send(courierId, delivery.payload()))
        .thenReturn(new DeliveryResult(false, "WECHAT_NOT_CONFIGURED"));
    worker.runOnce();
    verify(subscriptions).send(courierId, delivery.payload());
    assertEquals(
        2,
        db.queryForObject(
            "SELECT attempt_count FROM runrun_v2.notification_outbox WHERE id=?",
            Integer.class,
            delivery.id()));
    notifications.markRead(userToken, id);
    assertNull(
        db.queryForObject(
            "SELECT read_at FROM runrun_v2.user_notifications WHERE id=?", Object.class, id));
    http.perform(
            post("/api/v2/notifications/{id}/read", id)
                .header("Authorization", "Bearer " + courierToken))
        .andExpect(status().isOk());
    assertNotNull(
        db.queryForObject(
            "SELECT read_at FROM runrun_v2.user_notifications WHERE id=?", Object.class, id));
    http.perform(get("/api/v2/notifications").header("Authorization", "Bearer " + courierToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(id));
  }

  @Test
  void cleanupRetriesStorageFailureAndDoesNotRevokeEligibility() throws Exception {
    JsonNode uploaded = upload(apply(), png());
    String resource = uploaded.get("photoResourceId").asText();
    certifications.decide(
        adminToken,
        identity,
        certificationId,
        uploaded.get("version").asLong(),
        resource,
        "APPROVE",
        "synthetic approval",
        "approve-cleanup");
    String key =
        db.queryForObject(
            "SELECT approved_photo_storage_key FROM runrun_v2.courier_certifications WHERE id=?",
            String.class,
            certificationId);
    governance.requestCertificationDeletion(
        certificationId, resource, key, Instant.parse("2020-01-01T00:00:00Z"));
    doThrow(new IOException("synthetic storage failure")).when(storage).delete(key);
    governance.cleanupDue();
    assertEquals(
        "FAILED",
        db.queryForObject(
            "SELECT status FROM runrun_v2.evidence_deletion_audits WHERE certification_id=?",
            String.class,
            certificationId));
    assertTrue(Files.exists(evidenceRoot.resolve(key)));
    doCallRealMethod().when(storage).delete(key);
    governance.cleanupDue();
    assertEquals(
        "SUCCEEDED",
        db.queryForObject(
            "SELECT status FROM runrun_v2.evidence_deletion_audits WHERE certification_id=?",
            String.class,
            certificationId));
    assertFalse(Files.exists(evidenceRoot.resolve(key)));
    assertTrue(certifications.getMine(courierId).eligible());
    assertFalse(certifications.getMine(courierId).photoUploaded());
  }
}
