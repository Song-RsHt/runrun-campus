package com.zhangxuan.runruncampus.model;

/**
 * Internal binary response payload; never exposes a storage path.
 */
public record ProofContent(String contentType, byte[] bytes) {
}
