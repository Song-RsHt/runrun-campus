package com.zhangxuan.runrunv2;

import static com.zhangxuan.runrunv2.PaymentModels.*;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@ConditionalOnProperty(name="rrc.payment.mock-enabled",havingValue="true")
public class PaymentService implements OriginalPaymentWriter {
    private final JdbcTemplate db;
    private final IdentityService identity;
    private final OrderStore orders;
    private final OrderIdempotency requests;
    private final OrderJson json;
    private final MockPaymentAdapter channel;
    private final Clock clock;
    public PaymentService(JdbcTemplate db,IdentityService identity,OrderStore orders,OrderIdempotency requests,
                          OrderJson json,MockPaymentAdapter channel,Clock clock) {
        this.db=db; this.identity=identity; this.orders=orders; this.requests=requests; this.json=json; this.channel=channel; this.clock=clock;
    }
    public boolean available() { return true; }
    @Transactional(propagation=Propagation.MANDATORY)
    public OrderModels.OriginalPayment create(String orderId,long amountCents,OffsetDateTime deadline) {
        var order=orders.read(orderId,true);
        if(!order.status().equals("PENDING_PAYMENT") || amountCents!=order.originalFeeCents()
                || !deadline.toInstant().equals(order.pendingPaymentDeadline().toInstant())) throw conflict();
        String id=db.queryForObject("INSERT INTO runrun_v2.payment_orders(order_id,purpose,amount_cents,deadline) VALUES(?,'ORIGINAL',?,?) RETURNING id::text",
                String.class,Long.valueOf(orderId),amountCents,deadline);
        return new OrderModels.OriginalPayment(id,orderId,"ORIGINAL",amountCents,"UNPAID",deadline,0);
    }
    @Transactional(readOnly=true)
    public Summary get(String token,String paymentId) {
        var actor=identity.requireRole(token,"USER",false);
        Payment payment=payment(paymentId,false);
        own(actor.id(),orders.read(payment.orderId(),false));
        return summary(payment);
    }
    @Transactional
    public Started start(String token,String paymentId,String key,VersionRequest body) {
        var actor=identity.authorizeForWrite(token,"USER",false);
        Payment p=lockPayment(paymentId,actor.id());
        var content=List.of(paymentId,required(body));
        Started replay=requests.replay(actor.id(),"payments:attempt",key,content,Started.class);
        if(replay!=null) return replay;
        version(p.version(),body.expectedVersion());
        if(p.status().equals("CLOSED") || originalExpired(p)) throw new IdentityFailure(409,"PAYMENT_CLOSED");
        if(p.status().equals("PAID")) throw conflict();
        // UNKNOWN includes transport timeout: only reconciliation can clear the old attempt.
        if(!active(p.id()).isEmpty()) throw new IdentityFailure(409,"PAYMENT_RESULT_PENDING");
        int number=db.queryForObject("SELECT coalesce(max(attempt_no),0)+1 FROM runrun_v2.payment_attempts WHERE payment_order_id=?",Integer.class,Long.valueOf(p.id()));
        String id=db.queryForObject("INSERT INTO runrun_v2.payment_attempts(payment_order_id,attempt_no,status,channel_reference) VALUES(?,?,'PROCESSING',?) RETURNING id::text",
                String.class,Long.valueOf(p.id()),number,"mock-payment-"+UUID.randomUUID());
        channel.submit("PAYMENT",id);
        paymentStatus(p.id(),"PROCESSING");
        Started response=new Started(payment(p.id(),false),attempt(id));
        event(p.orderId(),actor.id(),"PAYMENT_ATTEMPT_STARTED",id,Map.of("paymentId",p.id(),"attemptId",id));
        requests.finish(actor.id(),"payments:attempt",key,"PAYMENT_ATTEMPT",id,response);
        return response;
    }
    @Transactional
    public Summary queryPayment(String token,String id,String key,VersionRequest body) {
        var actor=identity.authorizeForWrite(token,"USER",false);
        Payment p=lockPayment(id,actor.id());
        Summary replay=requests.replay(actor.id(),"payments:query",key,List.of(id,required(body)),Summary.class);
        if(replay!=null) return replay;
        version(p.version(),body.expectedVersion());
        // Query even after close: a lost late receipt must still be compensated.
        for(Attempt a:attempts(id)) if(!a.status().equals("SUCCESS"))
            applyPayment(p,a,channel.query("PAYMENT",a.id()),"query:payment:"+a.id()+":"+channel.revision("PAYMENT",a.id()));
        Summary result=summary(payment(id,false));
        requests.finish(actor.id(),"payments:query",key,"PAYMENT_ORDER",id,result);
        return result;
    }
    @Transactional
    public Attempt paymentResult(String token,String id,String key,ResultRequest body) {
        var actor=identity.authorizeForWrite(token,"ADMIN",false);
        Attempt a=attempt(id);
        Payment p=lockPayment(a.paymentOrderId(),null);
        a=attempt(id);
        validateResult(body);
        Attempt replay=requests.replay(actor.id(),"payments:result",key,List.of(id,body),Attempt.class);
        if(replay!=null) return replay;
        if(!knownEvent("PAYMENT",id,body.result(),body.eventId())) {
            version(a.version(),body.expectedVersion());
            channel.record("PAYMENT",id,body.result());
            applyPayment(p,a,body.result(),body.eventId());
        }
        audit(actor.id(),"PAYMENT_RESULT",id,key,"isolated simulated channel result");
        Attempt result=attempt(id);
        requests.finish(actor.id(),"payments:result",key,"PAYMENT_ATTEMPT",id,result);
        return result;
    }
    @Transactional
    public Refund refundResult(String token,String id,String key,ResultRequest body) {
        var actor=identity.authorizeForWrite(token,"ADMIN",false);
        Refund r=refund(id);
        lockPayment(r.paymentOrderId(),null); r=refund(id);
        validateResult(body);
        Refund replay=requests.replay(actor.id(),"refunds:result",key,List.of(id,body),Refund.class);
        if(replay!=null) return replay;
        if(!knownEvent("REFUND",id,body.result(),body.eventId())) {
            version(r.version(),body.expectedVersion());
            channel.record("REFUND",id,body.result());
            applyRefund(r,body.result(),body.eventId());
        }
        audit(actor.id(),"REFUND_RESULT",id,key,"isolated simulated channel result");
        Refund result=refund(id);
        requests.finish(actor.id(),"refunds:result",key,"REFUND_ORDER",id,result);
        return result;
    }
    @Transactional
    public Refund queryRefund(String token,String id,String key,VersionRequest body) {
        var actor=identity.authorizeForWrite(token,"USER",false);
        Refund r=refund(id); lockPayment(r.paymentOrderId(),actor.id()); r=refund(id);
        Refund replay=requests.replay(actor.id(),"refunds:query",key,List.of(id,required(body)),Refund.class);
        if(replay!=null) return replay;
        version(r.version(),body.expectedVersion());
        applyRefund(r,channel.query("REFUND",id),"query:refund:"+id+":"+channel.revision("REFUND",id));
        Refund result=refund(id);
        requests.finish(actor.id(),"refunds:query",key,"REFUND_ORDER",id,result);
        return result;
    }
    @Transactional
    public Cancelled cancel(String token,String id,String key,CancelRequest body) {
        var actor=identity.authorizeForWrite(token,"USER",false);
        var order=orders.read(id,true); own(actor.id(),order);
        if(body==null) throw IdentityFailure.invalid();
        IdentityService.text(body.reason(),500);
        String reason=body.reason();
        Cancelled replay=requests.replay(actor.id(),"payments:cancel",key,List.of(id,body),Cancelled.class);
        if(replay!=null) return replay;
        version(order.version(),body.expectedVersion());
        if(!Set.of("PENDING_PAYMENT","PENDING_ASSIGNMENT","ACCEPTED").contains(order.status())) throw conflict();
        closeOrder(id);
        // Cancel all financial obligations, retaining their evidence and every receipt.
        db.update("UPDATE runrun_v2.payment_adjustments SET status='CANCELLED',resolved_at=?,version=version+1 WHERE order_id=? AND status IN ('PENDING_SUPPLEMENT','PENDING_REFUND')",now(),Long.valueOf(id));
        for(Payment p:paymentsForOrder(id)) {
            payment(p.id(),true);
            for(Attempt a:attempts(p.id())) if(a.status().equals("SUCCESS")) {
                long available=available(a.id(),p.amountCents());
                if(available>0) createRefund(p,a.id(),null,available,"CANCELLATION","cancel:"+id+":"+a.id(),reason);
            }
            if(!p.status().equals("PAID") && !p.status().equals("CLOSED")) paymentStatus(p.id(),"CLOSED");
        }
        event(id,actor.id(),"ORDER_CANCELLED",key,Map.of("reason",reason));
        notifyUser(id,"cancel:"+id,"ORDER_CANCELLED");
        Cancelled result=new Cancelled(orders.read(id,false),refundsForOrder(id));
        requests.finish(actor.id(),"payments:cancel",key,"V2_ORDER",id,result);
        return result;
    }
    /** Trusted refund worker/admin-action port; callers enforce their confirmation/MFA
     * policy. FAILED retries reuse the reserved row/channel identity, UNKNOWN queries. */
    @Transactional(propagation=Propagation.MANDATORY)
    public Refund retryRefund(String token,String id,long expectedVersion,String reason,String key) {
        var actor=identity.authorizeForWrite(token,"ADMIN",false);
        IdentityService.text(reason,500);
        Refund r=refund(id); lockPayment(r.paymentOrderId(),null); r=refund(id);
        Refund replay=requests.replay(actor.id(),"refunds:retry",key,List.of(id,expectedVersion,reason),Refund.class);
        if(replay!=null) return replay;
        version(r.version(),expectedVersion);
        Result actual=channel.query("REFUND",id);
        if(!r.status().equals("SUCCEEDED") && actual==Result.FAILED) {
            channel.retryRefund(id);
            db.update("UPDATE runrun_v2.refund_orders SET status='PROCESSING',version=version+1 WHERE id=?",Long.valueOf(id));
            event(r.orderId(),actor.id(),"REFUND_RETRIED",key,Map.of("refundId",id,"reason",reason));
        } else applyRefund(r,actual,"query:refund:"+id+":"+channel.revision("REFUND",id));
        audit(actor.id(),"REFUND_RETRY",id,key,reason);
        Refund result=refund(id);
        requests.finish(actor.id(),"refunds:retry",key,"REFUND_ORDER",id,result);
        return result;
    }
    /** M05 approval port. Caller derives delta from approved server pricing/evidence,
     * holds its review lock, and calls this in the SAME transaction. No HTTP mapping. */
    @Transactional(propagation=Propagation.MANDATORY)
    public AdjustmentFunds approvedAdjustment(String token,String orderId,String adjustmentId,long deltaCents,
                                               long expectedOrderVersion,String reason,String key) {
        var actor=identity.authorizeForWrite(token,"ADMIN",false);
        lockNotificationRecipient(orderId);
        var order=orders.read(orderId,true);
        IdentityService.text(reason,500); ServiceConfigService.id(adjustmentId);
        if(deltaCents==0 || deltaCents==Long.MIN_VALUE) throw IdentityFailure.invalid();
        var content=List.of(orderId,adjustmentId,deltaCents,expectedOrderVersion,reason);
        AdjustmentFunds replay=requests.replay(actor.id(),"payments:adjustment",key,content,AdjustmentFunds.class);
        if(replay!=null) return replay;
        version(order.version(),expectedOrderVersion);
        if(db.queryForObject("SELECT count(*) FROM runrun_v2.payment_adjustments WHERE id=?",Long.class,Long.valueOf(adjustmentId))>0) throw conflict();
        if(!Set.of("PENDING_ASSIGNMENT","ACCEPTED","PICKED_UP","DELIVERED_PENDING_CONFIRMATION").contains(order.status())) throw conflict();
        if(db.queryForObject("SELECT count(*) FROM runrun_v2.payment_adjustments WHERE order_id=? AND status IN ('PENDING_SUPPLEMENT','PENDING_REFUND')",Long.class,Long.valueOf(orderId))>0)
            throw new IdentityFailure(409,"ADJUSTMENT_ALREADY_OPEN");
        long effective;
        try { effective=Math.addExact(order.effectiveFeeCents(),deltaCents); }
        catch(ArithmeticException overflow) { throw IdentityFailure.invalid(); }
        if(effective<=0) throw IdentityFailure.invalid();
        Payment original=paymentsForOrder(orderId).stream().filter(p->p.purpose().equals("ORIGINAL")).findFirst().orElseThrow(PaymentService::conflict);
        if(!original.status().equals("PAID")) throw conflict();
        db.update("INSERT INTO runrun_v2.payment_adjustments(id,order_id,delta_cents,status,approved_by,reason,approved_at) VALUES(?,?,?,?,?,?,?)",
                Long.valueOf(adjustmentId),Long.valueOf(orderId),deltaCents,deltaCents>0?"PENDING_SUPPLEMENT":"PENDING_REFUND",Long.valueOf(actor.id()),reason,now());
        db.update("UPDATE runrun_v2.v2_orders SET effective_fee_cents=?,confirm_started_at=NULL,confirm_deadline=NULL,version=version+1,updated_at=? WHERE id=?",effective,now(),Long.valueOf(orderId));
        Payment supplement=null;
        if(deltaCents>0) {
            String id=db.queryForObject("INSERT INTO runrun_v2.payment_orders(order_id,adjustment_id,purpose,amount_cents,deadline) VALUES(?,?,'SUPPLEMENT',?,?) RETURNING id::text",
                    String.class,Long.valueOf(orderId),Long.valueOf(adjustmentId),deltaCents,now().plusMinutes(15));
            supplement=payment(id,false);
        } else {
            long remaining=-deltaCents;
            // Refund each actual receipt; no invented balance when earlier supplements exist.
            for(Payment p:paymentsForOrder(orderId)) if(p.status().equals("PAID")) {
                payment(p.id(),true);
                String source=credited(p.id());
                long amount=Math.min(remaining,available(source,p.amountCents()));
                if(amount>0) { createRefund(p,source,adjustmentId,amount,"DOWNGRADE","downgrade:"+adjustmentId+":"+source,reason); remaining-=amount; }
            }
            if(remaining!=0) throw new IdentityFailure(409,"AMOUNT_EXCEEDS_AVAILABLE");
        }
        audit(actor.id(),"ADJUSTMENT_FUNDS",adjustmentId,key,reason);
        event(orderId,actor.id(),"ADJUSTMENT_FUNDS_CREATED",adjustmentId,Map.of("deltaCents",deltaCents));
        notifyUser(orderId,"adjustment:"+adjustmentId,"ADJUSTMENT_FUNDS_CREATED");
        AdjustmentFunds result=new AdjustmentFunds(adjustmentId,supplement,refundsForOrder(orderId).stream().filter(r->adjustmentId.equals(r.adjustmentId())).toList());
        requests.finish(actor.id(),"payments:adjustment",key,"PAYMENT_ADJUSTMENT",adjustmentId,result);
        return result;
    }
    @Transactional
    public int closeExpired() {
        var ids=db.queryForList("SELECT id::text FROM runrun_v2.v2_orders WHERE status='PENDING_PAYMENT' AND pending_payment_deadline<=? ORDER BY id LIMIT 100",String.class,now());
        int closed=0;
        for(String id:ids) {
            var order=orders.read(id,true);
            if(order.status().equals("PENDING_PAYMENT") && !now().isBefore(order.pendingPaymentDeadline())) {
                closeOrder(id);
                db.update("UPDATE runrun_v2.payment_orders SET status='CLOSED',version=version+1,updated_at=? WHERE order_id=? AND purpose='ORIGINAL' AND status NOT IN ('PAID','CLOSED')",now(),Long.valueOf(id));
                event(id,null,"PAYMENT_EXPIRED",id,Map.of()); closed++;
            }
        }
        return closed;
    }
    // All paths (callback, query, and timeout recovery) converge here under order/payment locks.
    private void applyPayment(Payment stale,Attempt a,Result result,String eventId) {
        if(seen("PAYMENT",a.id(),result,eventId) || a.status().equals("SUCCESS")) return;
        Payment p=payment(stale.id(),false);
        String status=result.name();
        if(a.status().equals("FAILED") && result!=Result.SUCCESS) return;
        if(!a.status().equals(status)) db.update("UPDATE runrun_v2.payment_attempts SET status=?,result_received_at=?,version=version+1 WHERE id=?",status,now(),Long.valueOf(a.id()));
        if(result!=Result.SUCCESS) {
            if(!Set.of("PAID","CLOSED").contains(p.status())) paymentStatus(p.id(),result==Result.PROCESSING?"PROCESSING":status);
            return;
        }
        var order=orders.read(p.orderId(),true);
        boolean closed=order.status().equals("CANCELLED") || p.status().equals("CLOSED");
        if(originalExpired(p) && order.status().equals("PENDING_PAYMENT")) {
            closeOrder(p.orderId()); paymentStatus(p.id(),"CLOSED"); closed=true;
            event(p.orderId(),null,"PAYMENT_EXPIRED",p.orderId(),Map.of());
        }
        if(p.purpose().equals("SUPPLEMENT") && !adjustmentOpen(p.adjustmentId())) closed=true;
        if(closed || p.status().equals("PAID")) {
            createRefund(p,a.id(),null,p.amountCents(),closed?"LATE_PAYMENT":"EXTRA_RECEIPT","receipt:"+a.id(),"late or additional simulated receipt");
            event(p.orderId(),null,"PAYMENT_COMPENSATION_REQUIRED",a.id(),Map.of("attemptId",a.id()));
            return;
        }
        if(p.purpose().equals("ORIGINAL") && !order.status().equals("PENDING_PAYMENT")) throw conflict();
        db.update("UPDATE runrun_v2.payment_orders SET status='PAID',paid_at=?,credited_attempt_id=?,version=version+1,updated_at=? WHERE id=?",now(),Long.valueOf(a.id()),now(),Long.valueOf(p.id()));
        if(p.purpose().equals("ORIGINAL")) {
            // Payment receipt and task-pool admission are committed atomically (payment rules §2).
            db.update("UPDATE runrun_v2.v2_orders SET status='PENDING_ASSIGNMENT',version=version+1,updated_at=? WHERE id=?",now(),Long.valueOf(p.orderId()));
        } else resolveAdjustment(p.adjustmentId());
        financial(p.orderId(),"receipt:"+a.id(),p.purpose()+"_RECEIPT",p.amountCents(),Map.of("paymentId",p.id(),"attemptId",a.id()));
        event(p.orderId(),null,"PAYMENT_SUCCEEDED",a.id(),Map.of("paymentId",p.id()));
        notifyUser(p.orderId(),"payment:"+a.id(),"PAYMENT_SUCCEEDED");
    }
    private void applyRefund(Refund r,Result result,String eventId) {
        if(seen("REFUND",r.id(),result,eventId) || r.status().equals("SUCCEEDED")) return;
        String status=result==Result.SUCCESS?"SUCCEEDED":result.name();
        // Failed refunds retain their balance reservation: a late receipt must never
        // over-refund after another request reused that money. Retry/query the SAME row.
        if(!r.status().equals(status)) db.update("UPDATE runrun_v2.refund_orders SET status=?,succeeded_at=?,version=version+1 WHERE id=?",status,result==Result.SUCCESS?now():null,Long.valueOf(r.id()));
        if(result==Result.SUCCESS) {
            if(r.adjustmentId()!=null && db.queryForObject("SELECT count(*) FROM runrun_v2.refund_orders WHERE adjustment_id=? AND status<>'SUCCEEDED'",Long.class,Long.valueOf(r.adjustmentId()))==0)
                resolveAdjustment(r.adjustmentId());
            refundFinancial(r);
            event(r.orderId(),null,"REFUND_SUCCEEDED",r.id(),Map.of("refundId",r.id()));
            notifyUser(r.orderId(),"refund:"+r.id(),"REFUND_SUCCEEDED");
        }
    }
    private Refund createRefund(Payment p,String source,String adjustment,long cents,String purpose,String businessKey,String reason) {
        var existing=db.queryForList("SELECT id::text FROM runrun_v2.refund_orders WHERE business_key=?",String.class,businessKey);
        if(!existing.isEmpty()) return refund(existing.get(0));
        if(cents<=0 || cents>available(source,p.amountCents())) throw new IdentityFailure(409,"AMOUNT_EXCEEDS_AVAILABLE");
        if(!attempt(source).status().equals("SUCCESS")) throw conflict();
        String id=db.queryForObject("INSERT INTO runrun_v2.refund_orders(order_id,payment_order_id,source_attempt_id,adjustment_id,amount_cents,purpose,reason,business_key,channel_reference) VALUES(?,?,?,?,?,?,?,?,?) RETURNING id::text",
                String.class,Long.valueOf(p.orderId()),Long.valueOf(p.id()),Long.valueOf(source),adjustment==null?null:Long.valueOf(adjustment),cents,purpose,reason,businessKey,"mock-refund-"+UUID.randomUUID());
        channel.submit("REFUND",id);
        event(p.orderId(),null,"REFUND_REQUESTED",id,Map.of("refundId",id,"amountCents",cents));
        notifyUser(p.orderId(),"refund-request:"+id,"REFUND_REQUESTED");
        return refund(id);
    }
    private void resolveAdjustment(String id) {
        // M05 consumes this event before starting its full ten-minute window; M03
        // cannot decide whether another review/after-sales case still blocks completion.
        if(db.update("UPDATE runrun_v2.payment_adjustments SET status='COMPLETED',resolved_at=?,version=version+1 WHERE id=? AND status IN ('PENDING_SUPPLEMENT','PENDING_REFUND')",now(),Long.valueOf(id))>0) {
            String orderId=db.queryForObject("SELECT order_id::text FROM runrun_v2.payment_adjustments WHERE id=?",String.class,Long.valueOf(id));
            event(orderId,null,"ADJUSTMENT_FUNDS_RESOLVED",id,Map.of("adjustmentId",id));
        }
    }
    private boolean adjustmentOpen(String id) { return db.queryForObject("SELECT status='PENDING_SUPPLEMENT' FROM runrun_v2.payment_adjustments WHERE id=?",Boolean.class,Long.valueOf(id)); }
    private long available(String source,long received) {
        return received-db.queryForObject("SELECT coalesce(sum(amount_cents),0) FROM runrun_v2.refund_orders WHERE source_attempt_id=?",Long.class,Long.valueOf(source));
    }
    private String credited(String payment) { return db.queryForObject("SELECT credited_attempt_id::text FROM runrun_v2.payment_orders WHERE id=?",String.class,Long.valueOf(payment)); }
    private void closeOrder(String id) { db.update("UPDATE runrun_v2.v2_orders SET status='CANCELLED',cancelled_at=?,confirm_started_at=NULL,confirm_deadline=NULL,version=version+1,updated_at=? WHERE id=?",now(),now(),Long.valueOf(id)); }
    private boolean originalExpired(Payment p) { return p.purpose().equals("ORIGINAL") && !now().isBefore(p.deadline()); }
    private Payment lockPayment(String id,String owner) {
        Payment p=payment(id,false);
        // Identity writes lock the user's account first. Acquire the outbox FK lock
        // before the order as well, so a callback cannot deadlock a user cancellation.
        lockNotificationRecipient(p.orderId());
        var order=orders.read(p.orderId(),true);
        if(owner!=null) own(owner,order);
        return payment(id,true);
    }
    private void lockNotificationRecipient(String orderId) {
        db.queryForList("SELECT id FROM runrun_v2.app_users WHERE id=(SELECT user_id FROM runrun_v2.v2_orders WHERE id=?) FOR KEY SHARE",ServiceConfigService.id(orderId));
    }
    private void paymentStatus(String id,String status) { db.update("UPDATE runrun_v2.payment_orders SET status=?,version=version+1,updated_at=? WHERE id=? AND status<>?",status,now(),Long.valueOf(id),status); }
    private Payment payment(String id,boolean lock) {
        var rows=db.query("SELECT * FROM runrun_v2.payment_orders WHERE id=?"+(lock?" FOR UPDATE":""),(r,n)->new Payment(r.getString("id"),r.getString("order_id"),r.getString("adjustment_id"),r.getString("purpose"),r.getLong("amount_cents"),r.getString("status"),r.getObject("deadline",OffsetDateTime.class),r.getObject("paid_at",OffsetDateTime.class),r.getLong("version")),ServiceConfigService.id(id));
        if(rows.isEmpty()) throw missing(); return rows.get(0);
    }
    private Attempt attempt(String id) {
        var rows=db.query("SELECT * FROM runrun_v2.payment_attempts WHERE id=?",(r,n)->new Attempt(r.getString("id"),r.getString("payment_order_id"),r.getInt("attempt_no"),r.getString("status"),r.getLong("version")),ServiceConfigService.id(id));
        if(rows.isEmpty()) throw missing(); return rows.get(0);
    }
    private Refund refund(String id) {
        var rows=db.query("SELECT * FROM runrun_v2.refund_orders WHERE id=?",(r,n)->new Refund(r.getString("id"),r.getString("order_id"),r.getString("payment_order_id"),r.getString("adjustment_id"),r.getLong("amount_cents"),r.getString("purpose"),r.getString("status"),r.getObject("succeeded_at",OffsetDateTime.class),r.getLong("version")),ServiceConfigService.id(id));
        if(rows.isEmpty()) throw missing(); return rows.get(0);
    }
    private List<Attempt> attempts(String id) { return db.queryForList("SELECT id::text FROM runrun_v2.payment_attempts WHERE payment_order_id=? ORDER BY attempt_no",String.class,Long.valueOf(id)).stream().map(this::attempt).toList(); }
    private List<Attempt> active(String id) { return attempts(id).stream().filter(a->Set.of("PROCESSING","UNKNOWN").contains(a.status())).toList(); }
    private List<Payment> paymentsForOrder(String id) { return db.queryForList("SELECT id::text FROM runrun_v2.payment_orders WHERE order_id=? ORDER BY id",String.class,Long.valueOf(id)).stream().map(p->payment(p,false)).toList(); }
    private List<Refund> refundsForOrder(String id) { return db.queryForList("SELECT id::text FROM runrun_v2.refund_orders WHERE order_id=? ORDER BY id",String.class,Long.valueOf(id)).stream().map(this::refund).toList(); }
    private Summary summary(Payment p) { return new Summary(p,attempts(p.id()),refundsForOrder(p.orderId()).stream().filter(r->r.paymentOrderId().equals(p.id())).toList()); }
    private void own(String actor,OrderModels.Order order) { if(!actor.equals(order.userId())) throw missing(); }
    private OffsetDateTime now() { return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS).atOffset(ZoneOffset.UTC); }
    private static IdentityFailure conflict() { return new IdentityFailure(409,"ORDER_STATE_CONFLICT"); }
    private static IdentityFailure missing() { return new IdentityFailure(404,"RESOURCE_NOT_FOUND"); }
    private static void version(long actual,Long expected) { if(expected==null || expected<0) throw IdentityFailure.invalid(); if(actual!=expected) throw conflict(); }
    private static VersionRequest required(VersionRequest body) { if(body==null || body.expectedVersion()==null) throw IdentityFailure.invalid(); return body; }
    private void validateResult(ResultRequest body) { if(body==null || body.result()==null || body.expectedVersion()==null || body.expectedVersion()<0) throw IdentityFailure.invalid(); IdentityService.text(body.eventId(),128); }
    private boolean seen(String type,String id,Result result,String eventId) {
        if(knownEvent(type,id,result,eventId)) return true;
        db.update("INSERT INTO runrun_v2.payment_result_events(event_id,resource_type,resource_id,result) VALUES(?,?,?,?)",eventId,type,Long.valueOf(id),result.name());
        return false;
    }
    private boolean knownEvent(String type,String id,Result result,String eventId) {
        // Event IDs are global, including across orders and administrator accounts.
        db.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",(org.springframework.jdbc.core.RowMapper<Object>)(r,n)->null,"payment-event:"+eventId);
        var rows=db.queryForList("SELECT resource_type,resource_id,result FROM runrun_v2.payment_result_events WHERE event_id=?",eventId);
        if(!rows.isEmpty()) {
            var row=rows.get(0);
            if(!type.equals(row.get("resource_type")) || !id.equals(row.get("resource_id").toString()) || !result.name().equals(row.get("result")))
                throw new IdentityFailure(409,"IDEMPOTENCY_KEY_CONFLICT");
            return true;
        }
        return false;
    }
    private void refundFinancial(Refund r) {
        String source=db.queryForObject("SELECT source_attempt_id::text FROM runrun_v2.refund_orders WHERE id=?",String.class,Long.valueOf(r.id()));
        long received=payment(r.paymentOrderId(),false).amountCents();
        long total=db.queryForObject("SELECT sum(amount_cents) FROM runrun_v2.refund_orders WHERE source_attempt_id=? AND status='SUCCEEDED'",Long.class,Long.valueOf(source));
        long originalPlatform=received/100*30+(received%100)*30/100;
        long target=java.math.BigInteger.valueOf(total).multiply(java.math.BigInteger.valueOf(originalPlatform)).divide(java.math.BigInteger.valueOf(received)).longValueExact();
        long previous=db.queryForObject("SELECT coalesce(sum(platform_cents),0) FROM runrun_v2.payment_financial_events WHERE payload->>'sourceAttemptId'=?",Long.class,source);
        long platform=target-previous;
        db.update("INSERT INTO runrun_v2.payment_financial_events(order_id,event_key,kind,amount_cents,platform_cents,courier_cents,payload) VALUES(?,?,?,?,?,?,?::jsonb)",Long.valueOf(r.orderId()),"refund:"+r.id(),r.purpose()+"_REFUND",r.amountCents(),platform,r.amountCents()-platform,json.write(Map.of("refundId",r.id(),"paymentId",r.paymentOrderId(),"sourceAttemptId",source)));
    }
    private void event(String order,String actor,String type,String key,Object payload) { db.update("INSERT INTO runrun_v2.v2_order_events(order_id,actor_id,event_type,request_id,payload) VALUES(?,?,?,?,?::jsonb) ON CONFLICT DO NOTHING",Long.valueOf(order),actor==null?null:Long.valueOf(actor),type,key,json.write(payload)); }
    private void financial(String order,String key,String kind,long cents,Object payload) {
        long platform=cents/100*30+(cents%100)*30/100;
        db.update("INSERT INTO runrun_v2.payment_financial_events(order_id,event_key,kind,amount_cents,platform_cents,courier_cents,payload) VALUES(?,?,?,?,?,?,?::jsonb) ON CONFLICT DO NOTHING",Long.valueOf(order),key,kind,cents,platform,cents-platform,json.write(payload));
    }
    private void notifyUser(String order,String key,String type) {
        db.update("INSERT INTO runrun_v2.notification_outbox(recipient_id,event_key,channel,payload) SELECT user_id,?,'IN_APP',?::jsonb FROM runrun_v2.v2_orders WHERE id=? ON CONFLICT DO NOTHING",key,json.write(Map.of("orderId",order,"kind",type)),Long.valueOf(order));
    }
    private void audit(String actor,String action,String id,String key,String reason) { db.update("INSERT INTO runrun_v2.payment_admin_audit(actor_id,action,resource_id,request_id,reason) VALUES(?,?,?,?,?)",Long.valueOf(actor),action,Long.valueOf(id),key,reason); }
}
