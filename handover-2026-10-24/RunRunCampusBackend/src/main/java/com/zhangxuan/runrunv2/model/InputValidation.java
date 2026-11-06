package com.zhangxuan.runrunv2.model;

import com.zhangxuan.runrunv2.exception.IdentityFailure;




public final class InputValidation {

    private InputValidation() {
    }

    public static void text(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max)
            throw IdentityFailure.invalid();
    }
}
