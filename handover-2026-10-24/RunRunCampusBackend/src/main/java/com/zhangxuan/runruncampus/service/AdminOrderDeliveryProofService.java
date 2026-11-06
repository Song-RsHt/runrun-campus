package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.model.ProofContent;















/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface AdminOrderDeliveryProofService {

    ProofContent read(Long id);
}
