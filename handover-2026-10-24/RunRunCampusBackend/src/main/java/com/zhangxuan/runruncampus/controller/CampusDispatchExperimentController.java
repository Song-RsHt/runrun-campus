package com.zhangxuan.runruncampus.controller;

import com.zhangxuan.runruncampus.dto.DispatchExperimentRequest;
import com.zhangxuan.runruncampus.enums.AdminPermission;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.service.CampusDispatchExperimentService;
import com.zhangxuan.runruncampus.vo.DispatchExperimentVO;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 校园派单策略离线比较 HTTP 入口；绑定请求并委托业务服务，不直接访问数据库。 */
@RestController
@RequestMapping("/api/v1/admin/dispatch-experiments")
public class CampusDispatchExperimentController {

    private final AuthService auth;
    private final CampusDispatchExperimentService service;

    public CampusDispatchExperimentController(
            AuthService auth, CampusDispatchExperimentService service) {
        this.auth = auth;
        this.service = service;
    }

    @PostMapping
    public DispatchExperimentVO compare(
            @RequestHeader("Authorization") String authorization,
            @RequestBody DispatchExperimentRequest request) {
        auth.requirePermission(authorization, AdminPermission.ORDER_DISPATCH);
        return service.compare(request);
    }
}
