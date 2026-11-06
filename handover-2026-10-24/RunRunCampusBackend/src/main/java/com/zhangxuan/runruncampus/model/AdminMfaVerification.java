package com.zhangxuan.runruncampus.model;

import com.zhangxuan.runruncampus.vo.AdminMfaChallengeVO;
import com.zhangxuan.runruncampus.vo.AuthResponse;

import java.util.List;

/** 管理员双因素认证核验结果；仅承载内部协作所需的数据或校验逻辑。 */
public record AdminMfaVerification(
        AuthResponse session, List<String> recoveryCodes, AdminMfaChallengeVO challenge) {}
