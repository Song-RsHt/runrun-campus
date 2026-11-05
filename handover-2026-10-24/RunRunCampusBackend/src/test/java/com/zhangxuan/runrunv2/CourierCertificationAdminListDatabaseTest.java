package com.zhangxuan.runrunv2;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.zhangxuan.runrunv2.dto.response.identity.Account;
import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.service.courier.CourierCertificationV2Service;
import com.zhangxuan.runrunv2.service.identity.IdentityService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CourierCertificationAdminListDatabaseTest {

  @Container
  static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

  static JdbcTemplate db;

  @BeforeAll
  static void migrateFullLocalChain() throws Exception {
    var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
    db = new JdbcTemplate(ds);
    Path root = Path.of("../..").toAbsolutePath().normalize();
    try (var connection = ds.getConnection()) {
      for (String name : Files.readAllLines(root.resolve("deploy/v2-test/migrations.txt"))) {
        if (name.isBlank()) continue;
        try (var statement = connection.createStatement()) {
          statement.execute(
              Files.readString(root.resolve("server/db/migrations/up").resolve(name)));
        }
      }
    }
  }

  @Test
  void emptyAndPopulatedListsUseV2SchemaAndAdminDto() {
    var identity = mock(IdentityService.class);
    when(identity.requireRole("test-session", "ADMIN", false))
        .thenReturn(new Account("999", "ADMIN", "ACTIVE", true, 0, false));
    var service =
        new CourierCertificationV2Service(
            new com.zhangxuan.runrunv2.mapper.courier.CourierCertificationV2Mapper(db),
            identity,
            null,
            null,
            null);
    assertEquals(0L, service.adminList("test-session", null, null, 0, 20).get("totalElements"));
    long first =
        db.queryForObject(
            "INSERT INTO runrun_v2.app_users(phone,role,created_at) VALUES"
                + " ('test-courier-alpha','COURIER','2026-01-01') RETURNING id",
            Long.class);
    long second =
        db.queryForObject(
            "INSERT INTO runrun_v2.app_users(phone,role) VALUES ('test-courier-beta','COURIER')"
                + " RETURNING id",
            Long.class);
    db.update(
        "INSERT INTO"
            + " runrun_v2.courier_certifications(courier_id,status,submitted_at,submitted_photo_resource_id,submitted_photo_storage_key)"
            + " VALUES (?,'PENDING',now(),'test-photo','test/photo.png')",
        first);
    db.update(
        "INSERT INTO runrun_v2.courier_certifications(courier_id,status) VALUES (?,'NOT_APPLIED')",
        second);
    var page = service.adminList("test-session", "", "", 0, 1);
    assertEquals(2L, page.get("totalElements"));
    assertEquals(2L, page.get("totalPages"));
    var item = items(page).get(0);
    assertEquals(first, item.get("courierId"));
    assertNotNull(item.get("certificationId"));
    assertEquals("骑手 #" + first, item.get("displayName"));
    assertEquals("test-courier-alpha", item.get("phone"));
    assertEquals(true, item.get("photoUploaded"));
    assertEquals("test-photo", item.get("photoResourceId"));
    assertNotEquals(item.get("submittedAt"), item.get("registeredAt"));
    assertTrue(item.containsKey("rejectionReason"));
    assertNull(item.get("realName"));
    assertFalse(item.containsKey("submitted_photo_resource_id"));
    assertEquals(
        second, items(service.adminList("test-session", null, null, 1, 1)).get(0).get("courierId"));
    assertEquals(
        1L, service.adminList("test-session", "PENDING", null, 0, 20).get("totalElements"));
    assertEquals(
        second,
        items(service.adminList("test-session", null, " BETA ", 0, 20)).get(0).get("courierId"));
    assertEquals(
        0L, service.adminList("test-session", "PENDING", "beta", 0, 20).get("totalElements"));
    assertTrue(
        items(service.adminList("test-session", null, null, Integer.MAX_VALUE, 100)).isEmpty());
    var clamped = service.adminList("test-session", " ", " ", -1, 0);
    assertEquals(0, clamped.get("page"));
    assertEquals(1, clamped.get("size"));
    verify(identity, atLeastOnce()).requireRole("test-session", "ADMIN", false);
  }

  @Test
  void deniedIdentityStopsBeforeAnyDatabaseAccess() {
    var identity = mock(IdentityService.class);
    when(identity.requireRole("denied-session", "ADMIN", false))
        .thenThrow(new IdentityFailure(403, "FORBIDDEN"));
    var service = new CourierCertificationV2Service(null, identity, null, null, null);
    assertThrows(
        IdentityFailure.class, () -> service.adminList("denied-session", null, null, 0, 20));
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> items(Map<String, Object> page) {
    return (List<Map<String, Object>>) page.get("items");
  }
}
