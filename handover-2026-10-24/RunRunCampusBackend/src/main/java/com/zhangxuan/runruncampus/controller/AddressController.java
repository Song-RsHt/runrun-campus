package com.zhangxuan.runruncampus.controller;

import com.zhangxuan.runruncampus.dto.AddressRequest;
import com.zhangxuan.runruncampus.service.AddressService;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.vo.AddressResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 收件地址 HTTP 入口；绑定请求并委托业务服务，不直接访问数据库。 */
@RestController
@RequestMapping("/api/v1/addresses")
public class AddressController {

    private final AuthService authService;
    private final AddressService addressService;

    public AddressController(AuthService authService, AddressService addressService) {
        this.authService = authService;
        this.addressService = addressService;
    }

    @GetMapping
    public List<AddressResponse> list(@RequestHeader("Authorization") String authorization) {
        return addressService.list(authService.requireUser(authorization));
    }

    @PostMapping
    public AddressResponse create(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody AddressRequest request) {
        return addressService.create(
                authService.requireUser(authorization),
                request.label(),
                request.address(),
                request.deliveryZone(),
                request.defaultAddress());
    }

    @PutMapping("/{id}")
    public AddressResponse update(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id,
            @Valid @RequestBody AddressRequest request) {
        return addressService.update(
                authService.requireUser(authorization),
                id,
                request.label(),
                request.address(),
                request.deliveryZone(),
                request.defaultAddress());
    }

    @PostMapping("/{id}/default")
    public AddressResponse setDefault(
            @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return addressService.setDefault(authService.requireUser(authorization), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        addressService.delete(authService.requireUser(authorization), id);
    }
}
