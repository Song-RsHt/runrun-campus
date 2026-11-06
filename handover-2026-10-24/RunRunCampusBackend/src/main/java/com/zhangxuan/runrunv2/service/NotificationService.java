package com.zhangxuan.runrunv2.service;

import java.util.List;
import java.util.Map;

/** 站内通知及订阅投递业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface NotificationService {

    /**
     * 查询站内通知及订阅投递列表，保留现有筛选和分页口径。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @return 站内通知及订阅投递列表，顺序和筛选沿用原查询
     */
    List<Map<String, Object>> list(String token);

    /**
     * 标记当前账号有权读取的指定消息为已读。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param id 目标业务记录标识
     */
    void markRead(String token, long id);

    /**
     * 领取到期订阅投递，保持原 claim、租约及重试范围。
     *
     * <p>领取时受原 M08 开关和租约条件约束；网络发送在领取与完成回写事务之外。
     *
     * @param limit 本批领取记录数量上限
     * @return 站内通知及订阅投递列表，顺序和筛选沿用原查询
     */
    List<Map<String, Object>> claim(int limit);

    /**
     * 在原完成事务中回写投递结果及下一次重试信息。
     *
     * @param id 目标业务记录标识
     * @param success 本次订阅投递是否成功
     * @param error 脱敏后的投递失败分类或说明
     */
    void complete(long id, boolean success, String error);
}
