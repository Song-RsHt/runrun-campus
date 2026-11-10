package com.zhangxuan.runrunv2.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/** 本地管理端预览跨域，预检必须先于身份拦截执行。 */
@Configuration
@Profile("local-v2-test")
public class LocalTestCors {

    @Bean
    public FilterRegistrationBean<CorsFilter> localTestCorsFilter(
            @Value("${rrc.local.admin-origin:http://127.0.0.1:5173}") String origin) {
        if (!origin.matches("http://(127[.]0[.]0[.]1|localhost):[0-9]{1,5}")
                || Integer.parseInt(origin.substring(origin.lastIndexOf(':') + 1)) > 65535
                || Integer.parseInt(origin.substring(origin.lastIndexOf(':') + 1)) < 1) {
            throw new IllegalArgumentException(
                    "Local admin origin must be one exact loopback HTTP origin");
        }
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(origin));
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
