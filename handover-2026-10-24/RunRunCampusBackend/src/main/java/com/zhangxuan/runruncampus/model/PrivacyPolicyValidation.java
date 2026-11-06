package com.zhangxuan.runruncampus.model;

import com.zhangxuan.runruncampus.constant.PrivacyConstants;
import com.zhangxuan.runruncampus.exception.BusinessFailure;

/** 当前隐私政策的版本、生效日期及正文校验；仅承载内部协作所需的数据或校验逻辑。 */
public final class PrivacyPolicyValidation {

    private PrivacyPolicyValidation() {}

    public static void requireCurrentVersion(String version) {
        if (!PrivacyConstants.CURRENT_VERSION.equals(version)) {
            throw new BusinessFailure(409, "隐私政策已更新，请阅读最新版本后重试");
        }
    }
}
