package com.zhangxuan.runrunv2;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="rrc.identity.wechat-enabled", havingValue="true", matchIfMissing=true)
public class WechatHttpGateway implements WechatGateway {
    private final String appId;
    private final String appSecret;
    private final ObjectMapper json;
    private final HttpClient http;
    private String accessToken;
    private Instant tokenExpires = Instant.EPOCH;
    @org.springframework.beans.factory.annotation.Autowired
    public WechatHttpGateway(@Value("${rrc.identity.app-id}") String appId,
                             @Value("${rrc.identity.app-secret}") String secret, ObjectMapper json) {
        this(appId, secret, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
    }
    WechatHttpGateway(String appId, String secret, ObjectMapper json, HttpClient http) {
        if (appId.isBlank() || secret.isBlank()) throw new IllegalStateException("Wechat credentials required");
        this.appId = appId; this.appSecret = secret; this.json = json; this.http = http;
    }
    @Override public String exchangeLoginCode(String code) {
        JsonNode result = send("/sns/jscode2session?appid=" + esc(appId) + "&secret=" + esc(appSecret)
                + "&js_code=" + esc(code) + "&grant_type=authorization_code", null);
        String openid = result.path("openid").asText();
        if (openid.isBlank()) throw failure();
        // session_key is deliberately neither persisted nor exposed.
        return openid;
    }
    @Override public String exchangePhoneCode(String code) {
        JsonNode result = send("/wxa/business/getuserphonenumber?access_token=" + esc(token()), Map.of("code", code));
        JsonNode info = result.path("phone_info");
        String phone = info.path("purePhoneNumber").asText();
        if (!"86".equals(info.path("countryCode").asText()) || !phone.matches("1[3-9][0-9]{9}")
                || !appId.equals(info.path("watermark").path("appid").asText())) throw failure();
        return phone;
    }
    private synchronized String token() {
        if (Instant.now().isBefore(tokenExpires)) return accessToken;
        JsonNode result = send("/cgi-bin/stable_token", Map.of("grant_type", "client_credential", "appid", appId, "secret", appSecret));
        String next = result.path("access_token").asText();
        long ttl = result.path("expires_in").asLong();
        if (next.isBlank() || ttl < 120) throw failure();
        accessToken = next;
        tokenExpires = Instant.now().plusSeconds(ttl - 60);
        return accessToken;
    }
    private JsonNode send(String path, Map<String, String> body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("https://api.weixin.qq.com" + path)).timeout(Duration.ofSeconds(10));
            if (body == null) request.GET();
            else request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw failure();
            JsonNode result = json.readTree(response.body());
            if (result == null || result.path("errcode").asInt(0) != 0) throw failure();
            return result;
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw failure(); }
        catch (Exception e) { throw failure(); } // Never retain upstream URLs, codes or response bodies in exceptions/logs.
    }
    private static String esc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static IdentityFailure failure() { return new IdentityFailure(401, "WECHAT_AUTH_FAILED"); }
}
