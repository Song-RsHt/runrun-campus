package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.model.AdminMfaVerification;
import com.zhangxuan.runruncampus.model.SessionMetadata;
import com.zhangxuan.runruncampus.vo.AdminMfaChallengeVO;
































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
