package com.zhangxuan.runruncampus.v2.foundation;

import java.lang.annotation.*;

/** Mark v2 controllers so legacy message-only contracts are preserved. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface V2Api {}
