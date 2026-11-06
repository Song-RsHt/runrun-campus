package com.zhangxuan.runrunv2.integration.wechat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangxuan.runrunv2.exception.IdentityFailure;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.util.Map;
import java.util.Optional;

import javax.net.ssl.SSLSession;

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
                return Optional.empty();
            }

            public HttpHeaders headers() {
                return HttpHeaders.of(Map.of(), (k, v) -> true);
            }

            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            public URI uri() {
                return URI.create("https://api.weixin.qq.com");
            }

            public Version version() {
                return Version.HTTP_1_1;
            }
        };
    }

    private WechatHttpGateway gateway(HttpClient http) {
        return new WechatHttpGateway("synthetic-app", "synthetic-secret", new ObjectMapper(), http);
    }

    @Test
    void exchangesLoginOnlyAtWechatAndReturnsOnlyOpenid() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(BodyHandler.class)))
                .thenReturn(
                        response(
                                "{\"openid\":\"synthetic-openid\",\"session_key\":\"discarded\"}"));
        assertEquals("synthetic-openid", gateway(http).exchangeLoginCode("code & value"));
        var captured = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captured.capture(), any(BodyHandler.class));
        assertEquals("api.weixin.qq.com", captured.getValue().uri().getHost());
        assertEquals("/sns/jscode2session", captured.getValue().uri().getPath());
        assertEquals("GET", captured.getValue().method());
        assertTrue(captured.getValue().uri().getRawQuery().contains("js_code=code+%26+value"));
    }

    @Test
    void phoneExchangeUsesStableTokenAndChecksWatermarkWhileCachingAccessToken() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(BodyHandler.class)))
                .thenReturn(
                        response(
                                "{\"access_token\":\"synthetic-access-token\",\"expires_in\":7200}"),
                        response(
                                "{\"errcode\":0,\"phone_info\":{\"purePhoneNumber\":\"13800000001\",\"countryCode\":\"86\",\"watermark\":{\"appid\":\"synthetic-app\"}}}"));
        var gateway = gateway(http);
        assertEquals("13800000001", gateway.exchangePhoneCode("phone-code"));
        assertEquals("13800000001", gateway.exchangePhoneCode("another-code"));
        var captured = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(3)).send(captured.capture(), any(BodyHandler.class));
        assertEquals("/cgi-bin/stable_token", captured.getAllValues().get(0).uri().getPath());
        assertEquals(
                "/wxa/business/getuserphonenumber", captured.getAllValues().get(1).uri().getPath());
        assertTrue(captured.getAllValues().stream().allMatch(r -> "POST".equals(r.method())));
    }

    @Test
    void rejectsUpstreamCodeFailureMissingOpenidAndTransportDiagnostics() throws Exception {
        for (String body :
                new String[] {
                    "{\"errcode\":40029,\"errmsg\":\"synthetic-error\"}",
                    "{}",
                    "null",
                    "invalid-json"
                }) {
            HttpClient http = mock(HttpClient.class);
            when(http.send(any(HttpRequest.class), any(BodyHandler.class)))
                    .thenReturn(response(body));
            var error =
                    assertThrows(
                            IdentityFailure.class, () -> gateway(http).exchangeLoginCode("bad"));
            assertEquals("WECHAT_AUTH_FAILED", error.code);
            assertNull(error.getCause());
        }
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(BodyHandler.class)))
                .thenThrow(new IOException("sensitive upstream diagnostics"));
        var error =
                assertThrows(IdentityFailure.class, () -> gateway(http).exchangeLoginCode("bad"));
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
            when(http.send(any(HttpRequest.class), any(BodyHandler.class)))
                    .thenReturn(
                            response("{\"access_token\":\"synthetic\",\"expires_in\":7200}"),
                            response("{\"phone_info\":" + info + "}"));
            assertEquals(
                    "WECHAT_AUTH_FAILED",
                    assertThrows(
                                    IdentityFailure.class,
                                    () -> gateway(http).exchangePhoneCode("code"))
                            .code);
        }
    }
}
