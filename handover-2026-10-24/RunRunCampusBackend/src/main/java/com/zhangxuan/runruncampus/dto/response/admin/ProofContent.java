package com.zhangxuan.runruncampus.dto.response.admin;

/** Internal binary response payload; never exposes a storage path. */
public record ProofContent(String contentType, byte[] bytes) {}
