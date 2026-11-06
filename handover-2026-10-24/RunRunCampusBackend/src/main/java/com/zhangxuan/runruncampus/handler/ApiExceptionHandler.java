package com.zhangxuan.runruncampus.handler;

import com.zhangxuan.runruncampus.exception.BusinessFailure;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** 接口异常处理组件；保持当前应用的配置与协作边界。 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException exception) {
        BodyBuilder response = ResponseEntity.status(exception.getStatusCode());
        if (exception.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            response.header(HttpHeaders.RETRY_AFTER, "60");
            response.header(HttpHeaders.CACHE_CONTROL, "no-store");
        }
        return response.body(
                Map.of("message", exception.getReason() == null ? "请求失败" : exception.getReason()));
    }

    /** 保留业务异常原有状态、提示和限流响应头。 */
    @ExceptionHandler(BusinessFailure.class)
    public ResponseEntity<Map<String, String>> handleBusiness(BusinessFailure exception) {
        BodyBuilder response = ResponseEntity.status(exception.status());
        if (exception.status() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            response.header(HttpHeaders.RETRY_AFTER, "60");
            response.header(HttpHeaders.CACHE_CONTROL, "no-store");
        }
        return response.body(
                Map.of("message", exception.getReason() == null ? "请求失败" : exception.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException exception) {
        String message =
                exception.getBindingResult().getFieldErrors().stream()
                        .findFirst()
                        .map(error -> error.getDefaultMessage())
                        .orElse("请求参数不正确");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleUploadTooLarge(
            MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("message", "凭证图片不能超过 8MB"));
    }
}
