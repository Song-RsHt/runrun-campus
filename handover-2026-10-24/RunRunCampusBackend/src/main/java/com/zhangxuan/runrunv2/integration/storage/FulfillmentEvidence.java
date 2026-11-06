package com.zhangxuan.runrunv2.integration.storage;

import com.zhangxuan.runrunv2.model.EvidenceBinding;
import com.zhangxuan.runrunv2.model.VerifiedEvidence;
import com.zhangxuan.runrunv2.vo.UploadTicketVO;
import java.time.OffsetDateTime;








/**
 * M07 private-storage port. Implementations must validate actual file bytes/type/size, persist
 * ticket binding, and verify AVAILABLE storage in the current transaction. Neither storage keys nor
 * client claims of successful upload are evidence.
 */
public interface FulfillmentEvidence {

    UploadTicketVO issue(EvidenceBinding binding, String contentType, long sizeBytes, OffsetDateTime now);

    VerifiedEvidence verify(EvidenceBinding binding, String ticket, OffsetDateTime now);
}
