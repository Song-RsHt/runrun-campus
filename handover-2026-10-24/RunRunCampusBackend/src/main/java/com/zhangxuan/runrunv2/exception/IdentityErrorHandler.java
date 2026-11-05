package com.zhangxuan.runrunv2.exception;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class IdentityErrorHandler {
  @ExceptionHandler({
    org.springframework.web.servlet.resource.NoResourceFoundException.class,
    org.springframework.web.servlet.NoHandlerFoundException.class
  })
  public ResponseEntity<Map<String, String>> notFound(Exception e) {
    return reply(404, "RESOURCE_NOT_FOUND");
  }

  @ExceptionHandler(IdentityFailure.class)
  public ResponseEntity<Map<String, String>> identity(IdentityFailure e) {
    return reply(e.status, e.code);
  }

  // Binding failures are invalid requests under E01, rather than a temporary service outage.
  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MissingRequestHeaderException.class,
    IllegalArgumentException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class,
    org.springframework.web.multipart.support.MissingServletRequestPartException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<Map<String, String>> invalid(Exception e) {
    return reply(400, "VALIDATION_ERROR");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, String>> conflict(Exception e) {
    return reply(409, "IDENTITY_BINDING_CONFLICT");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> unavailable(Exception e) {
    // Database diagnostics can embed bindings and credentials. Never serialize or log raw causes.
    return ResponseEntity.status(503)
        .body(Map.of("code", "IDENTITY_SERVICE_UNAVAILABLE", "message", "身份服务暂不可用"));
  }

  private ResponseEntity<Map<String, String>> reply(int status, String code) {
    String message =
        switch (code) {
          case "WECHAT_AUTH_FAILED" -> "微信授权失败，请重新授权";
          case "IDENTITY_BINDING_CONFLICT" -> "身份绑定冲突，请联系管理员";
          case "AUTHENTICATION_FAILED" -> "认证失败，请重新登录";
          case "ADMIN_ACCESS_MANAGEMENT_FORBIDDEN" -> "请通过受控部署流程管理管理员资格";
          case "V2_ADMIN_NOT_ENABLED" -> "管理员尚未启用或已停用";
          case "COURIER_CAPACITY_EXCEEDED" -> "当前接单名额已满或尚未配置";
          case "PROOF_NOT_READY" -> "凭证尚未就绪或不属于当前任务";
          case "ADJUSTMENT_UNRESOLVED" -> "规格调整尚未结清";
          case "AFTER_SALES_UNRESOLVED" -> "存在未处理异议";
          case "UPLOAD_INVALID" -> "上传文件或票据无效";
          case "COURIER_NOT_APPROVED" -> "骑手尚未审核通过";
          case "VALIDATION_ERROR" -> "请求参数无效";
          case "NEW_ORDER_DISABLED" -> "当前暂停新建订单";
          case "OUTSIDE_BUSINESS_HOURS" -> "请在北京时间09:00至23:00前下单，已有订单仍可操作";
          case "CONFIG_VERSION_CONFLICT" -> "服务配置已更新，请刷新报价";
          case "ORDER_STATE_CONFLICT" -> "订单已更新，请刷新后重试";
          case "IDEMPOTENCY_KEY_CONFLICT" -> "请求标识已用于不同内容";
          case "REVIEW_OWNER_REQUIRED" -> "请先配置有效的值班管理员";
          default -> "当前请求不允许执行";
        };
    return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
  }
}
