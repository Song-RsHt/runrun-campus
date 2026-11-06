package com.zhangxuan.runruncampus.controller.admin;

import com.zhangxuan.runruncampus.dto.admin.OrderLifecycleReasonRequest;
import com.zhangxuan.runruncampus.enums.admin.AdminPermission;
import com.zhangxuan.runruncampus.service.admin.AdminOrderLifecycleService;
import com.zhangxuan.runruncampus.service.auth.AuthService;
import com.zhangxuan.runruncampus.vo.admin.ActionResult;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员订单收尾与归档 HTTP 入口；绑定请求并委托业务服务，不直接访问数据库。 */
@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderLifecycleController {

    private final AuthService auth;
    private final AdminOrderLifecycleService service;

    public AdminOrderLifecycleController(AuthService auth, AdminOrderLifecycleService service) {
        this.auth = auth;
        this.service = service;
    }

    @PostMapping("/{id}/force-complete")
    public ActionResult forceComplete(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id,
            @Valid @RequestBody OrderLifecycleReasonRequest request) {
        service.forceComplete(
                auth.requirePermission(authorization, AdminPermission.ORDER_FORCE_COMPLETE),
                id,
                request.reason());
        return new ActionResult(id, "FORCE_COMPLETED");
    }

    @PostMapping("/{id}/archive")
    public ActionResult archive(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id,
            @Valid @RequestBody OrderLifecycleReasonRequest request) {
        service.archive(
                auth.requirePermission(authorization, AdminPermission.ORDER_ARCHIVE),
                id,
                request.reason());
        return new ActionResult(id, "ARCHIVED");
    }

    @PostMapping("/{id}/unarchive")
    public ActionResult unarchive(
            @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        service.unarchive(auth.requirePermission(authorization, AdminPermission.ORDER_ARCHIVE), id);
        return new ActionResult(id, "UNARCHIVED");
    }
}
