package com.zhangxuan.runruncampus.v2.foundation;

import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import jakarta.validation.ConstraintViolationException;

@Order(-100)
@RestControllerAdvice(annotations = V2Api.class)
public class V2ExceptionHandler {
    public record ErrorBody(String code, String message) {}
    @ExceptionHandler(V2Exception.class)
    public ResponseEntity<ErrorBody> business(V2Exception e) {
        return ResponseEntity.status(e.error().status()).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(new ErrorBody(e.error().name(), e.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, ConstraintViolationException.class})
    public ResponseEntity<ErrorBody> validation(Exception e) {
        // Do not echo rejected values, parser errors or private payload fragments.
        return business(new V2Exception(V2Error.VALIDATION_ERROR, "请求参数不正确"));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorBody> upload(MaxUploadSizeExceededException e) {
        return business(new V2Exception(V2Error.UPLOAD_INVALID, "图片不能超过8MiB"));
    }
}
