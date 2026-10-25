package com.zhangxuan.runruncampus.auth;

import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {
    private final UserRepository userRepository;
    private final int maximumAttempts;
    private final long lockSeconds;
    private final int captchaAfterAttempts;
    private final Map<String, Integer> unknownAccountFailures = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptService(UserRepository userRepository,
                               @Value("${app.login-security.maximum-attempts:5}") int maximumAttempts,
                               @Value("${app.login-security.lock-minutes:15}") long lockMinutes,
                               @Value("${app.login-security.captcha-after-attempts:3}") int captchaAfterAttempts) {
        this.userRepository = userRepository;
        this.maximumAttempts = maximumAttempts;
        this.lockSeconds = lockMinutes * 60;
        this.captchaAfterAttempts = captchaAfterAttempts;
    }

    LoginAttemptService(UserRepository userRepository, int maximumAttempts, long lockMinutes) {
        this(userRepository, maximumAttempts, lockMinutes, Math.min(3, maximumAttempts));
    }

    public boolean requiresCaptcha(String phone) {
        return userRepository.findByPhone(phone)
                .map(user -> user.getFailedLoginAttempts() >= captchaAfterAttempts)
                .orElseGet(() -> unknownAccountFailures.getOrDefault(phone, 0) >= captchaAfterAttempts);
    }

    public void recordUnknownFailure(String phone) {
        if (unknownAccountFailures.size() >= 10_000 && !unknownAccountFailures.containsKey(phone)) {
            unknownAccountFailures.clear();
        }
        unknownAccountFailures.merge(phone, 1, (current, increment) -> Math.min(maximumAttempts, current + increment));
    }

    public void clearTransientFailures(String phone) {
        unknownAccountFailures.remove(phone);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long userId) {
        AppUser user = userRepository.findLockedById(userId).orElse(null);
        if (user == null || user.isLoginLocked(Instant.now())) return;
        user.recordLoginFailure(maximumAttempts, Instant.now().plusSeconds(lockSeconds));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearFailures(Long userId) {
        AppUser user = userRepository.findLockedById(userId).orElse(null);
        if (user != null && (user.getFailedLoginAttempts() > 0 || user.getLoginLockedUntil() != null)) {
            user.clearLoginFailures();
        }
    }
}
