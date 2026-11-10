package com.zhangxuan.runruncampus.model.courier;

/** 资质图片；仅承载内部协作所需的数据或校验逻辑。 */
public record CertificationPhoto(byte[] bytes, String contentType) {}
