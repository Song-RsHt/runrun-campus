package com.zhangxuan.runruncampus.controller;

import com.zhangxuan.runruncampus.dto.CertificationRequest;
import com.zhangxuan.runruncampus.dto.DecisionRequest;
import com.zhangxuan.runruncampus.dto.RejectionRequest;
import com.zhangxuan.runruncampus.enums.AdminPermission;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.service.CourierCertificationService;
import com.zhangxuan.runruncampus.vo.CertificationResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v2/courier/certification")
public class CourierCertificationController {

    private final AuthService authService;

    private final CourierCertificationService certificationService;

    public CourierCertificationController(AuthService authService, CourierCertificationService certificationService) {
        this.authService = authService;
        this.certificationService = certificationService;
    }

    @GetMapping("/me")
    public CertificationResponse overview(@RequestHeader("Authorization") String authorization) {
        return certificationService.overview(authService.requireUser(authorization));
    }

    @PostMapping("/apply")
    public CertificationResponse submit(@RequestHeader("Authorization") String authorization, @RequestBody(required = false) CertificationRequest request) {
        return certificationService.submit(authService.requireUser(authorization));
    }

    @PostMapping(value = "/{id}/student-card", consumes = "multipart/form-data")
    public CertificationResponse uploadStudentCard(@RequestHeader("Authorization") String authorization, @PathVariable Long id, @RequestPart("file") MultipartFile file, @RequestPart(value = "expectedVersion", required = false) Long expectedVersion) {
        return certificationService.uploadStudentCard(authService.requireUser(authorization), id, file, expectedVersion);
    }

    @GetMapping("/{id}/student-card")
    public ResponseEntity<byte[]> studentCardPhoto(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        var admin = authService.requirePermission(authorization, AdminPermission.COURIER_REVIEW);
        var photo = certificationService.studentCardPhoto(admin, id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType())).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(photo.bytes());
    }

    @GetMapping("/pending")
    public List<CertificationResponse> pending(@RequestHeader("Authorization") String authorization) {
        return certificationService.pending(authService.requirePermission(authorization, AdminPermission.COURIER_REVIEW));
    }

    @PostMapping("/{id}/approve")
    public CertificationResponse approve(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return certificationService.approve(authService.requirePermission(authorization, AdminPermission.COURIER_REVIEW), id);
    }

    @PostMapping("/{id}/decision")
    public CertificationResponse decision(@RequestHeader("Authorization") String authorization, @PathVariable Long id, @Valid @RequestBody DecisionRequest request) {
        var admin = authService.requirePermission(authorization, AdminPermission.COURIER_REVIEW);
        if ("APPROVE".equals(request.decision())) {
            return certificationService.approve(admin, id, request.expectedVersion(), request.photoResourceId());
        }
        return certificationService.reject(admin, id, request.reason());
    }

    @PostMapping("/{id}/reject")
    public CertificationResponse reject(@RequestHeader("Authorization") String authorization, @PathVariable Long id, @Valid @RequestBody RejectionRequest request) {
        return certificationService.reject(authService.requirePermission(authorization, AdminPermission.COURIER_REVIEW), id, request.reason());
    }
}
