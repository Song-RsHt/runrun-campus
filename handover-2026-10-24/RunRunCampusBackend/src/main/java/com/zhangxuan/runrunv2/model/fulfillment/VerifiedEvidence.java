package com.zhangxuan.runrunv2.model.fulfillment;

/** 已核验证据；仅承载内部协作所需的数据或校验逻辑。 */
public record VerifiedEvidence(EvidenceBinding binding, String storageKey) {}
