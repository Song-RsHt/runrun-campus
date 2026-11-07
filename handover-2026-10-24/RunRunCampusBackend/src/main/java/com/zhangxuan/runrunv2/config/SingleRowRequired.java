package com.zhangxuan.runrunv2.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 延续原 JDBC 单行查询契约：零行或多行均失败，单行可含 null。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SingleRowRequired {}
