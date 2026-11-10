package com.zhangxuan.runrunv2.model.identity;

import com.zhangxuan.runrunv2.exception.IdentityFailure;

/** 身份输入校验；仅承载内部协作所需的数据或校验逻辑。 */
public final class InputValidation {

    private InputValidation() {}

    public static void text(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw IdentityFailure.invalid();
        }
    }
}
