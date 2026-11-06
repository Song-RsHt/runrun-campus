package com.zhangxuan.runrunv2.integration.storage;

import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.model.EvidenceBinding;
import com.zhangxuan.runrunv2.model.VerifiedEvidence;
import com.zhangxuan.runrunv2.vo.UploadTicketVO;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/** 证据生命周期尚未验收时保持拒绝，不能默用 legacy 公开上传服务；M07 在生命周期 QA 通过后替换此适配器。 */
@Component
public class PendingFulfillmentEvidence implements FulfillmentEvidence {

    public UploadTicketVO issue(
            EvidenceBinding binding, String type, long size, OffsetDateTime now) {
        throw new IdentityFailure(409, "PROOF_NOT_READY");
    }

    public VerifiedEvidence verify(EvidenceBinding binding, String ticket, OffsetDateTime now) {
        throw new IdentityFailure(409, "PROOF_NOT_READY");
    }
}
