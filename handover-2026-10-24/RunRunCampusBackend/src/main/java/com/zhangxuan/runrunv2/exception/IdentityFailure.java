package com.zhangxuan.runrunv2.exception;

/** 新系统身份与会话失败组件；保持当前应用的配置与协作边界。 */
public class IdentityFailure extends RuntimeException {

    public final int status;

    public final String code;

    public IdentityFailure(int status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }

    public static IdentityFailure invalid() {
        return new IdentityFailure(400, "VALIDATION_ERROR");
    }

    public static IdentityFailure auth() {
        return new IdentityFailure(401, "AUTHENTICATION_FAILED");
    }

    public static IdentityFailure conflict() {
        return new IdentityFailure(409, "IDENTITY_BINDING_CONFLICT");
    }
}
