package com.zhangxuan.runrunv2.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 新系统身份与会话权限组件；保持当前应用的配置与协作边界。 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface IdentityPermission {

    boolean anonymous() default false;

    String role() default "";

    boolean courierEligible() default false;
}
