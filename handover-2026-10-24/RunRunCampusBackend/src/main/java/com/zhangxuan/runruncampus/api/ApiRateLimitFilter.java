package com.zhangxuan.runruncampus.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiRateLimitFilter extends OncePerRequestFilter {
    private static final Policy AUTH = new Policy("auth", 0, 30);
    private static final Policy PAYMENT_CALLBACK = new Policy("payment-callback", 0, 6_000);
    private static final Policy PROOF = new Policy("proof", 12, 120);
    private static final Policy WRITE = new Policy("write", 60, 600);
    private static final Policy READ = new Policy("read", 180, 1_200);

    private final ApiRateLimitService rateLimitService;

    public ApiRateLimitFilter(ApiRateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/")
                || HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/v1/auth/admin/") || request.getRequestURI().contains("/mfa/")
                || request.getRequestURI().equals("/api/v1/auth/password")
                || request.getRequestURI().startsWith("/api/v1/payments/")) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        }
        Policy policy = policy(request);
        String remoteAddress = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        if (!rateLimitService.tryAcquire(policy.scope + "-ip", remoteAddress, policy.perIp)) {
            reject(response);
            return;
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (policy.perCredential > 0 && authorization != null && !authorization.isBlank()
                && !rateLimitService.tryAcquire(policy.scope + "-credential", authorization, policy.perCredential)) {
            reject(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Policy policy(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/v1/auth/")) return AUTH;
        if (uri.startsWith("/api/v1/payments/wechat/") && uri.endsWith("notify")) return PAYMENT_CALLBACK;
        if (HttpMethod.POST.matches(request.getMethod()) && uri.contains("/proofs/")) return PROOF;
        if (!HttpMethod.GET.matches(request.getMethod()) && !HttpMethod.HEAD.matches(request.getMethod())) return WRITE;
        return READ;
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, "60");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"请求过于频繁，请稍后再试\"}");
    }

    private record Policy(String scope, int perCredential, int perIp) { }
}
