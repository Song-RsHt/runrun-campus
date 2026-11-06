package com.zhangxuan.runrunv2.integration.wechat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangxuan.runrunv2.exception.IdentityFailure;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WechatHttpGatewayTest {

  private HttpResponse<String> response(String body) {
    return new HttpResponse<>() {

      public int statusCode() {
        return 200;
      }

      public String body() {
        return body;
      }

      public HttpRequest request() {
        return null;
      }

      public Optional<HttpResponse<String>> previousResponse() {
        return java.util.Optional.empty();
      }

      public HttpHeaders headers() {
        return HttpHeaders.of(java.util.Map.of(), (k, v) -> true);
      }

      public Optional<javax.net.ssl.SSLSession> sslSession() {
        return java.util.Optional.empty();
      }

      public URI uri() {
        return java.net.URI.create("https://api.weixin.qq.com");
      }

      public HttpClient.Version version() {
        return HttpClient.Version.HTTP_1_1;
      }
    };
  }

  private WechatHttpGateway gateway(HttpClient http) {
    return new WechatHttpGateway("synthetic-app", "synthetic-secret", new ObjectMapper(), http);
  }

  @Test
  void exchangesLoginOnlyAtWechatAndReturnsOnlyOpenid() throws Exception {
    HttpClient http = mock(HttpClient.class);
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(response("{\"openid\":\"synthetic-openid\",\"session_key\":\"discarded\"}"));
    assertEquals("synthetic-openid", gateway(http).exchangeLoginCode("code & value"));
    var captured = ArgumentCaptor.forClass(HttpRequest.class);
    verify(http).send(captured.capture(), any(HttpResponse.BodyHandler.class));
    assertEquals("api.weixin.qq.com", captured.getValue().uri().getHost());
    assertEquals("/sns/jscode2session", captured.getValue().uri().getPath());
    assertEquals("GET", captured.getValue().method());
    assertTrue(captured.getValue().uri().getRawQuery().contains("js_code=code+%26+value"));
  }

  @Test
  void phoneExchangeUsesStableTokenAndChecksWatermarkWhileCachingAccessToken() throws Exception {
    HttpClient http = mock(HttpClient.class);
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(
            response("{\"access_token\":\"synthetic-access-token\",\"expires_in\":7200}"),
            response(
                "{\"errcode\":0,\"phone_info\":{\"purePhoneNumber\":\"13800000001\",\"countryCode\":\"86\",\"watermark\":{\"appid\":\"synthetic-app\"}}}"));
    var gateway = gateway(http);
    assertEquals("13800000001", gateway.exchangePhoneCode("phone-code"));
    assertEquals("13800000001", gateway.exchangePhoneCode("another-code"));
    var captured = ArgumentCaptor.forClass(HttpRequest.class);
    verify(http, times(3)).send(captured.capture(), any(HttpResponse.BodyHandler.class));
    assertEquals("/cgi-bin/stable_token", captured.getAllValues().get(0).uri().getPath());
    assertEquals(
        "/wxa/business/getuserphonenumber", captured.getAllValues().get(1).uri().getPath());
    assertTrue(captured.getAllValues().stream().allMatch(r -> "POST".equals(r.method())));
  }

  @Test
  void rejectsUpstreamCodeFailureMissingOpenidAndTransportDiagnostics() throws Exception {
    for (String body :
        new String[] {
          "{\"errcode\":40029,\"errmsg\":\"synthetic-error\"}", "{}", "null", "invalid-json"
        }) {
      HttpClient http = mock(HttpClient.class);
      when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
          .thenReturn(response(body));
      var error = assertThrows(IdentityFailure.class, () -> gateway(http).exchangeLoginCode("bad"));
      assertEquals("WECHAT_AUTH_FAILED", error.code);
      assertNull(error.getCause());
    }
    HttpClient http = mock(HttpClient.class);
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new IOException("sensitive upstream diagnostics"));
    var error = assertThrows(IdentityFailure.class, () -> gateway(http).exchangeLoginCode("bad"));
    assertFalse(error.toString().contains("sensitive"));
  }

  @Test
  void refusesDifferentAppOrInvalidPhone() throws Exception {
    for (String info :
        new String[] {
          "{\"purePhoneNumber\":\"13800000001\",\"countryCode\":\"86\",\"watermark\":{\"appid\":\"other-app\"}}",
          "{\"purePhoneNumber\":\"13800000001\",\"countryCode\":\"1\",\"watermark\":{\"appid\":\"synthetic-app\"}}",
          "{\"purePhoneNumber\":\"invalid\",\"countryCode\":\"86\",\"watermark\":{\"appid\":\"synthetic-app\"}}"
        }) {
      HttpClient http = mock(HttpClient.class);
      when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
          .thenReturn(
              response("{\"access_token\":\"synthetic\",\"expires_in\":7200}"),
              response("{\"phone_info\":" + info + "}"));
      assertEquals(
          "WECHAT_AUTH_FAILED",
          assertThrows(IdentityFailure.class, () -> gateway(http).exchangePhoneCode("code")).code);
    }
  }
}
