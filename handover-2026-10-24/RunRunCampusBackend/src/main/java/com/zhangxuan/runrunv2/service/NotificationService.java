package com.zhangxuan.runrunv2.service;

import java.util.List;
import java.util.Map;








/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface NotificationService {

    List<Map<String, Object>> list(String token);

    void markRead(String token, long id);

    List<Map<String, Object>> claim(int limit);

    void complete(long id, boolean success, String error);
}
