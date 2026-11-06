package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.Session;
import com.zhangxuan.runruncampus.enums.AdminPermission;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.model.SessionMetadata;
import com.zhangxuan.runruncampus.vo.SessionInfoVO;
import java.util.List;

































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
