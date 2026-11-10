package com.zhangxuan.runrunv2.service.notification;

import com.zhangxuan.runrunv2.vo.notification.NotificationVO;

import java.util.List;
import java.util.Map;

/** 站内通知及订阅投递业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface NotificationService {

    /**
     * 查询当前会话所属账号的站内通知；不暴露其他账号的消息。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @return 按创建时间倒序的最近 100 条站内通知
     */
    List<NotificationVO> list(String token);

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
     * <p>由受 M08 开关控制的投递工作器调用；数据库负责领取及租约，网络发送在事务之外。
     *
     * @param limit 本批领取数量，约束为 1 至 100
     * @return 已领取的 outbox 投递记录，包含工作器发送所需字段
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
