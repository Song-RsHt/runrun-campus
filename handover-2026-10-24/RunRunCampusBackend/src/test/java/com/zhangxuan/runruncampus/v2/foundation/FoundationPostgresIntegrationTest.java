package com.zhangxuan.runruncampus.v2.foundation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhangxuan.runruncampus.admin.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class FoundationPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    final ObjectMapper json = new ObjectMapper();
    DriverManagerDataSource data;
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    AggregateLocks locks;
    IdempotentWrites writes;
    AuditFacts audit;
    String schema;

    @BeforeEach void prepare() {
        schema = "m01_" + UUID.randomUUID().toString().replace("-", "");
        data = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        new JdbcTemplate(data).execute("create schema " + schema);
        data.setUrl(POSTGRES.getJdbcUrl() + "&currentSchema=" + schema);
        jdbc = new JdbcTemplate(data);
        var manager = new DataSourceTransactionManager(data);
        tx = new TransactionTemplate(manager);
        locks = new AggregateLocks(jdbc);
        writes = new IdempotentWrites(jdbc, json, locks, manager);
        audit = new AuditFacts(jdbc);
    }
    Flyway migrate(String target) {
        var config = Flyway.configure().dataSource(data).schemas(schema).defaultSchema(schema);
        if (target != null) config.target(MigrationVersion.fromVersion(target));
        Flyway flyway = config.load(); flyway.migrate(); flyway.validate(); return flyway;
    }
    long user(String role) {
        return jdbc.queryForObject("insert into app_users(created_at,display_name,password_hash,phone,role) values(now(),'synthetic','not-a-real-password',?,?) returning id",
                Long.class, UUID.randomUUID().toString().replace("-", ""), role);
    }
    long order(long user) {
        Long point = jdbc.queryForObject("insert into service_points(code,name,coordinate_system,enabled,config_version) values(?,'synthetic','GCJ02',true,0) returning id", Long.class, UUID.randomUUID().toString());
        return jdbc.queryForObject("""
                insert into v2_orders(order_no,user_id,status,service_type,zone_id,service_point_id,address_snapshot,pricing_snapshot,policy_snapshot,original_fee_cents,effective_fee_cents,pending_payment_deadline)
                values(?,?,'PENDING_PAYMENT','LOBBY',(select id from delivery_zones where code='STANDARD'),?,'{}','{}','{}',200,200,now()+interval '30 minutes') returning id
                """, Long.class, UUID.randomUUID().toString(), user, point);
    }
    long task(long order, long courier, String state) {
        return jdbc.queryForObject("insert into fulfillment_tasks(order_id,courier_id,status) values(?,?,?) returning id", Long.class, order, courier, state);
    }
    long adjustment(long order, long actor, String state) {
        return jdbc.queryForObject("""
                insert into order_adjustments(order_id,requested_by,from_service_type,to_service_type,delta_cents,status,requested_at,remind_at,escalate_at)
                values(?,?,'LOBBY','DOOR',100,?,now(),now()+interval '2 hours',now()+interval '24 hours') returning id
                """, Long.class, order, actor, state);
    }
    long payment(long order, Long adjustment) {
        return jdbc.queryForObject("insert into payment_orders(order_id,adjustment_id,purpose,amount_cents,status) values(?,?,?,?, 'UNPAID') returning id",
                Long.class, order, adjustment, adjustment == null ? "ORIGINAL" : "SUPPLEMENT", 200);
    }
    long count(String table) { return jdbc.queryForObject("select count(*) from " + table, Long.class); }

    @Test void emptyDatabaseMigratesAll47AndValidates() {
        Flyway flyway = migrate(null);
        assertEquals("47", flyway.info().current().getVersion().getVersion());
        assertEquals(47, jdbc.queryForObject("select count(*) from flyway_schema_history where version is not null", Integer.class));
        assertEquals(0, count("v2_orders"));
        assertEquals(2, count("delivery_zones"));
        assertEquals(0L, jdbc.queryForObject("select count(*) from app_users where v2_admin_enabled", Long.class));
        assertFalse(jdbc.queryForObject("select v2_new_order_enabled from platform_settings", Boolean.class));
    }

    @Test void actualHandoverUpgradeFrom41PreservesLegacyFactsAndChecksums() {
        migrate("41");
        long customer = user("CUSTOMER"), courier = user("COURIER");
        jdbc.update("update app_users set courier_approved=true where id=?", courier);
        jdbc.update("""
                insert into courier_certifications(campus,real_name,student_id,status,submitted_at,courier_id,reviewed_at,approved_photo_key,approval_retained)
                values('historical','synthetic','synthetic','APPROVED',now(),?,now(),'synthetic-private-key',false)
                """, courier);
        jdbc.update("""
                insert into campus_orders(created_at,updated_at,destination,fee_cents,merchant,order_no,pickup_code,pickup_location,service_type,status,customer_id,pickup_station,delivery_zone,payment_method,payment_status,paid_cents,delivery_code)
                values(now(),now(),'synthetic',200,'synthetic','legacy-synthetic','legacy-code','synthetic','LOBBY','PENDING_ASSIGNMENT',?,'EAST_GATE_TAKEOUT','STANDARD','PERSONAL_QR_MANUAL','UNPAID',0,'123456')
                """, customer);
        jdbc.update("""
                insert into campus_orders(created_at,updated_at,destination,fee_cents,merchant,order_no,pickup_code,pickup_location,service_type,status,customer_id,pickup_station,delivery_zone,payment_method,payment_status,paid_cents,delivery_code)
                values(now(),now(),'synthetic',200,'synthetic','legacy-cash','legacy-code','synthetic','LOBBY','ACCEPTED',?,'EAST_GATE_TAKEOUT','STANDARD','CASH_ON_DELIVERY','UNPAID',0,'123456'),
                      (now(),now(),'synthetic',200,'synthetic','legacy-wechat','legacy-code','synthetic','LOBBY','CANCELLED',?,'EAST_GATE_TAKEOUT','STANDARD','WECHAT_APP','REFUNDING',200,'123456')
                """,customer,customer);
        long oldPayment=jdbc.queryForObject("""
                insert into wechat_payments(out_trade_no,business_type,status,amount_cents,expires_at,paid_at,created_at,updated_at,user_id,order_id)
                values('synthetic-payment','ORDER','PAID',200,now(),now(),now(),now(),?,(select id from campus_orders where order_no='legacy-wechat')) returning id
                """,Long.class,customer);
        jdbc.update("insert into wechat_refunds(out_refund_no,amount_cents,status,reason,created_at,updated_at,payment_id) values('synthetic-refund',200,'PROCESSING','synthetic',now(),now(),?)",oldPayment);
        var historicalOrders = jdbc.queryForList("select to_jsonb(o)::text as snapshot from campus_orders o order by id");
        var historicalPayments=jdbc.queryForList("select to_jsonb(p)::text as snapshot from wechat_payments p order by id");
        var historicalRefunds=jdbc.queryForList("select to_jsonb(r)::text as snapshot from wechat_refunds r order by id");
        var eligibility = jdbc.queryForList("select id,role,courier_approved from app_users order by id");
        var checksums = jdbc.queryForList("select version,checksum from flyway_schema_history where version is not null order by installed_rank");
        migrate(null);
        // V43 legitimately adds archive columns; compare original values, not a newly extended row JSON.
        try {
            var current=jdbc.queryForList("select to_jsonb(o)::text as snapshot from campus_orders o order by id");
            assertEquals(historicalOrders.size(),current.size());
            for(int i=0;i<historicalOrders.size();i++) {
                var before=json.readTree((String)historicalOrders.get(i).get("snapshot"));
                var after=json.readTree((String)current.get(i).get("snapshot"));
                before.fieldNames().forEachRemaining(k->assertEquals(before.get(k),after.get(k),k));
            }
        } catch (Exception e) { throw new AssertionError(e); }
        assertEquals(eligibility, jdbc.queryForList("select id,role,courier_approved from app_users order by id"));
        assertEquals(historicalPayments,jdbc.queryForList("select to_jsonb(p)::text as snapshot from wechat_payments p order by id"));
        assertEquals(historicalRefunds,jdbc.queryForList("select to_jsonb(r)::text as snapshot from wechat_refunds r order by id"));
        assertEquals(checksums, jdbc.queryForList("select version,checksum from flyway_schema_history where version::int<=41 order by installed_rank"));
        assertEquals(0, count("v2_orders")); assertEquals(0, count("fulfillment_tasks"));
        assertEquals(1, count("courier_certifications"));
        var cert = jdbc.queryForMap("select approved_photo_key,approved_photo_resource_id,version from courier_certifications");
        assertEquals("synthetic-private-key", cert.get("approved_photo_key"));
        assertNotNull(cert.get("approved_photo_resource_id")); assertEquals(0L, cert.get("version"));
        jdbc.update("update campus_orders set status='ACCEPTED' where order_no='legacy-synthetic'");
        assertEquals("ACCEPTED", jdbc.queryForObject("select status from campus_orders where order_no='legacy-synthetic'", String.class));
    }

    @Test void upgradeFrom43PreservesAdminDefaultsAndAuditVocabulary() {
        migrate("43"); long admin = user("ADMIN");
        jdbc.update("insert into admin_audit_logs(action,target_type,target_id,summary,created_at,administrator_id) values('ORDER_ARCHIVED','ORDER',1,'synthetic',now(),?)", admin);
        migrate(null);
        assertFalse(jdbc.queryForObject("select v2_admin_enabled from app_users where id=?", Boolean.class, admin));
        assertEquals(0L, jdbc.queryForObject("select v2_admin_security_version from app_users where id=?", Long.class, admin));
        assertEquals(1, count("admin_audit_logs"));
        tx.executeWithoutResult(s -> audit.administrator(admin, AdminAuditAction.V2_ADMIN_ACCESS_CHANGED,
                AdminAuditTargetType.APP_USER, admin, "synthetic reason", "enable-1", json.createObjectNode()));
        assertEquals(2, count("admin_audit_logs"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update app_users set v2_admin_enabled=true where id=?", user("CUSTOMER")));
    }

    @Test void paymentUniquenessAllowsDifferentSupplementAdjustments() {
        migrate(null); long actor = user("CUSTOMER"), o = order(actor);
        payment(o, null);
        assertThrows(DataIntegrityViolationException.class, () -> payment(o, null));
        long a = adjustment(o, actor, "COMPLETED"), b = adjustment(o, actor, "COMPLETED");
        payment(o, a); payment(o, b);
        assertThrows(DataIntegrityViolationException.class, () -> payment(o, a));
        long p = jdbc.queryForObject("select id from payment_orders where purpose='ORIGINAL'", Long.class);
        jdbc.update("insert into payment_attempts(payment_order_id,attempt_no,status) values(?,1,'UNKNOWN')", p);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into payment_attempts(payment_order_id,attempt_no,status) values(?,2,'PROCESSING')", p));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update payment_orders set purpose='ORIGINAL' where adjustment_id=?", b));
    }

    @Test void tasksAndAdjustmentsHaveOneActiveSlotAndRejectCrossOrderLinks() {
        migrate(null); long actor = user("CUSTOMER"), courier = user("COURIER"), a = order(actor), b = order(actor);
        long t = task(a,courier,"ACTIVE");
        assertThrows(DataIntegrityViolationException.class, () -> task(a,courier,"ACTIVE"));
        task(a,courier,"OFFERED"); task(a,courier,"OFFERED");
        adjustment(a,actor,"PENDING_REVIEW");
        assertThrows(DataIntegrityViolationException.class, () -> adjustment(a,actor,"PENDING_SUPPLEMENT"));
        long otherAdjustment = adjustment(b,actor,"COMPLETED");
        assertThrows(DataIntegrityViolationException.class, () -> payment(a,otherAdjustment));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into v2_order_proofs(order_id,task_id,uploaded_by,kind,storage_key,storage_status) values(?,?,?,'PICKUP','synthetic','AVAILABLE')", b,t,courier));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into location_events(order_id,task_id,courier_id,event_type,coordinate_system,received_at,request_id,accepted,latitude,longitude) values(?,?,?,'ARRIVAL','GCJ02',now(),'x',true,1,1)",a,t,actor));
    }

    @Test void amountsFlowCoordinatesAndInitialVersionRejectIllegalValues() {
        migrate(null); long actor=user("CUSTOMER"), courier=user("COURIER"), o=order(actor), t=task(o,courier,"ACTIVE");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update v2_orders set original_fee_cents=-1 where id=?",o));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update v2_orders set order_flow_version=1 where id=?",o));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update v2_orders set version=3 where id=?",o));
        jdbc.update("update v2_orders set status='PENDING_ASSIGNMENT' where id=?",o);
        assertEquals(1L,jdbc.queryForObject("select version from v2_orders where id=?",Long.class,o));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into fulfillment_tasks(order_id,status,version) values(?,'ACTIVE',2)",o));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into location_events(order_id,task_id,courier_id,event_type,coordinate_system,received_at,request_id,accepted,latitude) values(?,?,?,'ARRIVAL','GCJ02',now(),'x',true,1)",o,t,courier));
        jdbc.update("insert into location_events(order_id,task_id,courier_id,event_type,coordinate_system,received_at,request_id,accepted,latitude,longitude) values(?,?,?,'ARRIVAL','GCJ02',now(),'x',true,1,1)",o,t,courier);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update location_events set deleted_at=now()"));
        jdbc.update("update location_events set latitude=null,longitude=null,deleted_at=now()");
    }

    @Test void platformNullBeneficiaryDeduplicatesAndFinanceFactsAreAppendOnly() {
        migrate(null); long actor=user("CUSTOMER"), o=order(actor);
        String sql="insert into settlement_entries(order_id,beneficiary_type,source_type,source_id,kind,delta_cents,reason) values(?,'PLATFORM','PAYMENT','synthetic','ORIGINAL',60,'synthetic')";
        jdbc.update(sql,o);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(sql,o));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update settlement_entries set delta_cents=0"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("delete from settlement_entries"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into payout_registrations(order_id,beneficiary_type,amount_cents,reference,reason,registered_by,registered_at) values(?,'COURIER',1,'x','synthetic',?,now())",o,actor));
    }

    @Test void evidenceSubjectIsExclusiveAndCrossOrderProofCannotBeHeld() {
        migrate(null); long actor=user("CUSTOMER"), a=order(actor), b=order(actor);
        long proof=jdbc.queryForObject("insert into v2_order_proofs(order_id,uploaded_by,kind,storage_key,storage_status) values(?,?,'PICKUP','synthetic','AVAILABLE') returning id",Long.class,a,actor);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into evidence_holds(order_id,proof_id,source_type,source_id,reason,owner_id,review_due_at) values(?,?,'CASE','synthetic','synthetic',?,now())",b,proof,actor));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into evidence_holds(source_type,source_id,reason,owner_id,review_due_at) values('CASE','synthetic','synthetic',?,now())",actor));
        jdbc.update("insert into evidence_holds(order_id,source_type,source_id,reason,owner_id,review_due_at) values(?,'CASE','1','synthetic',?,now()),(?,'CASE','2','synthetic',?,now())",a,actor,a,actor);
        assertEquals(2,count("evidence_holds"));
    }

    @Test void legacyPhotoReplacementAndReviewAdvanceSharedVersionAndKeepStableResource() {
        migrate(null); long courier=user("COURIER");
        long id=jdbc.queryForObject("insert into courier_certifications(campus,real_name,student_id,status,submitted_at,courier_id,submitted_photo_key) values('','','','PENDING',now(),?,'synthetic-1') returning id",Long.class,courier);
        String old=jdbc.queryForObject("select submitted_photo_resource_id from courier_certifications where id=?",String.class,id);
        jdbc.update("update courier_certifications set submitted_photo_key='synthetic-2' where id=?",id);
        String replacement=jdbc.queryForObject("select submitted_photo_resource_id from courier_certifications where id=?",String.class,id);
        assertNotEquals(old,replacement);
        jdbc.update("update courier_certifications set approved_photo_key=submitted_photo_key,submitted_photo_key=null,status='APPROVED',version=version+1 where id=?",id);
        assertEquals(replacement,jdbc.queryForObject("select approved_photo_resource_id from courier_certifications where id=?",String.class,id));
        assertEquals(2L,jdbc.queryForObject("select version from courier_certifications where id=?",Long.class,id));
        assertNull(jdbc.queryForObject("select submitted_photo_resource_id from courier_certifications where id=?",String.class,id));
    }

    @Test void idempotencyReplaysWithoutSideEffectsAndRejectsDifferentContentAndRevokedAccess() {
        migrate(null); long actor=user("CUSTOMER"), o=order(actor);
        AtomicInteger calls=new AtomicInteger(), authorizations=new AtomicInteger();
        var content=json.createObjectNode().put("orderId",Long.toString(o)).put("purpose","ORIGINAL");
        var plan=List.of(new AggregateLocks.Subject(AggregateLocks.Kind.ORDER,o));
        java.util.function.Supplier<IdempotentWrites.Result> action=()->{
            calls.incrementAndGet(); ResourceChecks.version(jdbc.queryForObject("select version from v2_orders where id=?",Long.class,o),0L);
            jdbc.update("update v2_orders set status='PENDING_ASSIGNMENT' where id=?",o);
            audit.order(o,null,new AuditFacts.Actor(AuditFacts.ActorKind.USER,actor,null),"SYNTHETIC_TEST","request-1",json.createObjectNode());
            return new IdempotentWrites.Result("ORDER",Long.toString(o),json.createObjectNode().put("id",Long.toString(o)));
        };
        writes.execute(actor,"test-route","key",content,plan,authorizations::incrementAndGet,action);
        writes.execute(actor,"test-route","key",content,plan,authorizations::incrementAndGet,action);
        assertEquals(1,calls.get()); assertEquals(2,authorizations.get());
        assertEquals(1,count("v2_order_events"));
        assertEquals(1L,jdbc.queryForObject("select version from v2_orders where id=?",Long.class,o));
        assertEquals(V2Error.IDEMPOTENCY_KEY_CONFLICT,assertThrows(V2Exception.class,()->writes.execute(actor,"test-route","key",content.deepCopy().put("purpose","SUPPLEMENT"),plan,()->{},action)).error());
        assertEquals(V2Error.RESOURCE_NOT_FOUND,assertThrows(V2Exception.class,()->writes.execute(actor,"test-route","key",content,plan,()->ResourceChecks.visible(false),action)).error());
    }

    @Test void businessAuditAndKeyRollbackTogether() {
        migrate(null); long actor=user("CUSTOMER"),o=order(actor);
        assertThrows(IllegalStateException.class,()->writes.execute(actor,"test-route","key",json.createObjectNode(),List.of(),()->{},()->{
            jdbc.update("update v2_orders set status='CANCELLED' where id=?",o);
            audit.order(o,null,new AuditFacts.Actor(AuditFacts.ActorKind.SYSTEM,null,"m01-test"),"SYNTHETIC_TEST","x",json.createObjectNode());
            throw new IllegalStateException("synthetic failure");
        }));
        assertEquals(0,count("idempotency_requests"));assertEquals(0,count("v2_order_events"));
        assertEquals(0L,jdbc.queryForObject("select version from v2_orders where id=?",Long.class,o));
        assertThrows(IllegalStateException.class,()->audit.order(o,null,new AuditFacts.Actor(AuditFacts.ActorKind.SYSTEM,null,"test"),"TEST","x",json.createObjectNode()));
    }

    @Test void concurrentDuplicateRunsOnceAndConflictingContentLoses() throws Exception {
        migrate(null); long actor=user("CUSTOMER"); AtomicInteger calls=new AtomicInteger();
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        Callable<IdempotentWrites.Result> action=()->{
            assertTrue(start.await(5,TimeUnit.SECONDS));
            return writes.execute(actor,"concurrent","key",json.createObjectNode(),List.of(),()->{},()->{
                calls.incrementAndGet(); return new IdempotentWrites.Result("TEST","1",json.createObjectNode().put("value",1));
            });
        };
        try {
            var first=pool.submit(action); var second=pool.submit(action); start.countDown();
            assertEquals(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));assertEquals(1,calls.get());assertEquals(1,count("idempotency_requests"));
        } finally { pool.shutdownNow(); }
    }

    @Test void typedLocksSerializeOrdersWithoutAliasingCertificationAndRejectInversion() throws Exception {
        migrate(null); long actor=user("CUSTOMER"),o=order(actor);
        var pool=Executors.newFixedThreadPool(2); var acquired=new CountDownLatch(1); var release=new CountDownLatch(1); var secondEntered=new CountDownLatch(1);
        try {
            var first=pool.submit(()->tx.execute(s->{locks.acquire(List.of(new AggregateLocks.Subject(AggregateLocks.Kind.ORDER,o)));acquired.countDown();
                try { assertTrue(release.await(5,TimeUnit.SECONDS)); } catch(InterruptedException e){throw new RuntimeException(e);} return null;}));
            assertTrue(acquired.await(5,TimeUnit.SECONDS));
            var second=pool.submit(()->tx.execute(s->{locks.acquire(List.of(new AggregateLocks.Subject(AggregateLocks.Kind.ORDER,o)));secondEntered.countDown();return null;}));
            assertFalse(secondEntered.await(200,TimeUnit.MILLISECONDS));release.countDown();first.get(5,TimeUnit.SECONDS);second.get(5,TimeUnit.SECONDS);
        } finally { release.countDown();pool.shutdownNow(); }
        tx.executeWithoutResult(s->{locks.acquire(List.of(new AggregateLocks.Subject(AggregateLocks.Kind.ORDER,o)));
            assertThrows(IllegalStateException.class,()->locks.acquire(List.of(new AggregateLocks.Subject(AggregateLocks.Kind.ACCOUNT,actor))));});
    }

    @Test void notificationDedupAndEveryNewForeignKeyHasLeadingIndex() {
        migrate(null);long actor=user("CUSTOMER");
        jdbc.update("insert into notification_outbox(recipient_id,event_key,channel,payload,status,attempt_count) values(?,'synthetic','IN_APP','{}','PENDING',0)",actor);
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("insert into notification_outbox(recipient_id,event_key,channel,payload,status,attempt_count) values(?,'synthetic','IN_APP','{}','PENDING',0)",actor));
        var unindexed=jdbc.queryForList("""
            select c.conrelid::regclass::text as relation,c.conname from pg_constraint c
            join pg_class r on r.oid=c.conrelid join pg_namespace n on n.oid=r.relnamespace
            where c.contype='f' and n.nspname=? and r.relname in
            ('wechat_identities','v2_orders','fulfillment_tasks','handover_records','v2_order_proofs','location_events','after_sales_cases',
             'v2_order_events','evidence_holds','evidence_deletion_audits','courier_certification_events','order_adjustments','payment_orders',
             'payment_attempts','refund_orders','debts','debt_events','idempotency_requests','settlement_entries','rider_allocations',
             'rider_allocation_lines','settlement_freezes','freeze_events','payout_registrations','user_notifications','notification_outbox')
            and not exists(select 1 from pg_index i where i.indrelid=c.conrelid and i.indisvalid
              and (select array_agg(k.attnum order by k.ord) from unnest(i.indkey) with ordinality k(attnum,ord) where k.ord<=cardinality(c.conkey))=c.conkey)
            """,schema);
        assertEquals(List.of(),unindexed);
    }

    @Test void storedOwnershipAndCurrentAdministratorVersionAreAlwaysChecked() {
        migrate(null);long owner=user("CUSTOMER"),other=user("CUSTOMER"),admin=user("ADMIN"),o=order(owner);
        var access=new ResourceAccess(jdbc);
        assertEquals(0,access.ownedOrder(owner,o));
        assertEquals(V2Error.RESOURCE_NOT_FOUND,assertThrows(V2Exception.class,()->access.ownedOrder(other,o)).error());
        assertEquals(V2Error.V2_ADMIN_NOT_ENABLED,assertThrows(V2Exception.class,()->access.enabledAdministrator(admin,0)).error());
        jdbc.update("update app_users set v2_admin_enabled=true,v2_admin_security_version=1 where id=?",admin);
        access.enabledAdministrator(admin,1);
        assertEquals(V2Error.V2_ADMIN_NOT_ENABLED,assertThrows(V2Exception.class,()->access.enabledAdministrator(admin,0)).error());
        jdbc.update("update app_users set v2_admin_enabled=false,v2_admin_security_version=2 where id=?",admin);
        assertEquals(V2Error.V2_ADMIN_NOT_ENABLED,assertThrows(V2Exception.class,()->access.enabledAdministrator(admin,1)).error());
        jdbc.update("update app_users set v2_admin_enabled=true,enabled=false where id=?",admin);
        assertEquals(V2Error.AUTHENTICATION_FAILED,assertThrows(V2Exception.class,()->access.enabledAdministrator(admin,2)).error());
        jdbc.update("update app_users set enabled=true,deleted_at=now() where id=?",admin);
        assertEquals(V2Error.AUTHENTICATION_FAILED,assertThrows(V2Exception.class,()->access.enabledAdministrator(admin,2)).error());
    }

    @Test void staleVersionAndFailedAuditLeaveNoSideEffectsOrIdempotencyKey() {
        migrate(null);long actor=user("CUSTOMER"),o=order(actor);var access=new ResourceAccess(jdbc);
        jdbc.update("update v2_orders set effective_fee_cents=201 where id=?",o);
        var plan=List.of(new AggregateLocks.Subject(AggregateLocks.Kind.ORDER,o));
        assertEquals(V2Error.ORDER_STATE_CONFLICT,assertThrows(V2Exception.class,()->writes.execute(actor,"test","stale",json.createObjectNode(),plan,()->access.ownedOrder(actor,o),()->{
            ResourceChecks.version(access.ownedOrder(actor,o),0L);
            throw new AssertionError("stale callback must stop before effects");
        })).error());
        assertThrows(DataIntegrityViolationException.class,()->writes.execute(actor,"test","audit-failure",json.createObjectNode(),plan,()->{},()->{
            jdbc.update("update v2_orders set effective_fee_cents=202 where id=?",o);
            audit.order(o,null,new AuditFacts.Actor(AuditFacts.ActorKind.USER,Long.MAX_VALUE,null),"TEST","x",json.createObjectNode());
            return new IdempotentWrites.Result("ORDER",Long.toString(o),json.createObjectNode());
        }));
        assertEquals(0,count("idempotency_requests"));assertEquals(0,count("v2_order_events"));
        assertEquals(201L,jdbc.queryForObject("select effective_fee_cents from v2_orders where id=?",Long.class,o));
        assertEquals(1L,jdbc.queryForObject("select version from v2_orders where id=?",Long.class,o));
    }

    @Test void privateSnapshotsAreRejectedAndCanonicalObjectKeyOrderReplays() {
        migrate(null);long actor=user("CUSTOMER");
        assertThrows(IllegalArgumentException.class,()->writes.execute(actor,"test","secret",json.createObjectNode(),List.of(),()->{},()->
            new IdempotentWrites.Result("TEST","1",json.createObjectNode().put("storageKey","synthetic"))));
        assertEquals(0,count("idempotency_requests"));
        var a=json.createObjectNode().put("orderId","1").put("purpose","ORIGINAL");
        var b=json.createObjectNode().put("purpose","ORIGINAL").put("orderId","1");
        assertEquals(writes.hash(a),writes.hash(b));
        assertNotEquals(writes.hash(a),writes.hash(b.deepCopy().put("orderId","2")));
    }

    @Test void allocationsRejectCrossOrderTaskAndMismatchedCourier() {
        migrate(null);long actor=user("ADMIN"),courier=user("COURIER"),other=user("COURIER"),a=order(actor),b=order(actor);
        long task=task(a,courier,"DELIVERED");
        long allocation=jdbc.queryForObject("insert into rider_allocations(order_id,allocation_version,status,automatic,reason) values(?,1,'APPROVED',true,'synthetic') returning id",Long.class,b);
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("insert into rider_allocation_lines(allocation_id,task_id,courier_id,amount_cents) values(?,?,?,140)",allocation,task,courier));
        jdbc.update("update rider_allocations set order_id=? where id=?",a,allocation);
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("insert into rider_allocation_lines(allocation_id,task_id,courier_id,amount_cents) values(?,?,?,140)",allocation,task,other));
        jdbc.update("insert into rider_allocation_lines(allocation_id,task_id,courier_id,amount_cents) values(?,?,?,140)",allocation,task,courier);
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update rider_allocations set order_id=? where id=?",b,allocation));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update fulfillment_tasks set order_id=? where id=?",b,task));
    }
}
