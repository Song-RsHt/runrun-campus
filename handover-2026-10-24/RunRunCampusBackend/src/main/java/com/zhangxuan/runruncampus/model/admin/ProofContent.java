package com.zhangxuan.runruncampus.model.admin;

/** 私有图片的二进制响应载荷；不向调用方暴露存储路径。 */
public record ProofContent(String contentType, byte[] bytes) {}
