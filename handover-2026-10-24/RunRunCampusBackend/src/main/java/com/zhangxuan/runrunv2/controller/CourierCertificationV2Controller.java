package com.zhangxuan.runrunv2.controller;

import com.zhangxuan.runrunv2.dto.CertificationDecisionRequest;
import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.integration.web.MultipartUploads;
import com.zhangxuan.runrunv2.security.IdentityPermission;
import com.zhangxuan.runrunv2.service.CourierCertificationV2Service;
import com.zhangxuan.runrunv2.service.IdentityService;
import com.zhangxuan.runrunv2.vo.CourierCertificationVO;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;














































@RestController
@RequestMapping("/api/v2/courier/certification")
public class CourierCertificationV2Controller {

    private final IdentityService identity;

    private final CourierCertificationV2Service service;

    public CourierCertificationV2Controller(IdentityService identity, CourierCertificationV2Service service) {
        this.identity = identity;
        this.service = service;
    }

    @IdentityPermission(role = "COURIER")
    @GetMapping({ "", "/me" })
    public CourierCertificationVO me(@RequestHeader("Authorization") String auth) {
        var a = identity.authenticate(token(auth));
        return service.getMine(Long.parseLong(a.id()));
    }

    @IdentityPermission(role = "COURIER")
    @PostMapping("/applications")
    public CourierCertificationVO apply(@RequestHeader("Authorization") String auth, @RequestHeader(value = "Idempotency-Key", defaultValue = "missing") String key) {
        return service.apply(token(auth), identity, key);
    }

    @IdentityPermission(role = "COURIER")
    @PostMapping(value = "/applications/{id}/student-card", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public // A38 defines expectedVersion as a multipart text field, not a JSON request part.
    CourierCertificationVO upload(@RequestHeader("Authorization") String auth, @PathVariable long id, @RequestPart MultipartFile file, @RequestParam("expectedVersion") long expectedVersion, @RequestHeader(value = "Idempotency-Key", defaultValue = "missing") String key) {
        return service.upload(token(auth), identity, id, expectedVersion, key, MultipartUploads.adapt(file));
    }

    @IdentityPermission(role = "ADMIN")
    @PostMapping("/{id}/decision")
    public CourierCertificationVO decision(@RequestHeader("Authorization") String auth, @PathVariable long id, @RequestBody CertificationDecisionRequest body, @RequestHeader(value = "Idempotency-Key", defaultValue = "missing") String key) {
        return service.decide(token(auth), identity, id, body.expectedVersion(), body.photoResourceId(), body.decision(), body.reason(), key);
    }

    @IdentityPermission(role = "ADMIN")
    @GetMapping("/admin")
    public Map<String, Object> adminList(@RequestHeader("Authorization") String auth, @RequestParam(defaultValue = "") String status, @RequestParam(defaultValue = "") String query, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.adminList(token(auth), status, query, page, size);
    }

    @IdentityPermission(role = "ADMIN")
    @GetMapping("/admin/{id}/performance")
    public Map<String, Object> performance(@RequestHeader("Authorization") String auth, @PathVariable long id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.performance(token(auth), id, page, size);
    }

    @IdentityPermission(role = "ADMIN")
    @GetMapping(value = "/{id}/student-card", produces = { "image/jpeg", "image/png" })
    public ResponseEntity<byte[]> read(@RequestHeader("Authorization") String auth, @PathVariable long id, @RequestParam(required = false) String photoResourceId, @RequestHeader(value = "X-Request-Id", defaultValue = "read") String requestId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").header("X-Content-Type-Options", "nosniff").body(service.read(token(auth), identity, id, photoResourceId, requestId));
    }

    private static String token(String h) {
        if (h == null || !h.startsWith("Bearer "))
            throw IdentityFailure.auth();
        return h.substring(7);
    }
}
