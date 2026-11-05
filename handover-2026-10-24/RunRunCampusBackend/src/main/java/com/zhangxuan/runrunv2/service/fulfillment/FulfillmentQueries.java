package com.zhangxuan.runrunv2.service.fulfillment;
import com.zhangxuan.runrunv2.model.fulfillment.FulfillmentModels;
import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.security.IdentitySecrets;
import com.zhangxuan.runrunv2.service.identity.IdentityService;
import com.zhangxuan.runrunv2.service.configuration.ServiceConfigService;

import static com.zhangxuan.runrunv2.model.fulfillment.FulfillmentModels.*;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FulfillmentQueries {
    private final IdentityService identity;
    private final IdentitySecrets secrets;
    private final JdbcTemplate db;
    public FulfillmentQueries(IdentityService identity,IdentitySecrets secrets,JdbcTemplate db) { this.identity=identity; this.secrets=secrets; this.db=db; }
    private record Cursor(String createdAt,String id) {}
    @Transactional(readOnly=true)
    public Page<TaskItem> tasks(String token,String cursor,Integer requestedLimit,String status) {
        var actor=identity.requireRole(token,"COURIER",false); int limit=limit(requestedLimit);
        if(status!=null && !Set.of("OFFERED","ACTIVE","RELEASED","HANDED_OVER","DELIVERED","CANCELLED").contains(status)) throw IdentityFailure.invalid();
        String scope="tasks:"+actor.id()+":"+Objects.toString(status,""); Cursor after=decode(scope,cursor);
        var args=new ArrayList<Object>(); args.add(Long.valueOf(actor.id()));
        String where="t.courier_id=?";
        if(status!=null) { where+=" AND t.status=?"; args.add(status); }
        if(after!=null) { where+=" AND (t.created_at,t.id)<(?,?)"; args.add(OffsetDateTime.parse(after.createdAt())); args.add(Long.valueOf(after.id())); }
        args.add(limit+1);
        List<TaskItem> rows=db.query("SELECT t.*,o.order_no,o.status AS order_status,o.version AS order_version FROM runrun_v2.fulfillment_tasks t JOIN runrun_v2.v2_orders o ON o.id=t.order_id WHERE "+where+" ORDER BY t.created_at DESC,t.id DESC LIMIT ?",(r,n)->{
            String taskStatus=r.getString("status"),orderStatus=r.getString("order_status"); var actions=new ArrayList<String>();
            if(r.getObject("superseded_at")==null) {
                if(taskStatus.equals("OFFERED") && orderStatus.equals("EXCEPTION")) { actions.add("DECLINE"); if(actor.courierEligible()) actions.add("ACCEPT"); }
                if(actor.courierEligible()) {
                    if(taskStatus.equals("ACTIVE") && orderStatus.equals("ACCEPTED")) { actions.add("UPLOAD_PICKUP"); actions.add("PICKUP"); actions.add("ARRIVAL"); actions.add("SPECIFICATION"); }
                    if(taskStatus.equals("ACTIVE") && orderStatus.equals("PICKED_UP")) { actions.add("UPLOAD_DELIVERY"); actions.add("DELIVERY_LOCATION"); actions.add("DELIVER"); actions.add("SPECIFICATION"); }
                    if(taskStatus.equals("DELIVERED") && orderStatus.equals("DELIVERED_PENDING_CONFIRMATION")) actions.add("SPECIFICATION");
                }
            }
            return new TaskItem(r.getString("id"),taskStatus,r.getLong("version"),2,r.getString("order_id"),r.getString("order_no"),orderStatus,r.getLong("order_version"),r.getObject("created_at",OffsetDateTime.class),actions);
        },args.toArray());
        boolean more=rows.size()>limit; List<TaskItem> items=List.copyOf(rows.subList(0,Math.min(limit,rows.size())));
        TaskItem last=items.isEmpty()?null:items.get(items.size()-1);
        return new Page<>(items,more?encode(scope,last.createdAt(),last.taskId()):null);
    }
    @Transactional(readOnly=true)
    public Page<Order> pool(String token,String cursor,Integer requestedLimit) {
        var actor=identity.requireRole(token,"COURIER",true); int limit=limit(requestedLimit); String scope="pool:"+actor.id(); Cursor after=decode(scope,cursor);
        var args=new ArrayList<Object>(); String where="o.status='PENDING_ASSIGNMENT' AND EXISTS(SELECT 1 FROM runrun_v2.payment_orders p WHERE p.order_id=o.id AND p.purpose='ORIGINAL' AND p.status='PAID')";
        if(after!=null) { where+=" AND (o.created_at,o.id)<(?,?)"; args.add(OffsetDateTime.parse(after.createdAt())); args.add(Long.valueOf(after.id())); }
        args.add(limit+1);
        var rows=db.queryForList("SELECT o.* FROM runrun_v2.v2_orders o WHERE "+where+" ORDER BY o.created_at DESC,o.id DESC LIMIT ?",args.toArray());
        boolean more=rows.size()>limit; var items=rows.subList(0,Math.min(limit,rows.size())).stream().map(r->new Order(r.get("id").toString(),r.get("order_no").toString(),2,r.get("status").toString(),r.get("status").toString(),((Number)r.get("version")).longValue(),((Number)r.get("original_fee_cents")).longValue(),((Number)r.get("effective_fee_cents")).longValue(),null,null,null)).toList();
        var last=rows.isEmpty()?null:rows.get(Math.min(limit,rows.size())-1);
        OffsetDateTime created=last==null?null:((java.sql.Timestamp)last.get("created_at")).toInstant().atOffset(java.time.ZoneOffset.UTC);
        return new Page<>(items,more?encode(scope,created,last.get("id").toString()):null);
    }
    private int limit(Integer value) { int limit=value==null?20:value; if(limit<1||limit>100) throw IdentityFailure.invalid(); return limit; }
    private String encode(String scope,OffsetDateTime created,String id) {
        String payload=created+"|"+id; String body=Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8)); return body+"."+secrets.hash("fulfillment-cursor:"+scope,body);
    }
    private Cursor decode(String scope,String value) {
        if(value==null) return null;
        try {
            if(value.length()>512) throw IdentityFailure.invalid(); String[] parts=value.split("\\.",-1);
            if(parts.length!=2 || !java.security.MessageDigest.isEqual(secrets.hash("fulfillment-cursor:"+scope,parts[0]).getBytes(StandardCharsets.UTF_8),parts[1].getBytes(StandardCharsets.UTF_8))) throw IdentityFailure.invalid();
            String[] payload=new String(Base64.getUrlDecoder().decode(parts[0]),StandardCharsets.UTF_8).split("\\|",-1);
            if(payload.length!=2) throw IdentityFailure.invalid(); OffsetDateTime.parse(payload[0]); ServiceConfigService.id(payload[1]); return new Cursor(payload[0],payload[1]);
        } catch(RuntimeException ex) { throw IdentityFailure.invalid(); }
    }
}
