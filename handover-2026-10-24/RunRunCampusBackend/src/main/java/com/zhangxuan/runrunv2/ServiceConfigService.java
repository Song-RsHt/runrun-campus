package com.zhangxuan.runrunv2;

import static com.zhangxuan.runrunv2.OrderModels.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceConfigService {
    private final JdbcTemplate db;
    private final IdentityService identity;
    private final OrderIdempotency requests;
    private final OrderJson json;
    private final Clock clock;
    private final OriginalPaymentWriter payments;
    public ServiceConfigService(JdbcTemplate db, IdentityService identity, OrderIdempotency requests, OrderJson json, Clock clock, OriginalPaymentWriter payments) {
        this.db=db; this.identity=identity; this.requests=requests; this.json=json; this.clock=clock; this.payments=payments;
    }
    // Shared configuration lock protects both the quote version and all constituent rows.
    // Updates lock the singleton first and commit all zone/point changes with one version.
    Map<String,Object> settings(boolean update) {
        return db.queryForMap("SELECT * FROM runrun_v2.platform_settings WHERE id=1 FOR " + (update ? "UPDATE" : "SHARE"));
    }
    @Transactional
    public Config config(String token) {
        var actor=identity.authenticate(token);
        Map<String,Object> settings=settings(false);
        return view(settings, "ADMIN".equals(actor.role()) && actor.adminEnabled());
    }
    private Config view(Map<String,Object> settings, boolean includeDisabled) {
        List<com.zhangxuan.runrunv2.OrderModels.Service> services=Arrays.stream(ServiceType.values()).map(s -> new com.zhangxuan.runrunv2.OrderModels.Service(s.name(),s.name,s.baseCents,s.zoneSurcharge)).toList();
        String filter=includeDisabled ? "" : " WHERE enabled";
        var zones=db.query("SELECT * FROM runrun_v2.delivery_zones"+filter+" ORDER BY id", (r,n)->new Zone(r.getString("id"),r.getString("code"),r.getString("name"),r.getLong("package_surcharge_cents"),r.getBoolean("enabled"),r.getLong("config_version"),r.getLong("version")));
        var points=db.query("SELECT * FROM runrun_v2.service_points"+filter+" ORDER BY id", (r,n)->new ServicePoint(r.getString("id"),r.getString("code"),r.getString("name"),r.getBigDecimal("latitude"),r.getBigDecimal("longitude"),r.getString("coordinate_system"),r.getBoolean("enabled"),r.getLong("config_version"),r.getLong("version")));
        return new Config(services,zones,points,new Schedule("Asia/Shanghai","09:00","23:00"),Boolean.TRUE.equals(settings.get("new_order_enabled")) && payments.available(),
                includeDisabled && settings.get("review_owner_id")!=null ? settings.get("review_owner_id").toString() : null, number(settings,"config_version"),"1",number(settings,"version"));
    }
    @Transactional
    public Quote quote(String token, QuoteRequest request) {
        identity.requireRole(token,"USER",false);
        Map<String,Object> settings=settings(false);
        return new Quote(price(request,settings), number(settings,"config_version"));
    }
    Pricing price(QuoteRequest request, Map<String,Object> settings) {
        validate(request);
        Map<String,Object> zone=zone(request.zoneCode());
        point(request.servicePointCode());
        // 基线§4：区域加价只适用于包裹；目的地文字不能推断区域或覆盖服务端金额。
        long surcharge=request.serviceType().zoneSurcharge ? number(zone,"package_surcharge_cents") : 0;
        return new Pricing(request.serviceType(),request.serviceType().baseCents,request.zoneCode(),surcharge,
                Math.addExact(request.serviceType().baseCents,surcharge),number(settings,"config_version"));
    }
    static void validate(QuoteRequest request) {
        if(request==null || request.serviceType()==null) throw IdentityFailure.invalid();
        code(request.zoneCode()); code(request.servicePointCode()); IdentityService.text(request.destination(),500);
    }
    Map<String,Object> zone(String code) { return enabled("delivery_zones",code); }
    Map<String,Object> point(String code) { return enabled("service_points",code); }
    private Map<String,Object> enabled(String table,String code) {
        var rows=db.queryForList("SELECT * FROM runrun_v2."+table+" WHERE code=? AND enabled",code);
        if(rows.size()!=1) throw IdentityFailure.invalid();
        return rows.get(0);
    }
    void allowNewOrder(Map<String,Object> settings) {
        if(!Boolean.TRUE.equals(settings.get("new_order_enabled"))) throw new IdentityFailure(503,"NEW_ORDER_DISABLED");
        requireOwner(settings.get("review_owner_id"));
        LocalTime now=clock.instant().atZone(ZoneId.of(settings.get("business_timezone").toString())).toLocalTime();
        LocalTime open=((java.sql.Time)settings.get("business_open")).toLocalTime();
        LocalTime close=((java.sql.Time)settings.get("business_close")).toLocalTime();
        if(now.isBefore(open) || !now.isBefore(close)) throw new IdentityFailure(409,"OUTSIDE_BUSINESS_HOURS");
    }
    Map<String,Object> policy(Map<String,Object> settings) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("configVersion",number(settings,"config_version"));
        result.put("businessSchedule",new Schedule("Asia/Shanghai","09:00","23:00"));
        for(String key:List.of("pending_payment_minutes","delivery_confirm_minutes","arrival_radius_m","arrival_window_minutes","arrival_subsidy_cents","adjust_pay_timeout_minutes","adjustment_review_remind_minutes","adjustment_review_escalate_minutes","overdue_debt_closure_days","platform_share_percent")) {
            StringBuilder camel=new StringBuilder(); boolean upper=false;
            for(char c:key.toCharArray()) { if(c=='_') upper=true; else { camel.append(upper?Character.toUpperCase(c):c); upper=false; } }
            result.put(camel.toString(),number(settings,key));
        }
        result.put("courierSharePercent",100-number(settings,"platform_share_percent"));
        return result;
    }
    @Transactional
    public Config patch(String token,String key,ConfigChange change) {
        // 契约M47：更换负责人和停用资格共用锁，避免并发留下失效接收人。
        identity.lock("review-owner-policy");
        var actor=identity.authorizeForWrite(token,"ADMIN",false);
        if(change==null || change.configVersion()==null || change.configVersion()<0 || change.expectedVersion()==null || change.expectedVersion()<0) throw IdentityFailure.invalid();
        IdentityService.text(change.reason(),1000);
        Config replay=requests.replay(actor.id(),"service-config:patch",key,change,Config.class);
        if(replay!=null) return replay;
        Map<String,Object> settings=settings(true);
        long version=number(settings,"config_version");
        if(version!=change.configVersion()) throw new IdentityFailure(409,"CONFIG_VERSION_CONFLICT");
        if(number(settings,"version")!=change.expectedVersion()) throw new IdentityFailure(409,"ORDER_STATE_CONFLICT");
        if(change.businessSchedule()!=null && !new Schedule("Asia/Shanghai","09:00","23:00").equals(change.businessSchedule())) throw IdentityFailure.invalid();
        Object owner=change.reviewOwnerId()==null ? settings.get("review_owner_id") : id(change.reviewOwnerId());
        boolean enabled=change.newOrderEnabled()==null ? Boolean.TRUE.equals(settings.get("new_order_enabled")) : change.newOrderEnabled();
        if(owner!=null || enabled) requireOwner(owner);
        if(change.zones()!=null) {
            Set<String> codes=new HashSet<>();
            if(change.zones().size()>100) throw IdentityFailure.invalid();
            for(ZoneChange zone:change.zones()) {
                if(zone==null) throw IdentityFailure.invalid();
                code(zone.code()); IdentityService.text(zone.name(),100);
                if(!codes.add(zone.code()) || zone.enabled()==null || zone.packageSurchargeCents()==null || zone.packageSurchargeCents()<0 || zone.packageSurchargeCents()>Long.MAX_VALUE-800) throw IdentityFailure.invalid();
                db.update("INSERT INTO runrun_v2.delivery_zones(code,name,package_surcharge_cents,enabled,config_version) VALUES(?,?,?,?,?) ON CONFLICT(code) DO UPDATE SET name=excluded.name,package_surcharge_cents=excluded.package_surcharge_cents,enabled=excluded.enabled,config_version=excluded.config_version,version=delivery_zones.version+1,updated_at=now()",
                        zone.code(),zone.name(),zone.packageSurchargeCents(),zone.enabled(),version+1);
            }
        }
        if(change.servicePoints()!=null) {
            Set<String> codes=new HashSet<>();
            if(change.servicePoints().size()>100) throw IdentityFailure.invalid();
            for(PointChange point:change.servicePoints()) {
                if(point==null) throw IdentityFailure.invalid();
                code(point.code()); IdentityService.text(point.name(),100);
                if(!codes.add(point.code()) || point.enabled()==null || !"GCJ02".equals(point.coordinateSystem()) || (point.latitude()==null)!=(point.longitude()==null)) throw IdentityFailure.invalid();
                if(point.latitude()!=null && (point.latitude().scale()>7 || point.longitude().scale()>7 || point.latitude().abs().compareTo(java.math.BigDecimal.valueOf(90))>0 || point.longitude().abs().compareTo(java.math.BigDecimal.valueOf(180))>0)) throw IdentityFailure.invalid();
                db.update("INSERT INTO runrun_v2.service_points(code,name,latitude,longitude,coordinate_system,enabled,config_version) VALUES(?,?,?,?,?,?,?) ON CONFLICT(code) DO UPDATE SET name=excluded.name,latitude=excluded.latitude,longitude=excluded.longitude,coordinate_system=excluded.coordinate_system,enabled=excluded.enabled,config_version=excluded.config_version,version=service_points.version+1,updated_at=now()",
                        point.code(),point.name(),point.latitude(),point.longitude(),point.coordinateSystem(),point.enabled(),version+1);
            }
        }
        db.update("UPDATE runrun_v2.platform_settings SET new_order_enabled=?,review_owner_id=?,config_version=config_version+1,version=version+1,updated_at=now() WHERE id=1",enabled,owner);
        db.update("INSERT INTO runrun_v2.admin_audit_logs(administrator_id,action,target_type,target_id,request_id,reason,metadata) VALUES(?,'SERVICE_CONFIG_CHANGED','SERVICE_CONFIG',1,?,?,?::jsonb)",
                Long.valueOf(actor.id()),key,change.reason(),json.write(Map.of("oldConfigVersion",version,"newConfigVersion",version+1,"changes",change)));
        Config result=view(settings(true),true);
        requests.finish(actor.id(),"service-config:patch",key,"SERVICE_CONFIG","1",result);
        return result;
    }
    private void requireOwner(Object id) {
        if(id==null || !Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM runrun_v2.app_users WHERE id=? AND role='ADMIN' AND status='ACTIVE' AND v2_admin_enabled AND NOT controlled_identity)",Boolean.class,id))) throw new IdentityFailure(409,"REVIEW_OWNER_REQUIRED");
    }
    static long id(String value) {
        if(value==null || !value.matches("[1-9][0-9]{0,18}")) throw IdentityFailure.invalid();
        try { return Long.parseLong(value); } catch(NumberFormatException e) { throw IdentityFailure.invalid(); }
    }
    private static void code(String value) { if(value==null || !value.matches("[A-Z][A-Z0-9_]{0,63}")) throw IdentityFailure.invalid(); }
    static long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
}
