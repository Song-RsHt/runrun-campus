package com.zhangxuan.runrunv2.service.impl.notification;

import com.zhangxuan.runrunv2.mapper.notification.NotificationMapper;
import com.zhangxuan.runrunv2.service.identity.IdentityService;
import com.zhangxuan.runrunv2.service.notification.NotificationService;
import com.zhangxuan.runrunv2.vo.notification.NotificationVO;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** 站内通知及订阅投递业务实现；通过注入的业务契约及数据访问组件执行，事务边界保留在本类。 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper mapper;
    private final IdentityService identity;

    public NotificationServiceImpl(NotificationMapper mapper, IdentityService identity) {
        this.mapper = mapper;
        this.identity = identity;
    }

    @Transactional(readOnly = true)
    @Override
    public List<NotificationVO> list(String token) {
        long user = Long.parseLong(identity.authenticate(token).id());
        return mapper.findUserNotifications(user);
    }

    @Transactional
    @Override
    public void markRead(String token, long id) {
        long user = Long.parseLong(identity.authenticate(token).id());
        mapper.markNotificationRead(id, user);
    }

    @Transactional
    @Override
    public List<Map<String, Object>> claim(int limit) {
        return mapper.claimOutboxDeliveries(Math.max(1, Math.min(limit, 100)));
    }

    @Transactional
    @Override
    public void complete(long id, boolean success, String error) {
        mapper.completeDelivery(id, success, error);
    }
}
