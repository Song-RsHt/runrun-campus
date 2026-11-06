package com.zhangxuan.runrunv2.integration.storage;

import com.zhangxuan.runrunv2.model.EvidenceBinding;
import com.zhangxuan.runrunv2.model.VerifiedEvidence;
import com.zhangxuan.runrunv2.vo.UploadTicketVO;

import java.time.OffsetDateTime;

/** 私有履约证据端口。实现须校验文件内容、类型及大小，并在当前事务中保存票据绑定、确认存储 AVAILABLE；存储键或客户端上传声明不能作为证据。 */
public interface FulfillmentEvidence {

    UploadTicketVO issue(
            EvidenceBinding binding, String contentType, long sizeBytes, OffsetDateTime now);

    VerifiedEvidence verify(EvidenceBinding binding, String ticket, OffsetDateTime now);
}
