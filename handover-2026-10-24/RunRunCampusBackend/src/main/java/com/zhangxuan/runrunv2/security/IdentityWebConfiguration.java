package com.zhangxuan.runrunv2.security;

import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.service.IdentityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;














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
    registry
        .addInterceptor(
            new HandlerInterceptor() {

              @Override
              public boolean preHandle(
                  HttpServletRequest request, HttpServletResponse response, Object handler) {
                // All identity responses, especially bearer credentials, must stay out of caches.
                response.setHeader("Cache-Control", "no-store");
                response.setHeader("Pragma", "no-cache");
                if (!(handler instanceof HandlerMethod method)) {
                  // Disabled simulator routes must look absent, including before login.
                  // Other undeclared v2 handlers retain the default-deny identity policy.
                  if (request
                      .getRequestURI()
                      .matches("/api/v2/test/(payment-attempts|refunds)/[^/]+/result"))
                    throw new IdentityFailure(404, "RESOURCE_NOT_FOUND");
                  throw new IdentityFailure(403, "FORBIDDEN");
                }
                IdentityPermission permission =
                    AnnotatedElementUtils.findMergedAnnotation(
                        method.getMethod(), IdentityPermission.class);
                if (permission == null)
                  permission =
                      AnnotatedElementUtils.findMergedAnnotation(
                          method.getBeanType(), IdentityPermission.class);
                // New v2 handlers must declare their policy explicitly before becoming reachable.
                if (permission == null) throw new IdentityFailure(403, "FORBIDDEN");
                if (permission.anonymous()) {
                  throttle(request);
                } else if (permission.role().isEmpty()) identity.authenticate(bearer(request));
                else
                  identity.requireRole(
                      bearer(request), permission.role(), permission.courierEligible());
                return true;
              }
            })
        .addPathPatterns("/api/v2/**");
  }

  private void throttle(HttpServletRequest request) {
    // Per-process abuse bound; reverse proxy must also limit traffic for multi-instance deployment.
    long now = System.currentTimeMillis();
    attempts.entrySet().removeIf(e -> now - e.getValue().start > 900_000);
    if (attempts.size() >= 4096) throw IdentityFailure.auth();
    String key =
        secrets.hash("login-throttle", request.getRemoteAddr() + ":" + request.getRequestURI());
    Window window =
        attempts.compute(
            key,
            (k, old) ->
                old == null || now - old.start > 900_000
                    ? new Window(now, 1)
                    : new Window(old.start, old.count + 1));
    int limit = request.getRequestURI().endsWith("/password/login") ? 8 : 60;
    if (window.count > limit) throw IdentityFailure.auth();
  }

  public static String bearer(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header == null || !header.startsWith("Bearer ")) throw IdentityFailure.auth();
    return header.substring(7);
  }
}
