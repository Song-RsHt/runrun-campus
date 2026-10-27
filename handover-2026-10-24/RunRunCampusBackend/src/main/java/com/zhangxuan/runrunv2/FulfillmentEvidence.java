package com.zhangxuan.runrunv2;

import static com.zhangxuan.runrunv2.FulfillmentModels.*;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

/** M07 private-storage port. Implementations must validate actual file bytes/type/size,
 * persist ticket binding, and verify AVAILABLE storage in the current transaction.
 * Neither storage keys nor client claims of successful upload are evidence. */
public interface FulfillmentEvidence {
    record Binding(String orderId, String taskId, String actorId, Kind kind) {}
    record Verified(Binding binding, String storageKey) {}
    Ticket issue(Binding binding, String contentType, long sizeBytes, OffsetDateTime now);
    Verified verify(Binding binding, String ticket, OffsetDateTime now);
}

/** Evidence lifecycle is not yet accepted: fail closed instead of silently using
 * the legacy public upload service. M07 replaces this adapter after lifecycle QA. */
@Component
class PendingFulfillmentEvidence implements FulfillmentEvidence {
    public FulfillmentModels.Ticket issue(Binding binding,String type,long size,OffsetDateTime now) { throw new IdentityFailure(409,"PROOF_NOT_READY"); }
    public Verified verify(Binding binding,String ticket,OffsetDateTime now) { throw new IdentityFailure(409,"PROOF_NOT_READY"); }
}
