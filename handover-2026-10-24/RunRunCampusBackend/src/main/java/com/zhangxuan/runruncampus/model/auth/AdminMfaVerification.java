package com.zhangxuan.runruncampus.model.auth;

import com.zhangxuan.runruncampus.vo.auth.AdminMfaChallengeVO;
import com.zhangxuan.runruncampus.vo.auth.AuthResponse;

import java.util.List;

/** 管理员双因素认证核验结果；仅承载内部协作所需的数据或校验逻辑。 */
public record AdminMfaVerification(
        AuthResponse session, List<String> recoveryCodes, AdminMfaChallengeVO challenge) {}
