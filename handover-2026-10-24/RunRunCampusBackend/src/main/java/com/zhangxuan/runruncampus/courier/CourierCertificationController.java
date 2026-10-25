package com.zhangxuan.runruncampus.courier;

import com.zhangxuan.runruncampus.auth.AuthService;
import com.zhangxuan.runruncampus.admin.AdminPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/courier-certification")
public class CourierCertificationController {
    private final AuthService authService;
    private final CourierCertificationService certificationService;

    public CourierCertificationController(AuthService authService,
                                           CourierCertificationService certificationService) {
        this.authService = authService;
        this.certificationService = certificationService;
    }

    @GetMapping("/me")
    CourierCertificationService.CertificationResponse overview(
            @RequestHeader("Authorization") String authorization) {
        return certificationService.overview(authService.requireUser(authorization));
    }

    @PostMapping("/apply")
    CourierCertificationService.CertificationResponse submit(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody CertificationRequest request) {
        return certificationService.submit(authService.requireUser(authorization));
    }

    @PostMapping(value = "/{id}/student-card", consumes = "multipart/form-data")
    CourierCertificationService.CertificationResponse uploadStudentCard(
            @RequestHeader("Authorization") String authorization, @PathVariable Long id,
            @RequestPart("file") MultipartFile file) {
        return certificationService.uploadStudentCard(authService.requireUser(authorization), id, file);
    }

    @GetMapping("/{id}/student-card")
    ResponseEntity<byte[]> studentCardPhoto(@RequestHeader("Authorization") String authorization,
                                            @PathVariable Long id) {
        var admin = authService.requirePermission(authorization, AdminPermission.COURIER_REVIEW);
        var photo = certificationService.studentCardPhoto(admin, id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .body(photo.bytes());
    }

    @GetMapping("/pending")
    List<CourierCertificationService.CertificationResponse> pending(
            @RequestHeader("Authorization") String authorization) {
        return certificationService.pending(
                authService.requirePermission(authorization, AdminPermission.COURIER_REVIEW));
    }

    @PostMapping("/{id}/approve")
    CourierCertificationService.CertificationResponse approve(
            @RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        return certificationService.approve(
                authService.requirePermission(authorization, AdminPermission.COURIER_REVIEW), id);
    }

    @PostMapping("/{id}/reject")
    CourierCertificationService.CertificationResponse reject(
            @RequestHeader("Authorization") String authorization, @PathVariable Long id,
            @Valid @RequestBody RejectionRequest request) {
        return certificationService.reject(
                authService.requirePermission(authorization, AdminPermission.COURIER_REVIEW), id, request.reason());
    }

    public record CertificationRequest(
            String realName,
            String studentId,
            String campus,
            LocalDate validUntil
    ) { }

    public record RejectionRequest(
            @NotBlank(message = "请输入驳回原因") @Size(max = 300, message = "驳回原因不能超过300字") String reason
    ) { }
}
