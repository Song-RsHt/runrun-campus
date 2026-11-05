package com.zhangxuan.runrunv2.config;

import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/** Local admin preview is a separate origin; preflight must run before identity interception. */
@Configuration
@Profile("local-v2-test")
public class LocalTestCors {
  @Bean
  public FilterRegistrationBean<CorsFilter> localTestCorsFilter() {
    var cors = new CorsConfiguration();
    cors.setAllowedOrigins(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id"));
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/v2/**", cors);
    var filter = new FilterRegistrationBean<>(new CorsFilter(source));
    filter.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return filter;
  }
}
