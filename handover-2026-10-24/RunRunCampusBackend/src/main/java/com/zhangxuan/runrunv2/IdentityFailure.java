package com.zhangxuan.runrunv2;

public class IdentityFailure extends RuntimeException {
    final int status;
    final String code;
    public IdentityFailure(int status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }
    static IdentityFailure invalid() { return new IdentityFailure(400, "VALIDATION_ERROR"); }
    static IdentityFailure auth() { return new IdentityFailure(401, "AUTHENTICATION_FAILED"); }
    static IdentityFailure conflict() { return new IdentityFailure(409, "IDENTITY_BINDING_CONFLICT"); }
}
