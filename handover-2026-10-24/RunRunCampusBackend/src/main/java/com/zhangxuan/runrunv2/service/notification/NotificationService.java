package com.zhangxuan.runrunv2.service.notification;

import com.zhangxuan.runrunv2.mapper.notification.NotificationMapper;
import com.zhangxuan.runrunv2.service.identity.IdentityService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

  private final NotificationMapper mapper;

  private final IdentityService identity;

  public NotificationService(NotificationMapper mapper, IdentityService identity) {
    this.mapper = mapper;
    this.identity = identity;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> list(String token) {
    long user = Long.parseLong(identity.authenticate(token).id());
    return mapper.listRowsUserNotifications(user);
  }

  @Transactional
  public void markRead(String token, long id) {
    long user = Long.parseLong(identity.authenticate(token).id());
    mapper.markReadWriteUserNotifications(id, user);
  }

  @Transactional
  public List<Map<String, Object>> claim(int limit) {
    return mapper.claimRowsClaimNotificationOutbox(Math.max(1, Math.min(limit, 100)));
  }

  @Transactional
  public void complete(long id, boolean success, String error) {
    mapper.completeDelivery(id, success, error);
  }
}
