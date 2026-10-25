package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.auth.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/fulfillment-provenance")
public class FulfillmentProvenanceController {
    private final AuthService auth;
    private final FulfillmentProvenanceService service;

    public FulfillmentProvenanceController(AuthService auth, FulfillmentProvenanceService service) {
        this.auth = auth;
        this.service = service;
    }

    @PostMapping
    FulfillmentProvenanceService.Entry record(@RequestHeader("Authorization") String authorization,
                                               @RequestBody FulfillmentProvenanceService.CreateRequest request) {
        return service.record(auth.requirePermission(authorization, AdminPermission.PROVENANCE_RECORD), request);
    }

    @PostMapping("/{id}/verify")
    FulfillmentProvenanceService.Entry verify(@RequestHeader("Authorization") String authorization,
                                               @PathVariable long id) {
        return service.verify(auth.requirePermission(authorization, AdminPermission.PROVENANCE_VERIFY), id);
    }

    @GetMapping
    List<FulfillmentProvenanceService.Entry> list(@RequestHeader("Authorization") String authorization,
                                                  @RequestParam(defaultValue = "0") int page) {
        auth.requirePermission(authorization, AdminPermission.PROVENANCE_VIEW);
        return service.list(page);
    }

    @GetMapping("/summary")
    FulfillmentProvenanceService.Summary summary(@RequestHeader("Authorization") String authorization) {
        auth.requirePermission(authorization, AdminPermission.PROVENANCE_VIEW);
        return service.summary();
    }
}
