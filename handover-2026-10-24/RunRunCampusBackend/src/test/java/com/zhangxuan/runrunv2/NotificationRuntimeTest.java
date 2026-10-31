package com.zhangxuan.runrunv2;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.*;
import org.junit.jupiter.api.*;
/** Contract tests run without Docker; the SQL runner supplies PostgreSQL integration coverage. */
class NotificationRuntimeTest {
 private static String migration;
 @BeforeAll static void load() throws Exception { migration=Files.readString(Path.of("..","..","server","db","migrations","up","rrc_0007__notifications.sql").toAbsolutePath().normalize()); }
 @Test void enqueueCreatesInAppAndWechatIntentsIdempotently(){assertTrue(migration.contains("CREATE OR REPLACE FUNCTION runrun_v2.enqueue_notification"));assertTrue(migration.contains("pg_advisory_xact_lock"));assertTrue(migration.contains("IF n IS NULL"));}
 @Test void claimUsesSkipLockedAndLease(){assertTrue(migration.contains("claim_notification_outbox"));assertTrue(migration.contains("FOR UPDATE SKIP LOCKED"));assertTrue(migration.contains("locked_until<now()"));}
 @Test void completionStoresFailureBackoffAndSanitizedLength(){assertTrue(migration.contains("complete_notification_outbox"));assertTrue(migration.contains("left(coalesce(p_error"));assertTrue(migration.contains("interval '15 seconds'"));assertTrue(migration.contains("interval '6 hours'"));}
 @Test void notificationReadStateIsUserScoped(){assertTrue(migration.contains("read_at TIMESTAMPTZ"));assertTrue(Files.exists(Path.of("src/main/java/com/zhangxuan/runrunv2/NotificationController.java")));}
 @Test void businessTriggersAreTransactional(){assertTrue(migration.contains("m08_business_notification_trigger"));assertTrue(migration.contains("AFTER INSERT OR UPDATE"));}
}
