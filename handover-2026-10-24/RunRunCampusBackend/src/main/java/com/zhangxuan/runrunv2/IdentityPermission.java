package com.zhangxuan.runrunv2;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface IdentityPermission {
    boolean anonymous() default false;
    String role() default "";
    boolean courierEligible() default false;
}
