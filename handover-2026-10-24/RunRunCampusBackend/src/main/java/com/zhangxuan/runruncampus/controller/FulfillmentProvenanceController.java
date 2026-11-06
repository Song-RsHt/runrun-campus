package com.zhangxuan.runruncampus.controller;

import com.zhangxuan.runruncampus.dto.FulfillmentProvenanceCreateRequest;
import com.zhangxuan.runruncampus.enums.AdminPermission;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.service.FulfillmentProvenanceService;
import com.zhangxuan.runruncampus.vo.FulfillmentProvenanceEntryVO;
import com.zhangxuan.runruncampus.vo.FulfillmentProvenanceSummaryVO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;



































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
    public FulfillmentProvenanceEntryVO record(@RequestHeader("Authorization") String authorization, @RequestBody FulfillmentProvenanceCreateRequest request) {
        return service.record(auth.requirePermission(authorization, AdminPermission.PROVENANCE_RECORD), request);
    }

    @PostMapping("/{id}/verify")
    public FulfillmentProvenanceEntryVO verify(@RequestHeader("Authorization") String authorization, @PathVariable long id) {
        return service.verify(auth.requirePermission(authorization, AdminPermission.PROVENANCE_VERIFY), id);
    }

    @GetMapping
    public List<FulfillmentProvenanceEntryVO> list(@RequestHeader("Authorization") String authorization, @RequestParam(defaultValue = "0") int page) {
        auth.requirePermission(authorization, AdminPermission.PROVENANCE_VIEW);
        return service.list(page);
    }

    @GetMapping("/summary")
    public FulfillmentProvenanceSummaryVO summary(@RequestHeader("Authorization") String authorization) {
        auth.requirePermission(authorization, AdminPermission.PROVENANCE_VIEW);
        return service.summary();
    }
}
