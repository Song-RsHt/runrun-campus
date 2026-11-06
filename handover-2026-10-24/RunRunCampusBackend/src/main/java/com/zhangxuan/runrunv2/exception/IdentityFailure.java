package com.zhangxuan.runrunv2.exception;



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
