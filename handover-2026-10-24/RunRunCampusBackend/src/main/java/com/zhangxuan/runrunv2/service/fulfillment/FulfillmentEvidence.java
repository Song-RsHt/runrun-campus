package com.zhangxuan.runrunv2.service.fulfillment;
import com.zhangxuan.runrunv2.model.fulfillment.FulfillmentModels;
import com.zhangxuan.runrunv2.exception.IdentityFailure;

import static com.zhangxuan.runrunv2.model.fulfillment.FulfillmentModels.*;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

/** M07 private-storage port. Implementations must validate actual file bytes/type/size,
 * persist ticket binding, and verify AVAILABLE storage in the current transaction.
 * Neither storage keys nor client claims of successful upload are evidence. */
public interface FulfillmentEvidence {
    public record Binding(String orderId, String taskId, String actorId, Kind kind) {}
    public record Verified(Binding binding, String storageKey) {}
    Ticket issue(Binding binding, String contentType, long sizeBytes, OffsetDateTime now);
    Verified verify(Binding binding, String ticket, OffsetDateTime now);
}
