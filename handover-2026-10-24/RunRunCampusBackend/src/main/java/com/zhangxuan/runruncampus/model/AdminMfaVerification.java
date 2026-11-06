package com.zhangxuan.runruncampus.model;

import com.zhangxuan.runruncampus.vo.AdminMfaChallengeVO;
import com.zhangxuan.runruncampus.vo.AuthResponse;
import java.util.List;






























public record AdminMfaVerification(AuthResponse session, List<String> recoveryCodes, AdminMfaChallengeVO challenge) {
}
