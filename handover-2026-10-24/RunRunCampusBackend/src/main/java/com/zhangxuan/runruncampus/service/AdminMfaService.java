package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AdminMfaCredential;
import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.Session;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.enums.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.AdminAuditTargetType;
import com.zhangxuan.runruncampus.enums.AdminRole;
import com.zhangxuan.runruncampus.model.AdminMfaVerification;
import com.zhangxuan.runruncampus.model.SessionMetadata;
import com.zhangxuan.runruncampus.repository.AdminMfaRepository;
import com.zhangxuan.runruncampus.repository.SessionRepository;
import com.zhangxuan.runruncampus.repository.UserRepository;
import com.zhangxuan.runruncampus.security.AdminMfaCrypto;
import com.zhangxuan.runruncampus.service.AdminAuditService;
import com.zhangxuan.runruncampus.vo.AdminMfaChallengeVO;
import com.zhangxuan.runruncampus.vo.AuthResponse;
import com.zhangxuan.runruncampus.vo.UserResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface AdminMfaService {

    AdminMfaChallengeVO begin(String phone, String password);

    AdminMfaVerification verify(String token, String code);

    AdminMfaVerification verify(String token, String code, SessionMetadata metadata);

    AdminMfaChallengeVO replace(AppUser authenticated, String password, String code);

    String reset(AppUser authenticated, Long targetId, String reason);

    String resetState(AppUser target);
}
