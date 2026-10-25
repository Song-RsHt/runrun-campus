package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.auth.AuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dispatch-experiments")
public class CampusDispatchExperimentController {
    private final AuthService auth;
    private final CampusDispatchExperimentService service;

    public CampusDispatchExperimentController(AuthService auth, CampusDispatchExperimentService service) {
        this.auth = auth;
        this.service = service;
    }

    @PostMapping
    CampusDispatchExperimentService.Result compare(@RequestHeader("Authorization") String authorization,
                                                    @RequestBody CampusDispatchExperimentService.Request request) {
        auth.requirePermission(authorization, AdminPermission.ORDER_DISPATCH);
        return service.compare(request);
    }
}
