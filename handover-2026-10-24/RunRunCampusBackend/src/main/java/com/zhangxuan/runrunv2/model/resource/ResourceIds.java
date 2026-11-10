package com.zhangxuan.runrunv2.model.resource;

import com.zhangxuan.runrunv2.exception.IdentityFailure;

/** 跨 v2 层复用正 BIGINT 标识校验，保持原有资源 ID 契约。 */
public final class ResourceIds {

    private ResourceIds() {}

    public static long parse(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw IdentityFailure.invalid();
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw IdentityFailure.invalid();
        }
    }
}
