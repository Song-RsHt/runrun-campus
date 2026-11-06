package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.OrderProofType;
import com.zhangxuan.runruncampus.model.UploadSource;
import com.zhangxuan.runruncampus.vo.OrderProofVO;























/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface OrderProofService {

    OrderProofVO upload(AppUser courier, Long orderId, OrderProofType type, UploadSource file);
}
