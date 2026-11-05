package com.zhangxuan.runrunv2;

import static com.zhangxuan.runrunv2.OrderModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(classes=IdentityApplication.class,properties={"spring.config.name=application-v2-test","rrc.identity.environment=test",
        "rrc.identity.app-id=synthetic-app","rrc.identity.app-secret=synthetic-secret","spring.flyway.enabled=false"})
@AutoConfigureMockMvc(print=org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
// Close scheduled workers and JDBC pools with the class-scoped database fixture.
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class OrderIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) throws Exception {
        try(var conn=DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()); var sql=conn.createStatement()) {
            for(String name:List.of("rrc_0001__create_v2_schema.sql","rrc_0002__create_identity_foundation.sql","rrc_0003__identity_runtime.sql","rrc_0100__orders_and_service_config.sql"))
                sql.execute(Files.readString(Path.of("../../server/db/migrations/up",name)));
        }
        registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",POSTGRES::getUsername);
        registry.add("spring.datasource.password",POSTGRES::getPassword);
        registry.add("rrc.identity.encryption-key",()->Base64.getEncoder().encodeToString(new byte[32]));
        registry.add("rrc.identity.hash-key",()->Base64.getEncoder().encodeToString("synthetic-different-hash-key-000001".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    @Autowired JdbcTemplate db;
    @Autowired IdentitySecrets secrets;
    @Autowired DeploymentIdentityService deployment;
    @Autowired ServiceConfigService config;
    @Autowired OrderService orders;
    @Autowired OrderStore store;
    @Autowired OrderJson json;
    @Autowired MockMvc http;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean Clock clock;
    @MockitoBean WechatGateway wechat;
    // Explicit M03 substitute: no production payment table or payment processing is implemented here.
    @MockitoBean OriginalPaymentWriter payments;
    String user,other,admin,courier;
    String adminId;
    @BeforeEach void setup() {
        db.execute("TRUNCATE runrun_v2.app_users,runrun_v2.service_points,runrun_v2.delivery_zones,runrun_v2.platform_settings RESTART IDENTITY CASCADE");
        user=account("13800000001","USER"); other=account("13800000002","USER");
        admin=account("13800000003","ADMIN"); courier=account("13800000004","COURIER");
        adminId=db.queryForObject("SELECT id::text FROM runrun_v2.app_users WHERE role='ADMIN'",String.class);
        db.update("INSERT INTO runrun_v2.platform_settings(id,new_order_enabled,review_owner_id) VALUES(1,true,?)",Long.valueOf(adminId));
        db.update("INSERT INTO runrun_v2.delivery_zones(code,name,package_surcharge_cents) VALUES('STANDARD','其他区域',0),('MEIYUAN','梅园',200)");
        db.update("INSERT INTO runrun_v2.service_points(code,name,latitude,longitude) VALUES('TEST_POINT','合成测试点',30.1234567,120.1234567)");
        now("2026-10-27T01:00:00Z");
        when(payments.available()).thenReturn(true);
        when(payments.create(anyString(),anyLong(),any())).thenAnswer(i->new OriginalPayment(i.getArgument(0),i.getArgument(0),"ORIGINAL",i.getArgument(1),"UNPAID",i.getArgument(2),0));
    }
    private String account(String phone,String role) {
        long id=db.queryForObject("INSERT INTO runrun_v2.app_users(phone,role,v2_admin_enabled) VALUES(?,?,?) RETURNING id",Long.class,phone,role,role.equals("ADMIN"));
        String token=secrets.token();
        db.update("INSERT INTO runrun_v2.user_sessions(user_id,token_hash,expires_at) VALUES(?,?,now()+interval '1 day')",id,secrets.hash("session",token));
        return token;
    }
    private void now(String value) { when(clock.instant()).thenReturn(Instant.parse(value)); }
    private CreateRequest request(ServiceType service,String zone,long version) { return new CreateRequest(service,zone,"TEST_POINT","合成目的地梅园字样",version); }
    private CreateRequest request() { return request(ServiceType.PACKAGE_SMALL,"MEIYUAN",0); }
    private ConfigChange change(long version,Boolean enabled,List<ZoneChange> zones,List<PointChange> points) {
        return new ConfigChange(version,version,enabled,null,null,zones,points,"合成配置测试原因");
    }
    private void failure(String code,org.junit.jupiter.api.function.Executable action) { assertEquals(code,assertThrows(IdentityFailure.class,action).code); }
    private long count(String table) { return db.queryForObject("SELECT count(*) FROM runrun_v2."+table,Long.class); }
    @Test void allNineServicesArePricedInCentsFromStructuredZones() {
        long[] prices={200,300,500,100,200,300,400,500,800};
        for(int i=0;i<ServiceType.values().length;i++) {
            ServiceType service=ServiceType.values()[i];
            for(String zone:List.of("STANDARD","MEIYUAN")) {
                var quote=config.quote(user,request(service,zone,0).quote());
                assertEquals(prices[i]+(service.zoneSurcharge&&zone.equals("MEIYUAN")?200:0),quote.pricingSnapshot().totalCents());
                var created=orders.create(user,service.name()+zone,request(service,zone,0));
                assertEquals(quote.pricingSnapshot(),created.order().pricingSnapshot());
                assertEquals(quote.pricingSnapshot().totalCents(),created.originalPayment().amountCents());
            }
        }
        assertEquals(18,count("v2_orders")); assertEquals(18,count("v2_order_events"));
    }
    @Test void creationStoresSnapshotsAndImmutableDeadlineAndReturnsStringIds() throws Exception {
        http.perform(post("/api/v2/orders").header("Authorization","Bearer "+user).header("Idempotency-Key","create-http")
                .contentType("application/json").content(json.write(request())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.order.id").isString())
                .andExpect(jsonPath("$.order.orderFlowVersion").value(2)).andExpect(jsonPath("$.order.version").value(0))
                .andExpect(jsonPath("$.order.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.order.originalFeeCents").value(300)).andExpect(jsonPath("$.originalPayment.amountCents").value(300))
                .andExpect(jsonPath("$.order.addressSnapshot.coordinateSystem").value("GCJ02"));
        Order order=orders.get(user,"1");
        assertEquals(OffsetDateTime.parse("2026-10-27T01:30:00Z"),order.pendingPaymentDeadline());
        assertEquals(30,((Number)order.policySnapshot().get("pendingPaymentMinutes")).intValue());
        assertEquals(10,((Number)order.policySnapshot().get("deliveryConfirmMinutes")).intValue());
        assertEquals(70,((Number)order.policySnapshot().get("courierSharePercent")).intValue());
        config.patch(admin,"new-price",change(0,null,List.of(new ZoneChange("MEIYUAN","修改后区域",700L,true)),null));
        assertEquals(order,orders.get(user,order.id()));
        failure("CONFIG_VERSION_CONFLICT",()->orders.create(user,"stale",request()));
        failure("RESOURCE_NOT_FOUND",()->orders.get(other,order.id()));
        http.perform(get("/api/v2/orders/1").header("Authorization","Bearer "+courier)).andExpect(status().isForbidden());
    }
    @Test void clientAmountsSnapshotsVersionsAndUnknownFieldsAreRejected() throws Exception {
        for(String extra:List.of("\"originalFeeCents\":1","\"totalCents\":1","\"pricingSnapshot\":{}","\"orderFlowVersion\":1","\"version\":9","\"expectedVersion\":0")) {
            String body=json.write(request()); body=body.substring(0,body.length()-1)+","+extra+"}";
            http.perform(post("/api/v2/orders").header("Authorization","Bearer "+user).header("Idempotency-Key","tampered")
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        http.perform(post("/api/v2/quotes").header("Authorization","Bearer "+user).contentType("application/json")
                .content("{\"serviceType\":\"PACKAGE_SMALL\",\"zoneCode\":\"MEIYUAN\",\"servicePointCode\":\"TEST_POINT\",\"destination\":\"合成地址\",\"amountCents\":1}"))
                .andExpect(status().isBadRequest());
        http.perform(post("/api/v2/orders").header("Authorization","Bearer "+user).header("Idempotency-Key","fractional-version")
                .contentType("application/json").content(json.write(request()).replace("\"configVersion\":0","\"configVersion\":0.9")))
                .andExpect(status().isBadRequest());
        String fractionalConfig="{\"configVersion\":0,\"expectedVersion\":0,\"reason\":\"合成配置\",\"zones\":[{\"code\":\"MEIYUAN\",\"name\":\"梅园\",\"packageSurchargeCents\":200.1,\"enabled\":true}]}";
        http.perform(patch("/api/v2/admin/service-config").header("Authorization","Bearer "+admin).header("Idempotency-Key","fractional-cents")
                .contentType("application/json").content(fractionalConfig)).andExpect(status().isBadRequest());
        assertEquals(0,count("v2_orders")); assertEquals(0,count("idempotency_requests"));
        http.perform(post("/api/v1/orders").contentType("application/json").content("{}")).andExpect(status().isNotFound());
    }
    @Test void beijingBusinessBoundariesAreIndependentOfHostTimezone() {
        for(ServiceType service:List.of(ServiceType.LOBBY,ServiceType.EXPRESS)) {
            now("2026-10-27T00:59:59.999999999Z");
            failure("OUTSIDE_BUSINESS_HOURS",()->orders.create(user,"early"+service,request(service,"STANDARD",0)));
            now("2026-10-27T01:00:00Z");
            orders.create(user,"open"+service,request(service,"STANDARD",0));
            now("2026-10-27T14:59:59.999999999Z");
            Created last=orders.create(user,"last"+service,request(service,"STANDARD",0));
            assertEquals(last.order().pendingPaymentDeadline(),last.originalPayment().deadline());
            now("2026-10-27T15:00:00Z");
            failure("OUTSIDE_BUSINESS_HOURS",()->orders.create(user,"close"+service,request(service,"STANDARD",0)));
        }
        assertEquals(4,count("v2_orders")); assertEquals(4,count("idempotency_requests"));
    }
    @Test void replayAfterCloseAndConfigChangeReturnsOriginalWithoutIncrementOrRenewal() {
        Created first=orders.create(user,"same",request());
        now("2026-10-27T15:00:00Z");
        config.patch(admin,"disable",change(0,false,null,null));
        assertEquals(first,orders.create(user,"same",request()));
        assertEquals(0,orders.get(user,first.order().id()).version());
        failure("IDEMPOTENCY_KEY_CONFLICT",()->orders.create(user,"same",request(ServiceType.DOOR,"MEIYUAN",0)));
        assertEquals(1,count("v2_orders")); assertEquals(1,count("v2_order_events"));
        verify(payments,times(1)).create(anyString(),anyLong(),any());
    }
    @Test void concurrentIdenticalRequestsCreateOneOrderAndConflictingPayloadLoses() throws Exception {
        List<Object> results=race(()->orders.create(user,"race",request()),()->orders.create(user,"race",request()));
        assertEquals(results.get(0),results.get(1)); assertEquals(1,count("v2_orders"));
        results=race(()->orders.create(user,"race-diff",request()),()->orders.create(user,"race-diff",request(ServiceType.DOOR,"STANDARD",0)));
        assertEquals(1,results.stream().filter(x->x instanceof Created).count());
        assertEquals(1,results.stream().filter(x->x.equals("IDEMPOTENCY_KEY_CONFLICT")).count());
        assertEquals(2,count("v2_orders")); assertEquals(2,count("v2_order_events"));
    }
    @Test void disabledLocationsInvalidCoordinatesAndStaleConfigAreRejected() {
        config.patch(admin,"disable-zone",change(0,null,List.of(new ZoneChange("MEIYUAN","梅园",200L,false)),null));
        failure("VALIDATION_ERROR",()->orders.create(user,"disabled-zone",request(ServiceType.PACKAGE_SMALL,"MEIYUAN",1)));
        failure("VALIDATION_ERROR",()->config.quote(user,new QuoteRequest(ServiceType.LOBBY,"UNKNOWN","TEST_POINT","地址")));
        failure("VALIDATION_ERROR",()->config.patch(admin,"bad-coordinates",change(1,null,null,List.of(new PointChange("BAD","测试",BigDecimal.valueOf(91),BigDecimal.ONE,"GCJ02",true)))));
        failure("VALIDATION_ERROR",()->config.patch(admin,"unpaired",change(1,null,null,List.of(new PointChange("BAD","测试",null,BigDecimal.ONE,"GCJ02",true)))));
        config.patch(admin,"no-coordinates",change(1,null,null,List.of(new PointChange("NULL_POINT","待核实测试点",null,null,"GCJ02",true))));
        assertNull(config.config(user).servicePoints().stream().filter(p->p.code().equals("NULL_POINT")).findFirst().orElseThrow().latitude());
        config.patch(admin,"disable-point",change(2,null,null,List.of(new PointChange("TEST_POINT","测试点",null,null,"GCJ02",false))));
        failure("VALIDATION_ERROR",()->orders.create(user,"disabled-point",request(ServiceType.LOBBY,"STANDARD",3)));
        assertEquals(0,count("v2_orders"));
    }
    @Test void configPatchHasVersionConflictIdempotencyAuditAndOwnerValidation() throws Exception {
        ConfigChange change=change(0,null,List.of(new ZoneChange("MEIYUAN","梅园",300L,true)),null);
        Config first=config.patch(admin,"config-once",change);
        assertEquals(first,config.patch(admin,"config-once",change));
        assertEquals(1,count("admin_audit_logs")); assertEquals(1,first.configVersion());
        failure("CONFIG_VERSION_CONFLICT",()->config.patch(admin,"stale",change));
        failure("IDEMPOTENCY_KEY_CONFLICT",()->config.patch(admin,"config-once",change(1,false,null,null)));
        failure("REVIEW_OWNER_REQUIRED",()->config.patch(admin,"bad-owner",new ConfigChange(1L,1L,true,"1",null,null,null,"无效负责人")));
        failure("VALIDATION_ERROR",()->config.patch(admin,"bad-hours",new ConfigChange(1L,1L,null,null,new Schedule("UTC","09:00","23:00"),null,null,"错误时区")));
        failure("ORDER_STATE_CONFLICT",()->config.patch(admin,"wrong-version",new ConfigChange(1L,0L,null,null,null,null,null,"过期并发版本")));
        http.perform(patch("/api/v2/admin/service-config").header("Authorization","Bearer "+user).header("Idempotency-Key","forbidden")
                .contentType("application/json").content(json.write(change))).andExpect(status().isForbidden());
    }
    @Test void concurrentConfigUpdatesRejectStaleWriter() throws Exception {
        String admin2=account("13800000005","ADMIN");
        List<Object> results=race(()->config.patch(admin,"one",change(0,false,null,null)),()->config.patch(admin2,"two",change(0,false,null,null)));
        assertEquals(1,results.stream().filter(x->x instanceof Config).count());
        assertTrue(results.contains("CONFIG_VERSION_CONFLICT")); assertEquals(1,count("admin_audit_logs"));
    }
    @Test void reviewOwnerMustBeReplacedBeforeControlledDisable() {
        long deployer=Long.parseLong(deployment.bootstrap("合成受控身份","CREATE DEPLOYMENT IDENTITY","bootstrap"));
        long owner=Long.parseLong(adminId);
        failure("REVIEW_OWNER_REQUIRED",()->deployment.changeAccess(deployer,owner,false,0,"合成停用","DISABLE "+owner,"disable-owner"));
        account("13800000005","ADMIN");
        String next=db.queryForObject("SELECT id::text FROM runrun_v2.app_users WHERE phone='13800000005'",String.class);
        config.patch(admin,"replace-owner",new ConfigChange(0L,0L,null,next,null,null,null,"先更换值班负责人"));
        assertFalse(deployment.changeAccess(deployer,owner,false,0,"合成停用","DISABLE "+owner,"disable-owner").enabled());
    }
    @Test void configChangeRacingCreationNeverMixesPriceAndVersion() throws Exception {
        List<Object> results=race(()->orders.create(user,"create-race",request()),
                ()->config.patch(admin,"config-race",change(0,null,List.of(new ZoneChange("MEIYUAN","新测试价格",700L,true)),null)));
        assertTrue(results.get(1) instanceof Config);
        if(results.get(0) instanceof Created created) {
            assertEquals(0,created.order().pricingSnapshot().configVersion());
            assertEquals(300,created.order().originalFeeCents());
            assertEquals("梅园",created.order().addressSnapshot().zoneName());
        } else { assertEquals("CONFIG_VERSION_CONFLICT",results.get(0)); assertEquals(0,count("v2_orders")); }
    }
    @Test void unavailableM03AndFailedPaymentLeaveNoPartialOrderOrIdempotency() {
        when(payments.available()).thenReturn(false);
        assertFalse(config.config(user).newOrderEnabled());
        failure("NEW_ORDER_DISABLED",()->orders.create(user,"no-M03",request()));
        when(payments.available()).thenReturn(true);
        when(payments.create(anyString(),anyLong(),any())).thenThrow(new IllegalStateException("synthetic payment failure"));
        assertThrows(IllegalStateException.class,()->orders.create(user,"failed-payment",request()));
        assertEquals(0,count("v2_orders")); assertEquals(0,count("v2_order_events")); assertEquals(0,count("idempotency_requests"));
    }
    @Test void concurrentOrderUpdatesUseSharedLockAndIncrementExactlyOnce() throws Exception {
        Order order=orders.create(user,"order",request()).order();
        Supplier<Object> update=()->new TransactionTemplate(transactions).execute(s->{
            store.lock(order.id(),0);
            db.update("UPDATE runrun_v2.v2_orders SET effective_fee_cents=effective_fee_cents+100,version=version+1,updated_at=now() WHERE id=?",Long.valueOf(order.id()));
            db.update("INSERT INTO runrun_v2.v2_order_events(order_id,actor_id,event_type,request_id,payload) VALUES(?,1,'SYNTHETIC_UPDATE','update','{}')",Long.valueOf(order.id()));
            return "updated";
        });
        List<Object> results=race(update,update);
        assertTrue(results.contains("updated")); assertTrue(results.contains("ORDER_STATE_CONFLICT"));
        Order latest=orders.get(user,order.id());
        assertEquals(1,latest.version()); assertEquals(2,latest.orderFlowVersion()); assertEquals(400,latest.effectiveFeeCents());
        assertEquals(2,count("v2_order_events"));
        assertThrows(IllegalStateException.class,()->store.lock(order.id(),1));
    }
    @Test void databaseRejectsFlowMutationWrongVersionAndSnapshotOverwrite() {
        String id=orders.create(user,"guard",request()).order().id();
        for(String mutation:List.of("order_flow_version=1,version=1","version=0","version=2","pricing_snapshot='{}',version=1","original_fee_cents=1,version=1","pending_payment_deadline=now(),version=1"))
            assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE runrun_v2.v2_orders SET "+mutation+" WHERE id=?",Long.valueOf(id)));
        assertEquals(0,orders.get(user,id).version());
        assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("INSERT INTO runrun_v2.v2_orders(order_no,user_id,service_type,zone_id,service_point_id,address_snapshot,pricing_snapshot,policy_snapshot,original_fee_cents,effective_fee_cents,pending_payment_deadline,version) SELECT 'bad',user_id,service_type,zone_id,service_point_id,address_snapshot,pricing_snapshot,policy_snapshot,original_fee_cents,effective_fee_cents,pending_payment_deadline,1 FROM runrun_v2.v2_orders WHERE id=?",Long.valueOf(id)));
    }
    private List<Object> race(Supplier<?> first,Supplier<?> second) throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2), start=new CountDownLatch(1);
        try {
            List<Future<Object>> futures=new ArrayList<>();
            for(Supplier<?> operation:List.of(first,second)) futures.add(pool.submit(()->{ ready.countDown(); start.await(); try{return operation.get();}catch(IdentityFailure e){return e.code;} }));
            assertTrue(ready.await(5,TimeUnit.SECONDS)); start.countDown();
            return List.of(futures.get(0).get(15,TimeUnit.SECONDS),futures.get(1).get(15,TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
}
