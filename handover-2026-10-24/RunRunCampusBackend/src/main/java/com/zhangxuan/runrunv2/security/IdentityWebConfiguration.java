package com.zhangxuan.runrunv2.security;

import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.service.IdentityService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.ConcurrentHashMap;

/** 新系统身份与会话Web配置组件；保持当前应用的配置与协作边界。 */
@Configuration
public class IdentityWebConfiguration implements WebMvcConfigurer {

    private final IdentityService identity;
    private final IdentitySecrets secrets;
    private final ConcurrentHashMap<String, Window> attempts = new ConcurrentHashMap<>();

    private record Window(long start, int count) {}

    public IdentityWebConfiguration(IdentityService identity, IdentitySecrets secrets) {
        this.identity = identity;
        this.secrets = secrets;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(
                        new HandlerInterceptor() {

                            @Override
                            public boolean preHandle(
                                    HttpServletRequest request,
                                    HttpServletResponse response,
                                    Object handler) {
                                // 所有身份响应均禁止缓存，尤其是携带访问凭证的响应。
                                response.setHeader("Cache-Control", "no-store");
                                response.setHeader("Pragma", "no-cache");
                                if (!(handler instanceof HandlerMethod method)) {
                                    // 模拟路由关闭时，即使未登录也应表现为不存在。
                                    // 未声明策略的其他 v2 入口仍按默认拒绝处理。
                                    if (request.getRequestURI()
                                            .matches(
                                                    "/api/v2/test/(payment-attempts|refunds)/[^/]+/result")) {
                                        throw new IdentityFailure(404, "RESOURCE_NOT_FOUND");
                                    }
                                    throw new IdentityFailure(403, "FORBIDDEN");
                                }
                                IdentityPermission permission =
                                        AnnotatedElementUtils.findMergedAnnotation(
                                                method.getMethod(), IdentityPermission.class);
                                if (permission == null) {
                                    permission =
                                            AnnotatedElementUtils.findMergedAnnotation(
                                                    method.getBeanType(), IdentityPermission.class);
                                }
                                // 新增 v2 入口必须先显式声明身份策略，才能对外访问。
                                if (permission == null) {
                                    throw new IdentityFailure(403, "FORBIDDEN");
                                }
                                if (permission.anonymous()) {
                                    throttle(request);
                                } else if (permission.role().isEmpty()) {
                                    identity.authenticate(bearer(request));
                                } else {
                                    identity.requireRole(
                                            bearer(request),
                                            permission.role(),
                                            permission.courierEligible());
                                }
                                return true;
                            }
                        })
                .addPathPatterns("/api/v2/**");
    }

    private void throttle(HttpServletRequest request) {
        // 此限制仅覆盖当前进程；多实例部署仍需由反向代理限制流量。
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(e -> now - e.getValue().start > 900_000);
        if (attempts.size() >= 4096) {
            throw IdentityFailure.auth();
        }
        String key =
                secrets.hash(
                        "login-throttle", request.getRemoteAddr() + ":" + request.getRequestURI());
        Window window =
                attempts.compute(
                        key,
                        (k, old) ->
                                old == null || now - old.start > 900_000
                                        ? new Window(now, 1)
                                        : new Window(old.start, old.count + 1));
        int limit = request.getRequestURI().endsWith("/password/login") ? 8 : 60;
        if (window.count > limit) {
            throw IdentityFailure.auth();
        }
    }

    public static String bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw IdentityFailure.auth();
        }
        return header.substring(7);
    }
}
