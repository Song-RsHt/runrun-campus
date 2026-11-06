package com.zhangxuan.runrunv2.integration.storage;

import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.model.EvidenceBinding;
import com.zhangxuan.runrunv2.model.VerifiedEvidence;
import com.zhangxuan.runrunv2.vo.UploadTicketVO;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

/**
 * Evidence lifecycle is not yet accepted: fail closed instead of silently using the legacy public
 * upload service. M07 replaces this adapter after lifecycle QA.
 */
@Component
public class PendingFulfillmentEvidence implements FulfillmentEvidence {

    public UploadTicketVO issue(EvidenceBinding binding, String type, long size, OffsetDateTime now) {
        throw new IdentityFailure(409, "PROOF_NOT_READY");
    }

    public VerifiedEvidence verify(EvidenceBinding binding, String ticket, OffsetDateTime now) {
        throw new IdentityFailure(409, "PROOF_NOT_READY");
    }
}
