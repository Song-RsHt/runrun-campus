package com.zhangxuan.runrunv2.service.fulfillment;
import com.zhangxuan.runrunv2.model.fulfillment.FulfillmentModels;
import com.zhangxuan.runrunv2.exception.IdentityFailure;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;
/** Evidence lifecycle is not yet accepted: fail closed instead of silently using
 * the legacy public upload service. M07 replaces this adapter after lifecycle QA. */
@Component
public class PendingFulfillmentEvidence implements FulfillmentEvidence {
    public FulfillmentModels.Ticket issue(Binding binding,String type,long size,OffsetDateTime now) { throw new IdentityFailure(409,"PROOF_NOT_READY"); }
    public Verified verify(Binding binding,String ticket,OffsetDateTime now) { throw new IdentityFailure(409,"PROOF_NOT_READY"); }
}
