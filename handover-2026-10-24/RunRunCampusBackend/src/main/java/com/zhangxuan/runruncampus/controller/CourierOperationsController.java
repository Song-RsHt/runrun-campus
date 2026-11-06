package com.zhangxuan.runruncampus.controller;

import com.zhangxuan.runruncampus.dto.CourierAvailabilityRequest;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.service.CourierOperationsService;
import com.zhangxuan.runruncampus.vo.OperationsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

























@RestController
@RequestMapping("/api/v1/courier/operations")
public class CourierOperationsController {

    private final AuthService authService;

    private final CourierOperationsService operationsService;

    public CourierOperationsController(AuthService authService, CourierOperationsService operationsService) {
        this.authService = authService;
        this.operationsService = operationsService;
    }

    @GetMapping("/me")
    public OperationsResponse overview(@RequestHeader("Authorization") String authorization) {
        return operationsService.overview(authService.requireUser(authorization));
    }

    @PutMapping("/me/availability")
    public OperationsResponse updateAvailability(@RequestHeader("Authorization") String authorization, @RequestBody CourierAvailabilityRequest request) {
        return operationsService.updateAvailability(authService.requireUser(authorization), request.acceptingOrders());
    }
}
