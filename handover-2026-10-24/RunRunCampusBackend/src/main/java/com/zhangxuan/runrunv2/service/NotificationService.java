package com.zhangxuan.runrunv2.service;

import com.zhangxuan.runrunv2.mapper.NotificationMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface NotificationService {

    List<Map<String, Object>> list(String token);

    void markRead(String token, long id);

    List<Map<String, Object>> claim(int limit);

    void complete(long id, boolean success, String error);
}
