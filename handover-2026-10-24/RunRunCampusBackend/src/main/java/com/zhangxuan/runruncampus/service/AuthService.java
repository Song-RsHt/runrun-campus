package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.constant.PrivacyConstants;
import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.Session;
import com.zhangxuan.runruncampus.enums.SessionClientType;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.enums.admin.AdminPermission;
import com.zhangxuan.runruncampus.model.PrivacyPolicyValidation;
import com.zhangxuan.runruncampus.model.SessionMetadata;
import com.zhangxuan.runruncampus.repository.SessionRepository;
import com.zhangxuan.runruncampus.repository.UserRepository;
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

/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface AuthService {

    Session register(String phone, String password, String displayName, UserRole role);

    Session register(String phone, String password, String displayName, UserRole role, SessionMetadata metadata);

    Session register(String phone, String password, String displayName, UserRole role, SessionMetadata metadata, String privacyPolicyVersion);

    Session login(String phone, String password);

    Session login(String phone, String password, String captchaToken, String captchaCode, SessionMetadata metadata);

    AppUser requireUser(String authorization);

    AppUser requireAdmin(String authorization);

    AppUser requirePermission(String authorization, AdminPermission permission);

    List<SessionInfoVO> sessions(String authorization);

    void revokeSession(String authorization, Long sessionId);

    long revokeOtherSessions(String authorization);

    void logout(String authorization);

    void changeOwnPassword(String authorization, String currentPassword, String newPassword);
}
