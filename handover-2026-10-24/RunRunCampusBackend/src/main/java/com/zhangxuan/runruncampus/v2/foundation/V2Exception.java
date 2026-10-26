package com.zhangxuan.runruncampus.v2.foundation;

public final class V2Exception extends RuntimeException {
    private final V2Error error;
    public V2Exception(V2Error error, String message) { super(message); this.error = error; }
    public V2Error error() { return error; }
}
