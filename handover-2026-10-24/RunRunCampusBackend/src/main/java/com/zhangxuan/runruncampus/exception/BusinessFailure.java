package com.zhangxuan.runruncampus.exception;

/** 旧业务失败；保存既有错误分类和提示，由异常处理器映射 HTTP，业务层不依赖 Web 框架。 */
public class BusinessFailure extends RuntimeException {

    private final int status;
    private final String reason;

    public BusinessFailure(int status) {
        this(status, null, null);
    }

    public BusinessFailure(int status, String reason) {
        this(status, reason, null);
    }

    public BusinessFailure(int status, String reason, Throwable cause) {
        super(reason, cause);
        this.status = status;
        this.reason = reason;
    }

    public int status() {
        return status;
    }

    public String getReason() {
        return reason;
    }
}
