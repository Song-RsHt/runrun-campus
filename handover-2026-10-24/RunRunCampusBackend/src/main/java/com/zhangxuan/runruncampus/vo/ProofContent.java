package com.zhangxuan.runruncampus.vo;

/**
 * Internal binary response payload; never exposes a storage path.
 */
public record ProofContent(String contentType, byte[] bytes) {
}
