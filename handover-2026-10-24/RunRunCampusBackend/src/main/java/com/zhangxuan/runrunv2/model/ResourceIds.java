package com.zhangxuan.runrunv2.model;

import com.zhangxuan.runrunv2.exception.IdentityFailure;

/** Existing positive BIGINT identifier validation shared across v2 layer boundaries. */
public final class ResourceIds {
    private ResourceIds() {}

    public static long parse(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw IdentityFailure.invalid();
        try { return Long.parseLong(value); }
        catch (NumberFormatException exception) { throw IdentityFailure.invalid(); }
    }
}
