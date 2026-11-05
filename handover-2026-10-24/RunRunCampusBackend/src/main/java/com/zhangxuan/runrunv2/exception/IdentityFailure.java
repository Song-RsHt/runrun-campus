package com.zhangxuan.runrunv2.exception;

public class IdentityFailure extends RuntimeException {
    final public int status;
    final public String code;
    public IdentityFailure(int status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }
    static public IdentityFailure invalid() { return new IdentityFailure(400, "VALIDATION_ERROR"); }
    static public IdentityFailure auth() { return new IdentityFailure(401, "AUTHENTICATION_FAILED"); }
    static public IdentityFailure conflict() { return new IdentityFailure(409, "IDENTITY_BINDING_CONFLICT"); }
}
