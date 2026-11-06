package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.repository.UserRepository;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface LoginAttemptService {

    boolean requiresCaptcha(String phone);

    void recordUnknownFailure(String phone);

    void clearTransientFailures(String phone);

    void recordFailure(Long userId);

    void clearFailures(Long userId);
}
