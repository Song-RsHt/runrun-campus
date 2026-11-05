package com.zhangxuan.runrunv2;

import static com.zhangxuan.runrunv2.PaymentModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(classes=IdentityApplication.class,properties={"spring.config.name=application-v2-test","rrc.identity.environment=test",
        "rrc.identity.app-id=synthetic-app","rrc.identity.app-secret=synthetic-secret","rrc.payment.mock-enabled=true","rrc.fulfillment.capacity=3","rrc.fulfillment.enabled=true","rrc.fulfillment.completion-delay-ms=86400000","rrc.payment.expiry-delay-ms=86400000"})
@AutoConfigureMockMvc(print=org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
// Close scheduled workers and JDBC pools with the class-scoped database fixture.
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class FulfillmentIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) throws Exception {
        try(var conn=DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()); var sql=conn.createStatement()) {
            for(String name:List.of("rrc_0001__create_v2_schema.sql","rrc_0002__create_identity_foundation.sql","rrc_0003__identity_runtime.sql","rrc_0100__orders_and_service_config.sql","rrc_0200__mock_payments.sql","rrc_0300__fulfillment.sql"))
                sql.execute(Files.readString(Path.of("../../server/db/migrations/up",name)));
        }
        registry.add("spring.datasource.url",POSTGRES::getJdbcUrl); registry.add("spring.datasource.username",POSTGRES::getUsername); registry.add("spring.datasource.password",POSTGRES::getPassword);
        registry.add("rrc.identity.encryption-key",()->Base64.getEncoder().encodeToString(new byte[32]));
        registry.add("rrc.identity.hash-key",()->Base64.getEncoder().encodeToString("synthetic-different-hash-key-000001".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    @Autowired FulfillmentQueries queries;
    @Autowired FulfillmentService fulfillment;
    @MockitoBean FulfillmentEvidence evidence;
    @Autowired JdbcTemplate db;
    @Autowired PaymentService payments;
    @Autowired MockPaymentAdapter adapter;
    @Autowired OrderService orders;
    @Autowired IdentitySecrets secrets;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc http;
    @Autowired OrderJson json;
    @MockitoBean Clock clock;
    @MockitoBean WechatGateway wechat;
    String user,other,admin,courier; int seq;
    @BeforeEach void setup() {
        db.execute("TRUNCATE runrun_v2.app_users,runrun_v2.service_points,runrun_v2.delivery_zones,runrun_v2.platform_settings RESTART IDENTITY CASCADE");
        // Simulator event tables deliberately have no identity FK; clear only synthetic fixtures.
        db.execute("TRUNCATE runrun_v2.mock_channel_results,runrun_v2.payment_result_events");
        user=account("13800000001","USER"); other=account("13800000002","USER"); admin=account("13800000003","ADMIN"); courier=account("13800000004","COURIER"); seq=0;
        long aid=db.queryForObject("SELECT id FROM runrun_v2.app_users WHERE role='ADMIN'",Long.class);
        db.update("INSERT INTO runrun_v2.platform_settings(id,new_order_enabled,review_owner_id) VALUES(1,true,?)",aid);
        db.update("INSERT INTO runrun_v2.delivery_zones(code,name,package_surcharge_cents) VALUES('STANDARD','合成区域',0)");
        db.update("INSERT INTO runrun_v2.service_points(code,name,latitude,longitude) VALUES('TEST','合成点',30,120)");
        now("2026-10-27T01:00:00Z");
        db.update("INSERT INTO runrun_v2.courier_certifications(courier_id,status,eligible) SELECT id,'APPROVED',true FROM runrun_v2.app_users WHERE role='COURIER'");
        when(evidence.verify(any(),anyString(),any())).thenAnswer(inv -> new FulfillmentEvidence.Verified(inv.getArgument(0), "private-test-"+inv.getArgument(1)));
        when(evidence.issue(any(),anyString(),anyLong(),any())).thenAnswer(inv -> new FulfillmentModels.Ticket("ticket-"+UUID.randomUUID(),"https://isolated.invalid/private-upload",((OffsetDateTime)inv.getArgument(3)).plusMinutes(5)));
    }
    String account(String phone,String role) {
        long id=db.queryForObject("INSERT INTO runrun_v2.app_users(phone,role,v2_admin_enabled) VALUES(?,?,?) RETURNING id",Long.class,phone,role,role.equals("ADMIN"));
        String token=secrets.token(); db.update("INSERT INTO runrun_v2.user_sessions(user_id,token_hash,expires_at) VALUES(?,?,now()+interval '1 day')",id,secrets.hash("session",token)); return token;
    }
    void now(String instant) { when(clock.instant()).thenReturn(Instant.parse(instant)); }
    String key() { return "synthetic-"+(++seq); }
    OrderModels.Created create() { return orders.create(user,key(),new OrderModels.CreateRequest(OrderModels.ServiceType.PACKAGE_XL,"STANDARD","TEST","合成目的地",0L)); }
    Started start(String id) { return payments.start(user,id,key(),new VersionRequest(payments.get(user,id).payment().version())); }
    Attempt result(String id,Result result) {
        long version=db.queryForObject("SELECT version FROM runrun_v2.payment_attempts WHERE id=?",Long.class,Long.valueOf(id));
        return payments.paymentResult(admin,id,key(),new ResultRequest(version,result,"event-"+key()));
    }
    Refund resultRefund(String id,Result result) {
        long version=db.queryForObject("SELECT version FROM runrun_v2.refund_orders WHERE id=?",Long.class,Long.valueOf(id));
        return payments.refundResult(admin,id,key(),new ResultRequest(version,result,"event-"+key()));
    }
    OrderModels.Created paid() { var c=create(); result(start(c.originalPayment().id()).attempt().id(),Result.SUCCESS); return c; }
    Summary summary(OrderModels.Created c) { return payments.get(user,c.originalPayment().id()); }
    Cancelled cancel(OrderModels.Created c) { return payments.cancel(user,c.order().id(),key(),new CancelRequest(orders.get(user,c.order().id()).version(),"合成取消原因")); }
    AdjustmentFunds approve(OrderModels.Created c,String adjustment,long delta) {
        return new TransactionTemplate(transactions).execute(s->payments.approvedAdjustment(admin,c.order().id(),adjustment,delta,orders.get(user,c.order().id()).version(),"合成已批准规格差额",key()));
    }
    void failure(String code,org.junit.jupiter.api.function.Executable action) { assertEquals(code,assertThrows(IdentityFailure.class,action).code); }
    long count(String table) { return db.queryForObject("SELECT count(*) FROM runrun_v2."+table,Long.class); }
    @SafeVarargs final void race(Callable<?>... actions) throws Exception {
        var pool=Executors.newFixedThreadPool(actions.length); var start=new CountDownLatch(1);
        try { var futures=new ArrayList<Future<?>>(); for(var action:actions) futures.add(pool.submit(()->{ start.await(); return action.call(); })); start.countDown(); for(var future:futures) future.get(20,TimeUnit.SECONDS); }
        finally { pool.shutdownNow(); }
    }

    long v(String order) { return orders.get(user,order).version(); }
    String nextCourier() {
        String token=account("13800000005","COURIER");
        db.update("INSERT INTO runrun_v2.courier_certifications(courier_id,status,eligible) SELECT id,'APPROVED',true FROM runrun_v2.app_users WHERE phone='13800000005'"); return token;
    }
    String accept(String order) { return fulfillment.accept(courier,order,key(),new FulfillmentModels.Version(v(order))).task().id(); }
    FulfillmentModels.Proof proof(String token,String order,String task,FulfillmentModels.Kind kind) {
        return fulfillment.proof(token,order,key(),new FulfillmentModels.RegisterProof(v(order),kind,task,"synthetic-ticket-"+key()));
    }
    FulfillmentModels.Event location(String token,String order,String task,FulfillmentModels.EventType kind,String request) {
        return fulfillment.location(token,order,key(),new FulfillmentModels.Location(v(order),task,kind,new java.math.BigDecimal("30"),new java.math.BigDecimal("120"),"GCJ02",request));
    }
    String picked(String order) {
        String task=accept(order); var p=proof(courier,order,task,FulfillmentModels.Kind.PICKUP);
        fulfillment.pickup(courier,order,key(),new FulfillmentModels.Pickup(v(order),task,p.id())); return task;
    }
    FulfillmentModels.Result delivered(String order,String task) {
        var photo=proof(courier,order,task,FulfillmentModels.Kind.DELIVERY);
        var gps=location(courier,order,task,FulfillmentModels.EventType.DELIVERY,key());
        return fulfillment.deliver(courier,order,key(),new FulfillmentModels.Deliver(v(order),task,photo.id(),gps.eventId()));
    }
    @Test void administratorPerformanceReadsDeliveredTasksAndRetainsAuthorization() throws Exception {
        String order=paid().order().id(), task=picked(order);
        delivered(order,task);
        long courierId=db.queryForObject("SELECT courier_id FROM runrun_v2.fulfillment_tasks WHERE id=?",Long.class,Long.valueOf(task));
        String route="/api/v2/courier/certification/admin/"+courierId+"/performance";
        http.perform(get(route).header("Authorization","Bearer "+admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCompletedPackageOrders").value(1))
                .andExpect(jsonPath("$.specialLargeCompletedPackageOrders").value(1))
                .andExpect(jsonPath("$.orders.totalElements").value(1))
                .andExpect(jsonPath("$.orders.items[0].id").value(Long.parseLong(order)))
                .andExpect(jsonPath("$.orders.items[0].fee_cents").value(800));
        http.perform(get(route).param("page",Integer.toString(Integer.MAX_VALUE)).param("size","100")
                .header("Authorization","Bearer "+admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.orders.items").isEmpty());
        http.perform(get(route).header("Authorization","Bearer "+user)).andExpect(status().isForbidden());
    }
    @Test void concurrentSameOrderHasExactlyOneWinner() throws Exception {
        String order=paid().order().id(), second=nextCourier(); long version=v(order);
        var wins=new java.util.concurrent.atomic.AtomicInteger();
        race(()->attemptAccept(courier,order,version,"race-a",wins),()->attemptAccept(second,order,version,"race-b",wins));
        assertEquals(1,wins.get()); assertEquals(1,count("fulfillment_tasks")); assertEquals("ACCEPTED",orders.get(user,order).status());
    }
    Object attemptAccept(String token,String order,long version,String key,java.util.concurrent.atomic.AtomicInteger wins) {
        try { var result=fulfillment.accept(token,order,key,new FulfillmentModels.Version(version)); wins.incrementAndGet(); return result; }
        catch(IdentityFailure ex) { assertEquals("ORDER_STATE_CONFLICT",ex.code); return null; }
    }
    @Test void sameRiderCapacitySerializesAcrossDifferentOrders() throws Exception {
        String a=paid().order().id(),b=paid().order().id(),c=paid().order().id(),d=paid().order().id(); accept(a); accept(b);
        var wins=new java.util.concurrent.atomic.AtomicInteger();
        race(()->capacityAccept(c,"cap-c",wins),()->capacityAccept(d,"cap-d",wins));
        assertEquals(1,wins.get()); assertEquals(3L,db.queryForObject("SELECT count(*) FROM runrun_v2.fulfillment_tasks WHERE status='ACTIVE'",Long.class));
    }
    Object capacityAccept(String order,String key,java.util.concurrent.atomic.AtomicInteger wins) {
        try { var result=fulfillment.accept(courier,order,key,new FulfillmentModels.Version(v(order))); wins.incrementAndGet(); return result; }
        catch(IdentityFailure ex) { assertEquals("COURIER_CAPACITY_EXCEEDED",ex.code); return null; }
    }
    @Test void duplicateAcceptPickupAndDeliveryAreIdempotentButNewWritesConflict() {
        String order=paid().order().id(); var accept=new FulfillmentModels.Version(v(order));
        var first=fulfillment.accept(courier,order,"accept-repeat",accept);
        assertEquals(first,fulfillment.accept(courier,order,"accept-repeat",accept));
        String task=first.task().id(); var p=proof(courier,order,task,FulfillmentModels.Kind.PICKUP);
        var pickup=new FulfillmentModels.Pickup(v(order),task,p.id());
        var picked=fulfillment.pickup(courier,order,"pickup-repeat",pickup);
        assertEquals(picked,fulfillment.pickup(courier,order,"pickup-repeat",pickup));
        failure("ORDER_STATE_CONFLICT",()->fulfillment.pickup(courier,order,key(),new FulfillmentModels.Pickup(v(order),task,p.id())));
        var d=proof(courier,order,task,FulfillmentModels.Kind.DELIVERY); var gps=location(courier,order,task,FulfillmentModels.EventType.DELIVERY,"delivery-location");
        var body=new FulfillmentModels.Deliver(v(order),task,d.id(),gps.eventId());
        var done=fulfillment.deliver(courier,order,"deliver-repeat",body);
        assertEquals(done,fulfillment.deliver(courier,order,"deliver-repeat",body));
        failure("ORDER_STATE_CONFLICT",()->fulfillment.deliver(courier,order,key(),new FulfillmentModels.Deliver(v(order),task,d.id(),gps.eventId())));
        assertEquals(done.order().version(),v(order)); assertEquals(1L,db.queryForObject("SELECT count(*) FROM runrun_v2.v2_order_events WHERE event_type='FULFILLMENT_DELIVERED'",Long.class));
    }
    @Test void pickupNeedsAvailableProofFromSameTaskAndOrder() {
        String a=paid().order().id(),b=paid().order().id(),ta=accept(a),tb=accept(b);
        failure("PROOF_NOT_READY",()->fulfillment.pickup(courier,a,key(),new FulfillmentModels.Pickup(v(a),ta,"999")));
        var p=proof(courier,b,tb,FulfillmentModels.Kind.PICKUP);
        failure("PROOF_NOT_READY",()->fulfillment.pickup(courier,a,key(),new FulfillmentModels.Pickup(v(a),ta,p.id())));
        assertEquals("ACCEPTED",orders.get(user,a).status());
    }
    @Test void deliveryNeedsPhotoAndAcceptedLocationWithSameOwnership() {
        String a=paid().order().id(),b=paid().order().id(),ta=picked(a),tb=picked(b);
        var p=proof(courier,a,ta,FulfillmentModels.Kind.DELIVERY); var gps=location(courier,b,tb,FulfillmentModels.EventType.DELIVERY,"loc-b");
        failure("PROOF_NOT_READY",()->fulfillment.deliver(courier,a,key(),new FulfillmentModels.Deliver(v(a),ta,p.id(),gps.eventId())));
        assertEquals("PICKED_UP",orders.get(user,a).status());
    }
    @Test void locationRequestIdDeduplicatesEvenWithNewHttpKeyAndOldVersion() {
        String order=paid().order().id(),task=accept(order);
        var body=new FulfillmentModels.Location(v(order),task,FulfillmentModels.EventType.ARRIVAL,new java.math.BigDecimal("30"),new java.math.BigDecimal("120"),"GCJ02","same-gps");
        var event=fulfillment.location(courier,order,"gps-key",body); long version=v(order);
        assertEquals(event,fulfillment.location(courier,order,"gps-key",body));
        assertEquals(event,fulfillment.location(courier,order,"gps-new-key",body)); assertEquals(version,v(order)); assertEquals(1,count("location_events"));
        var changed=new FulfillmentModels.Location(v(order),task,FulfillmentModels.EventType.ARRIVAL,new java.math.BigDecimal("31"),new java.math.BigDecimal("120"),"GCJ02","same-gps");
        failure("IDEMPOTENCY_KEY_CONFLICT",()->fulfillment.location(courier,order,key(),changed));
        assertEquals(OffsetDateTime.parse("2026-10-27T01:00:00Z"),event.receivedAt());
    }
    @Test void concurrentLocationRetryInsertsOnlyOneEvent() throws Exception {
        String order=paid().order().id(),task=accept(order);
        var body=new FulfillmentModels.Location(v(order),task,FulfillmentModels.EventType.ARRIVAL,new java.math.BigDecimal("30"),new java.math.BigDecimal("120"),"GCJ02","same-gps");
        race(()->fulfillment.location(courier,order,"gps-race-a",body),()->fulfillment.location(courier,order,"gps-race-b",body));
        assertEquals(1,count("location_events"));
    }
    @Test void secondDeliveryLocationIsRetainedButCannotReplaceSelectedEvidence() {
        String order=paid().order().id(),task=picked(order);
        var first=location(courier,order,task,FulfillmentModels.EventType.DELIVERY,"first"); var second=location(courier,order,task,FulfillmentModels.EventType.DELIVERY,"second");
        assertTrue(first.accepted()); assertFalse(second.accepted()); assertEquals(2,count("location_events"));
        var p=proof(courier,order,task,FulfillmentModels.Kind.DELIVERY);
        failure("PROOF_NOT_READY",()->fulfillment.deliver(courier,order,key(),new FulfillmentModels.Deliver(v(order),task,p.id(),second.eventId())));
    }
    @Test void releaseImmediatelyRecoversCapacityAndRevokesAllOldWrites() {
        String order=paid().order().id(),task=accept(order);
        proof(courier,order,task,FulfillmentModels.Kind.SPECIFICATION);
        var body=new FulfillmentModels.Release(v(order),task,"合成释放原因");
        var released=fulfillment.release(courier,order,"release-key",body); assertEquals("RELEASED",released.task().status());
        assertEquals(released,fulfillment.release(courier,order,"release-key",body));
        failure("ORDER_STATE_CONFLICT",()->proof(courier,order,task,FulfillmentModels.Kind.PICKUP));
        failure("ORDER_STATE_CONFLICT",()->location(courier,order,task,FulfillmentModels.EventType.ARRIVAL,"late"));
        String other=nextCourier(); fulfillment.accept(other,order,key(),new FulfillmentModels.Version(v(order)));
        failure("ORDER_STATE_CONFLICT",()->proof(courier,order,task,FulfillmentModels.Kind.SPECIFICATION));
        accept(paid().order().id()); accept(paid().order().id());
    }
    @Test void pickupPhotoPreventsSelfReleaseEvenBeforePickup() {
        String order=paid().order().id(),task=accept(order); proof(courier,order,task,FulfillmentModels.Kind.PICKUP);
        failure("ORDER_STATE_CONFLICT",()->fulfillment.release(courier,order,key(),new FulfillmentModels.Release(v(order),task,"矛盾情形")));
        assertEquals("ACCEPTED",orders.get(user,order).status());
    }
    @Test void cancellationEndsActiveAndOfferedTasksInSameTransaction() {
        var c=paid(); String task=accept(c.order().id()); cancel(c);
        assertEquals("CANCELLED",db.queryForObject("SELECT status FROM runrun_v2.fulfillment_tasks WHERE id=?",String.class,Long.valueOf(task)));
        failure("ORDER_STATE_CONFLICT",()->location(courier,c.order().id(),task,FulfillmentModels.EventType.ARRIVAL,"after-cancel"));
        accept(paid().order().id()); accept(paid().order().id());
    }
    @Test void deliveredReleasesCapacityImmediatelyAndCompletesAtTenMinutes() {
        String a=paid().order().id(),ta=picked(a),b=paid().order().id(),c=paid().order().id(); accept(b); accept(c);
        failure("COURIER_CAPACITY_EXCEEDED",()->accept(paid().order().id()));
        var done=delivered(a,ta); assertEquals("DELIVERED",done.task().status()); assertEquals(done.order().deliveredAt().plusMinutes(10),done.confirmDeadline());
        accept(paid().order().id()); now("2026-10-27T01:09:59Z"); assertEquals(0,fulfillment.autoComplete());
        now("2026-10-27T01:10:00Z"); assertEquals(1,fulfillment.autoComplete()); assertEquals(0,fulfillment.autoComplete());
        assertEquals("COMPLETED",orders.get(user,a).status());
        failure("ORDER_STATE_CONFLICT",()->proof(courier,a,ta,FulfillmentModels.Kind.SPECIFICATION));
    }
    @Test void deliveredRiderOnlyHasLimitedSpecificationRightsAndUploadDoesNotBlockCompletion() {
        String order=paid().order().id(),task=picked(order); var done=delivered(order,task);
        var p=proof(courier,order,task,FulfillmentModels.Kind.SPECIFICATION);
        new TransactionTemplate(transactions).executeWithoutResult(s->fulfillment.authorizeSpecification(courier,order,task,v(order),p.id()));
        failure("ORDER_STATE_CONFLICT",()->proof(courier,order,task,FulfillmentModels.Kind.PICKUP));
        failure("ORDER_STATE_CONFLICT",()->proof(courier,order,task,FulfillmentModels.Kind.DELIVERY));
        failure("ORDER_STATE_CONFLICT",()->location(courier,order,task,FulfillmentModels.EventType.DELIVERY,"after-delivery"));
        now("2026-10-27T01:10:00Z"); assertEquals(1,fulfillment.autoComplete()); assertEquals(done.order().deliveredAt(),db.queryForObject("SELECT delivered_at FROM runrun_v2.v2_orders WHERE id=?",OffsetDateTime.class,Long.valueOf(order)));
    }
    @Test void adjustmentAndCaseBlocksInvalidateWindowThenRestartFullTenMinutes() {
        String order=paid().order().id(),task=picked(order); var done=delivered(order,task);
        now("2026-10-27T01:05:00Z");
        new TransactionTemplate(transactions).executeWithoutResult(s->{ fulfillment.blockCompletion(order,"ADJUSTMENT","a1"); fulfillment.blockCompletion(order,"AFTER_SALES","c1"); });
        failure("ADJUSTMENT_UNRESOLVED",()->fulfillment.confirm(user,order,key(),new FulfillmentModels.Version(v(order))));
        assertEquals(0,fulfillment.autoComplete());
        new TransactionTemplate(transactions).executeWithoutResult(s->fulfillment.unblockCompletion(order,"ADJUSTMENT","a1"));
        failure("AFTER_SALES_UNRESOLVED",()->fulfillment.confirm(user,order,key(),new FulfillmentModels.Version(v(order))));
        now("2026-10-27T01:25:00Z"); new TransactionTemplate(transactions).executeWithoutResult(s->fulfillment.unblockCompletion(order,"AFTER_SALES","c1"));
        assertEquals(OffsetDateTime.parse("2026-10-27T01:35:00Z"),db.queryForObject("SELECT confirm_deadline FROM runrun_v2.v2_orders WHERE id=?",OffsetDateTime.class,Long.valueOf(order)));
        assertEquals(done.order().deliveredAt(),db.queryForObject("SELECT delivered_at FROM runrun_v2.v2_orders WHERE id=?",OffsetDateTime.class,Long.valueOf(order)));
        assertEquals(0,fulfillment.autoComplete());
    }
    @Test void supplementBlocksWindowButNeverCapacity() {
        var c=paid(); String order=c.order().id(),task=picked(order); approve(c,"101",100);
        var done=delivered(order,task); assertNull(done.confirmDeadline()); assertEquals("PENDING_SUPPLEMENT",done.order().displayStatus()); assertEquals("DELIVERED",done.task().status());
        now("2026-10-27T02:00:00Z"); assertEquals(0,fulfillment.autoComplete()); accept(paid().order().id()); accept(paid().order().id());
    }
    @Test void inviteDeclineAcceptCapacityAndSupersession() {
        String order=paid().order().id(),old=picked(order); delivered(order,old);
        db.update("UPDATE runrun_v2.v2_orders SET status='EXCEPTION',version=version+1 WHERE id=?",Long.valueOf(order));
        String courierId=db.queryForObject("SELECT id::text FROM runrun_v2.app_users WHERE role='COURIER'",String.class);
        var offered=new TransactionTemplate(transactions).execute(s->fulfillment.invite(admin,order,courierId,v(order),"合成重新履约决定",key()));
        assertEquals("OFFERED",offered.status()); assertEquals(0L,db.queryForObject("SELECT count(*) FROM runrun_v2.fulfillment_tasks WHERE status='ACTIVE'",Long.class));
        failure("ORDER_STATE_CONFLICT",()->proof(courier,order,old,FulfillmentModels.Kind.SPECIFICATION));
        var accepted=fulfillment.respond(courier,offered.id(),key(),new FulfillmentModels.Respond(offered.version(),FulfillmentModels.Decision.ACCEPT));
        assertEquals("ACTIVE",accepted.task().status()); assertNotEquals(old,accepted.task().id());
        failure("ORDER_STATE_CONFLICT",()->proof(courier,order,old,FulfillmentModels.Kind.PICKUP));
    }
    @Test void confirmationAndNewAdjustmentSerializeToOneOutcome() throws Exception {
        String order=paid().order().id(),task=picked(order); delivered(order,task); var p=proof(courier,order,task,FulfillmentModels.Kind.SPECIFICATION); long version=v(order);
        race(()->{ try { return fulfillment.confirm(user,order,"confirm-race",new FulfillmentModels.Version(version)); } catch(IdentityFailure ex) { assertEquals("ORDER_STATE_CONFLICT",ex.code); return null; } },
             ()->{ try { return new TransactionTemplate(transactions).execute(s->{fulfillment.authorizeSpecification(courier,order,task,version,p.id()); fulfillment.blockCompletion(order,"ADJUSTMENT","race-adjustment"); return true;}); } catch(IdentityFailure ex) { assertEquals("ORDER_STATE_CONFLICT",ex.code); return null; } });
        String status=orders.get(user,order).status();
        assertTrue(status.equals("COMPLETED") || status.equals("DELIVERED_PENDING_CONFIRMATION"));
        assertEquals(status.equals("COMPLETED")?0:1,count("fulfillment_completion_blocks"));
    }
    @Test void unapprovedAndOtherRiderCannotWriteAndHttpRejectsReceiptCode() throws Exception {
        String order=paid().order().id(),task=accept(order); String other=nextCourier();
        failure("RESOURCE_NOT_FOUND",()->proof(other,order,task,FulfillmentModels.Kind.PICKUP));
        db.update("UPDATE runrun_v2.courier_certifications SET eligible=false,version=version+1 WHERE courier_id=(SELECT id FROM runrun_v2.app_users WHERE phone='13800000004')");
        failure("COURIER_NOT_APPROVED",()->proof(courier,order,task,FulfillmentModels.Kind.PICKUP));
        http.perform(post("/api/v2/orders/"+order+"/deliver").header("Authorization","Bearer "+other).header("Idempotency-Key","bad-code").contentType("application/json").content("{\"expectedVersion\":0,\"receiptCode\":\"1234\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
    @Test void failedEvidenceVerificationAndEventInsertRollBackEverything() {
        String order=paid().order().id(),task=accept(order); long version=v(order);
        when(evidence.verify(any(),anyString(),any())).thenThrow(new IdentityFailure(409,"PROOF_NOT_READY"));
        failure("PROOF_NOT_READY",()->proof(courier,order,task,FulfillmentModels.Kind.PICKUP)); assertEquals(version,v(order)); assertEquals(0,count("v2_order_proofs"));
        db.execute("ALTER TABLE runrun_v2.v2_order_events ADD CONSTRAINT synthetic_m04_failure CHECK(event_type<>'FULFILLMENT_LOCATION_RECORDED')");
        try { assertThrows(org.springframework.dao.DataAccessException.class,()->location(courier,order,task,FulfillmentModels.EventType.ARRIVAL,"rollback-gps")); }
        finally { db.execute("ALTER TABLE runrun_v2.v2_order_events DROP CONSTRAINT synthetic_m04_failure"); }
        assertEquals(version,v(order)); assertEquals(0,count("location_events"));
    }

    @Test void taskPaginationIsBoundToRiderAndFiltersAndAvailableWithoutEligibility() {
        String a=paid().order().id(),b=paid().order().id(); accept(a); accept(b);
        var page=queries.tasks(courier,null,1,null); assertEquals(1,page.items().size()); assertNotNull(page.nextCursor());
        assertEquals(1,queries.tasks(courier,page.nextCursor(),1,null).items().size());
        String second=nextCourier(); failure("VALIDATION_ERROR",()->queries.tasks(second,page.nextCursor(),1,null));
        failure("VALIDATION_ERROR",()->queries.tasks(courier,page.nextCursor(),1,"ACTIVE"));
        assertEquals(0,queries.tasks(second,null,100,null).items().size());
        db.update("UPDATE runrun_v2.courier_certifications SET eligible=false,version=version+1 WHERE courier_id=(SELECT id FROM runrun_v2.app_users WHERE phone='13800000004')");
        var readOnly=queries.tasks(courier,null,100,null); assertEquals(2,readOnly.items().size()); assertTrue(readOnly.items().stream().allMatch(t->t.allowedActions().isEmpty()));
    }
    @Test void poolExcludesUnpaidOrdersAndHasIdentityBoundCursor() {
        create(); paid(); paid(); var page=queries.pool(courier,null,1); assertEquals(1,page.items().size()); assertNotNull(page.nextCursor());
        assertEquals(1,queries.pool(courier,page.nextCursor(),1).items().size());
        String second=nextCourier(); failure("VALIDATION_ERROR",()->queries.pool(second,page.nextCursor(),1));
        failure("VALIDATION_ERROR",()->queries.pool(courier,"forged",1));
    }
    @Test void handedOverAndExceptionTasksCannotUsePreviouslyIssuedTickets() {
        String order=paid().order().id(),task=accept(order);
        var ticket=fulfillment.upload(courier,order,key(),new FulfillmentModels.Upload(v(order),FulfillmentModels.Kind.PICKUP,"image/jpeg",100L));
        db.update("UPDATE runrun_v2.fulfillment_tasks SET status='HANDED_OVER',ended_at=now(),version=version+1 WHERE id=?",Long.valueOf(task));
        failure("ORDER_STATE_CONFLICT",()->fulfillment.proof(courier,order,key(),new FulfillmentModels.RegisterProof(v(order),FulfillmentModels.Kind.PICKUP,task,ticket.uploadTicket())));
        verify(evidence,never()).verify(any(),eq(ticket.uploadTicket()),any());
    }
    @Test void invitationDeclineIsReadOnlyAndEligibilityDoesNotPreventDecline() {
        String order=paid().order().id();
        db.update("UPDATE runrun_v2.v2_orders SET status='EXCEPTION',version=version+1 WHERE id=?",Long.valueOf(order));
        String courierId=db.queryForObject("SELECT id::text FROM runrun_v2.app_users WHERE role='COURIER'",String.class);
        var offered=new TransactionTemplate(transactions).execute(s->fulfillment.invite(admin,order,courierId,v(order),"合成邀请",key()));
        db.update("UPDATE runrun_v2.courier_certifications SET eligible=false,version=version+1 WHERE courier_id=?",Long.valueOf(courierId));
        failure("COURIER_NOT_APPROVED",()->fulfillment.respond(courier,offered.id(),key(),new FulfillmentModels.Respond(offered.version(),FulfillmentModels.Decision.ACCEPT)));
        assertEquals("RELEASED",fulfillment.respond(courier,offered.id(),key(),new FulfillmentModels.Respond(offered.version(),FulfillmentModels.Decision.DECLINE)).task().status());
    }
    @Test void concurrentDuplicatePickupAndDeliveryOnlyAdvanceOnce() throws Exception {
        String order=paid().order().id(),task=accept(order); var p=proof(courier,order,task,FulfillmentModels.Kind.PICKUP);
        var pickup=new FulfillmentModels.Pickup(v(order),task,p.id());
        race(()->fulfillment.pickup(courier,order,"concurrent-pickup",pickup),()->fulfillment.pickup(courier,order,"concurrent-pickup",pickup));
        var d=proof(courier,order,task,FulfillmentModels.Kind.DELIVERY); var gps=location(courier,order,task,FulfillmentModels.EventType.DELIVERY,"delivery-race");
        var delivery=new FulfillmentModels.Deliver(v(order),task,d.id(),gps.eventId());
        race(()->fulfillment.deliver(courier,order,"concurrent-delivery",delivery),()->fulfillment.deliver(courier,order,"concurrent-delivery",delivery));
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM runrun_v2.v2_order_events WHERE event_type='FULFILLMENT_PICKED_UP'",Long.class));
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM runrun_v2.v2_order_events WHERE event_type='FULFILLMENT_DELIVERED'",Long.class));
    }
    @Test void automaticCompletionAndAdjustmentAreSerialized() throws Exception {
        String order=paid().order().id(),task=picked(order); delivered(order,task); var p=proof(courier,order,task,FulfillmentModels.Kind.SPECIFICATION); long version=v(order);
        now("2026-10-27T01:10:00Z");
        race(fulfillment::autoComplete,()->{
            try { return new TransactionTemplate(transactions).execute(s->{fulfillment.authorizeSpecification(courier,order,task,version,p.id()); fulfillment.blockCompletion(order,"ADJUSTMENT","auto-race"); return true;}); }
            catch(IdentityFailure ex) { assertEquals("ORDER_STATE_CONFLICT",ex.code); return null; }
        });
        String status=orders.get(user,order).status(); assertEquals(status.equals("COMPLETED")?0:1,count("fulfillment_completion_blocks"));
    }

    @Test void actualHttpChainReturnsServerWindowAndDefaultEvidenceAdapterFailsClosed() throws Exception {
        String order=paid().order().id(),task=picked(order); var done=delivered(order,task);
        assertEquals("PENDING_CONFIRMATION",done.order().displayStatus());
        http.perform(get("/api/v2/orders/"+order).header("Authorization","Bearer "+user))
            .andExpect(status().isOk()).andExpect(jsonPath("$.deliveredAt").isString()).andExpect(jsonPath("$.confirmStartedAt").isString()).andExpect(jsonPath("$.confirmDeadline").isString());
        http.perform(get("/api/v2/courier/tasks").header("Authorization","Bearer "+courier))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.items[0].taskStatus").value("DELIVERED"));
        var pending=new PendingFulfillmentEvidence();
        var binding=new FulfillmentEvidence.Binding(order,task,"synthetic",FulfillmentModels.Kind.SPECIFICATION);
        failure("PROOF_NOT_READY",()->pending.issue(binding,"image/jpeg",10,done.order().deliveredAt()));
        failure("PROOF_NOT_READY",()->pending.verify(binding,"client-claim",done.order().deliveredAt()));
    }

    @Test void releaseAndLocationRetrySerializeWithoutRestoringOldTask() throws Exception {
        String order=paid().order().id(),task=accept(order); long version=v(order);
        var release=new FulfillmentModels.Release(version,task,"合成并发释放");
        var gps=new FulfillmentModels.Location(version,task,FulfillmentModels.EventType.ARRIVAL,new java.math.BigDecimal("30"),new java.math.BigDecimal("120"),"GCJ02","release-race-gps");
        race(()->{ try { return fulfillment.release(courier,order,"release-race",release); } catch(IdentityFailure ex) { assertEquals("ORDER_STATE_CONFLICT",ex.code); return null; } },
             ()->{ try { return fulfillment.location(courier,order,"location-race",gps); } catch(IdentityFailure ex) { assertEquals("ORDER_STATE_CONFLICT",ex.code); return null; } });
        if(orders.get(user,order).status().equals("ACCEPTED")) fulfillment.release(courier,order,"release-after-gps",new FulfillmentModels.Release(v(order),task,"合成刷新后释放"));
        long before=count("location_events"); failure("ORDER_STATE_CONFLICT",()->location(courier,order,task,FulfillmentModels.EventType.ARRIVAL,"new-after-release"));
        assertEquals(before,count("location_events")); assertEquals("PENDING_ASSIGNMENT",orders.get(user,order).status());
    }
    @Test void cancellationAndPickupSerializeWithoutLosingPhysicalCustody() throws Exception {
        var c=paid(); String order=c.order().id(),task=accept(order); var p=proof(courier,order,task,FulfillmentModels.Kind.PICKUP); long version=v(order);
        race(()->{ try { return payments.cancel(user,order,"cancel-race",new CancelRequest(version,"合成并发取消")); } catch(IdentityFailure ex) { assertEquals("ORDER_STATE_CONFLICT",ex.code); return null; } },
             ()->{ try { return fulfillment.pickup(courier,order,"pickup-cancel-race",new FulfillmentModels.Pickup(version,task,p.id())); } catch(IdentityFailure ex) { assertEquals("ORDER_STATE_CONFLICT",ex.code); return null; } });
        var status=orders.get(user,order).status(); assertTrue(Set.of("CANCELLED","PICKED_UP").contains(status));
        if(status.equals("CANCELLED")) assertEquals("CANCELLED",db.queryForObject("SELECT status FROM runrun_v2.fulfillment_tasks WHERE id=?",String.class,Long.valueOf(task)));
        else failure("ORDER_STATE_CONFLICT",()->payments.cancel(user,order,key(),new CancelRequest(v(order),"已取件不能普通取消")));
    }

    @Test void arrivalOutsideRadiusOrWithoutConfiguredPointIsRetainedAsRejected() {
        db.update("UPDATE runrun_v2.service_points SET latitude=NULL,longitude=NULL");
        String missing=paid().order().id(),missingTask=accept(missing);
        var unconfigured=location(courier,missing,missingTask,FulfillmentModels.EventType.ARRIVAL,"missing-point"); assertFalse(unconfigured.accepted());
        assertEquals("SERVICE_LOCATION_UNCONFIGURED",db.queryForObject("SELECT decision_reason FROM runrun_v2.location_events WHERE id=?",String.class,Long.valueOf(unconfigured.eventId())));
        db.update("UPDATE runrun_v2.service_points SET latitude=30,longitude=120");
        String order=paid().order().id(),task=accept(order);
        var far=fulfillment.location(courier,order,key(),new FulfillmentModels.Location(v(order),task,FulfillmentModels.EventType.ARRIVAL,new java.math.BigDecimal("30.001"),new java.math.BigDecimal("120"),"GCJ02","far-point"));
        assertFalse(far.accepted()); assertEquals(2,count("location_events"));
    }
}
