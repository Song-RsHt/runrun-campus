package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.model.ProofContent;

/** 管理员送达凭证业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface AdminOrderDeliveryProofService {

    /**
     * 按既有权限和证据状态读取私有文件。
     *
     * @param id 目标业务记录标识
     * @return 私有凭证二进制载荷
     */
    ProofContent read(Long id);
}
