package com.zhangxuan.runruncampus.exception;

import com.zhangxuan.runruncampus.handler.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.assertEquals;










class ApiExceptionHandlerTest {

  @Test
  void oversizedMultipartRequestReturnsPayloadTooLarge() {
    var response =
        new ApiExceptionHandler()
            .handleUploadTooLarge(new MaxUploadSizeExceededException(8L * 1024 * 1024));
    assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
    assertEquals("凭证图片不能超过 8MB", response.getBody().get("message"));
  }

  @Test
  void rateLimitedServiceResponseIncludesGenericRetryHeaders() {
    var response =
        new ApiExceptionHandler()
            .handleStatus(
                new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试"));
    assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
    assertEquals("60", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
  }

  /** 业务异常改用中立类型后，逐个核对原 HTTP 状态、正文与限流响应头。 */
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(ints = {400, 401, 403, 404, 409, 428, 429, 500})
  void businessFailurePreservesExistingHttpContract(int status) {
    var handler = new ApiExceptionHandler();
    var previous = handler.handleStatus(new ResponseStatusException(HttpStatus.valueOf(status), "测试失败提示"));
    var actual = handler.handleBusiness(new BusinessFailure(status, "测试失败提示"));
    assertEquals(previous.getStatusCode(), actual.getStatusCode());
    assertEquals(previous.getBody(), actual.getBody());
    assertEquals(previous.getHeaders(), actual.getHeaders());
  }

  @Test
  void businessFailurePreservesFallbackMessage() {
    var response = new ApiExceptionHandler().handleBusiness(new BusinessFailure(400));
    assertEquals("请求失败", response.getBody().get("message"));
  }
}
