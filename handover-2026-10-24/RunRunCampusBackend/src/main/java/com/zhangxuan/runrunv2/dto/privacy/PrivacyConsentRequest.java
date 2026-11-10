package com.zhangxuan.runrunv2.dto.privacy;

/** 用户明确同意的协议版本，服务端只接受当前版本。 */
public record PrivacyConsentRequest(String version) {}
