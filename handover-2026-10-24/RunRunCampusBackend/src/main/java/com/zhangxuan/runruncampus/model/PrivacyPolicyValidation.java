package com.zhangxuan.runruncampus.model;

import com.zhangxuan.runruncampus.constant.PrivacyConstants;
import com.zhangxuan.runruncampus.exception.BusinessFailure;


























public final class PrivacyPolicyValidation {

    private PrivacyPolicyValidation() {
    }

    public static void requireCurrentVersion(String version) {
        if (!PrivacyConstants.CURRENT_VERSION.equals(version)) {
            throw new BusinessFailure(409, "隐私政策已更新，请阅读最新版本后重试");
        }
    }
}
