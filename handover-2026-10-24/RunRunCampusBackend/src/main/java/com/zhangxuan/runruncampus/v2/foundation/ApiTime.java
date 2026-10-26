package com.zhangxuan.runruncampus.v2.foundation;

import com.fasterxml.jackson.annotation.*;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonFormat(shape = JsonFormat.Shape.STRING, timezone = "UTC")
public @interface ApiTime {}
