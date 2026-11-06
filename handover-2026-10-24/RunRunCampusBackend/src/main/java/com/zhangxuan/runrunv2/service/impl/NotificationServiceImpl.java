package com.zhangxuan.runrunv2.service.impl;

import com.zhangxuan.runrunv2.mapper.NotificationMapper;
import com.zhangxuan.runrunv2.service.IdentityService;
import com.zhangxuan.runrunv2.service.NotificationService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;










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
    public List<Map<String, Object>> list(String token) {
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
