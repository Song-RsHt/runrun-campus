package com.zhangxuan.runruncampus.auth;

import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.zhangxuan.runruncampus.user.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(1)
public class AdminAccountInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String phone;
    private final String password;
    private final String displayName;

    public AdminAccountInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                   @Value("${app.admin.phone:}") String phone,
                                   @Value("${app.admin.password:}") String password,
                                   @Value("${app.admin.display-name:管理员}") String displayName) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.phone = phone;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (phone.isBlank() || password.isBlank()) return;
        userRepository.findByPhone(phone).ifPresentOrElse(existing -> {
            if (existing.getRole() != UserRole.ADMIN) {
                log.warn("Configured admin phone already belongs to a non-admin account");
            }
        }, () -> userRepository.save(new AppUser(phone, passwordEncoder.encode(password), displayName, UserRole.ADMIN)));
    }
}
