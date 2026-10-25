package com.zhangxuan.runruncampus.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerTest {
    @Test
    void oversizedMultipartRequestReturnsPayloadTooLarge() {
        var response = new ApiExceptionHandler()
                .handleUploadTooLarge(new MaxUploadSizeExceededException(8L * 1024 * 1024));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertEquals("凭证图片不能超过 8MB", response.getBody().get("message"));
    }

    @Test
    void rateLimitedServiceResponseIncludesGenericRetryHeaders() {
        var response = new ApiExceptionHandler().handleStatus(
                new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("60", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
    }
}
