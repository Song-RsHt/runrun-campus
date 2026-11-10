package com.zhangxuan.runruncampus.controller.courier;

import com.zhangxuan.runruncampus.dto.courier.CourierAvailabilityRequest;
import com.zhangxuan.runruncampus.service.auth.AuthService;
import com.zhangxuan.runruncampus.service.courier.CourierOperationsService;
import com.zhangxuan.runruncampus.vo.courier.OperationsResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 骑手接单开关 HTTP 入口；绑定请求并委托业务服务，不直接访问数据库。 */
@RestController
@RequestMapping("/api/v1/courier/operations")
public class CourierOperationsController {

    private final AuthService authService;
    private final CourierOperationsService operationsService;

    public CourierOperationsController(
            AuthService authService, CourierOperationsService operationsService) {
        this.authService = authService;
        this.operationsService = operationsService;
    }

    @GetMapping("/me")
    public OperationsResponse overview(@RequestHeader("Authorization") String authorization) {
        return operationsService.overview(authService.requireUser(authorization));
    }

    @PutMapping("/me/availability")
    public OperationsResponse updateAvailability(
            @RequestHeader("Authorization") String authorization,
            @RequestBody CourierAvailabilityRequest request) {
        return operationsService.updateAvailability(
                authService.requireUser(authorization), request.acceptingOrders());
    }
}
