package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.OrderProofType;
import com.zhangxuan.runruncampus.model.UploadSource;
import com.zhangxuan.runruncampus.vo.OrderProofVO;

/** 订单私有图片凭证业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface OrderProofService {

    /**
     * 申请或保存订单私有图片凭证私有上传内容，保留原校验顺序。
     *
     * <p>须为有写权限的骑手且满足任务状态；不能因延迟读取适配提前读取上传内容。
     *
     * @param courier 当前骑手账号或标识
     * @param orderId 订单标识
     * @param type 本次操作的业务分类
     * @param file 延迟读取的上传文件；业务校验后才读取内容
     * @return 订单私有图片凭证
     */
    OrderProofVO upload(AppUser courier, Long orderId, OrderProofType type, UploadSource file);
}
