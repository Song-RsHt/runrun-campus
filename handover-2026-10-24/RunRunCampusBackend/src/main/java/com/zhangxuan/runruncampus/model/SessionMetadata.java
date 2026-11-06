package com.zhangxuan.runruncampus.model;

import com.zhangxuan.runruncampus.constant.PrivacyConstants;
import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.Session;
import com.zhangxuan.runruncampus.enums.SessionClientType;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.enums.admin.AdminPermission;
import com.zhangxuan.runruncampus.repository.SessionRepository;
import com.zhangxuan.runruncampus.repository.UserRepository;
import com.zhangxuan.runruncampus.service.LoginAttemptService;
import com.zhangxuan.runruncampus.service.LoginCaptchaService;
import com.zhangxuan.runruncampus.vo.SessionInfoVO;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

public record SessionMetadata(SessionClientType clientType, String deviceName) {

    public static SessionMetadata of(String clientType, String deviceName) {
        SessionClientType type = SessionClientType.from(clientType);
        String normalizedName = deviceName == null || deviceName.isBlank() ? "未知设备" : deviceName;
        return new SessionMetadata(type, normalizedName);
    }

    public static SessionMetadata unknown() {
        return new SessionMetadata(SessionClientType.UNKNOWN, "未知设备");
    }
}
