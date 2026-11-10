package com.zhangxuan.runruncampus.dto.auth;

import com.zhangxuan.runruncampus.constant.privacy.PrivacyConstants;
import com.zhangxuan.runruncampus.enums.user.UserRole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 登记请求参数；保留入口的字段、构造器及校验契约。 */
public record RegisterRequest(
        @Pattern(regexp = "^1\\d{10}$", message = "请输入 11 位手机号") String phone,
        @Size(min = 6, max = 72, message = "密码长度应为 6 到 72 位") String password,
        @NotBlank(message = "请输入昵称") @Size(max = 48, message = "昵称过长") String displayName,
        UserRole role,
        @Size(max = 16) String clientType,
        @Size(max = 80) String deviceName,
        @NotBlank(message = "请先同意隐私政策") @Size(max = 32) String privacyPolicyVersion) {

    public RegisterRequest(String phone, String password, String displayName, UserRole role) {
        this(phone, password, displayName, role, null, null, PrivacyConstants.CURRENT_VERSION);
    }

    public RegisterRequest {
        if (role == null) {
            role = UserRole.CUSTOMER;
        }
    }
}
