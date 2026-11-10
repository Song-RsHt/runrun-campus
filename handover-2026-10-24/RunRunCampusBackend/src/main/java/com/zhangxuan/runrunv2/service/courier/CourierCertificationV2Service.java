package com.zhangxuan.runrunv2.service.courier;

import com.zhangxuan.runrunv2.model.upload.UploadSource;
import com.zhangxuan.runrunv2.service.identity.IdentityService;
import com.zhangxuan.runrunv2.vo.courier.CourierCertificationVO;

import java.util.Map;

/** 新系统骑手资质业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface CourierCertificationV2Service {

    /**
     * 按原幂等作用域提交当前骑手资质申请，重提时核对原申请状态。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param identity 现有身份服务协作者，沿用调用链的鉴权策略
     * @param requestId 本次操作的幂等请求标识
     * @return 新系统骑手资质
     */
    CourierCertificationVO apply(String token, IdentityService identity, String requestId);

    /**
     * 申请或保存新系统骑手资质私有上传内容，保留原校验顺序。
     *
     * <p>必须先校验身份、状态和并发版本，再读取文件；拒绝过大或不符合图片内容规则的文件。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param identity 现有身份服务协作者，沿用调用链的鉴权策略
     * @param id 目标业务记录标识
     * @param expectedVersion 调用方看到的并发版本，更新必须与当前版本一致
     * @param requestId 本次操作的幂等请求标识
     * @param file 延迟读取的上传文件；业务校验后才读取内容
     * @return 新系统骑手资质
     */
    CourierCertificationVO upload(
            String token,
            IdentityService identity,
            long id,
            long expectedVersion,
            String requestId,
            UploadSource file);

    /**
     * 管理员按并发版本及证据资源审核骑手资质。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param identity 现有身份服务协作者，沿用调用链的鉴权策略
     * @param id 目标业务记录标识
     * @param expectedVersion 调用方看到的并发版本，更新必须与当前版本一致
     * @param resource 私有图片资源标识
     * @param decision 管理员资质审核决定
     * @param reason 业务操作理由，按原有长度和必填规则校验
     * @param requestId 本次操作的幂等请求标识
     * @return 新系统骑手资质
     */
    CourierCertificationVO decide(
            String token,
            IdentityService identity,
            long id,
            long expectedVersion,
            String resource,
            String decision,
            String reason,
            String requestId);

    /**
     * 校验访问身份及保全状态后读取指定资质图片，并记录访问审计。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param identity 现有身份服务协作者，沿用调用链的鉴权策略
     * @param id 目标业务记录标识
     * @param resource 私有图片资源标识
     * @param requestId 本次操作的幂等请求标识
     * @return 授权读取的私有图片字节
     */
    byte[] read(String token, IdentityService identity, long id, String resource, String requestId);

    /**
     * 按资质状态、关键词和分页读取管理员资质列表。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param status 可选业务状态筛选
     * @param query 可选搜索关键词
     * @param page 从零开始的分页页码
     * @param size 每页记录数量
     * @return 资质列表、分页元数据及既有状态计数
     */
    Map<String, Object> adminList(String token, String status, String query, int page, int size);

    /**
     * 查询骑手履约统计及订单分页。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param courier 当前骑手账号或标识
     * @param page 从零开始的分页页码
     * @param size 每页记录数量
     * @return 骑手完成统计和订单分页
     */
    Map<String, Object> performance(String token, long courier, int page, int size);

    /**
     * 读取指定骑手名下资质；仅供已完成上游授权的内部流程。
     *
     * @param subject 资质所属骑手标识
     * @param id 目标业务记录标识
     * @return 新系统骑手资质
     */
    CourierCertificationVO get(long subject, long id);

    /**
     * 读取当前骑手最新资质。
     *
     * @param courier 当前骑手账号或标识
     * @return 新系统骑手资质
     */
    CourierCertificationVO getMine(long courier);
}
