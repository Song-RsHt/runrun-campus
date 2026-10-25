package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.auth.AuthService;
import com.zhangxuan.runruncampus.courier.CourierCertificationStatus;
import com.zhangxuan.runruncampus.order.OrderStatus;
import com.zhangxuan.runruncampus.order.ServiceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/admin/couriers")
public class AdminCourierController {
    private final AuthService authService;
    private final AdminCourierService adminCourierService;

    public AdminCourierController(AuthService authService, AdminCourierService adminCourierService) {
        this.authService = authService;
        this.adminCourierService = adminCourierService;
    }

    @GetMapping
    AdminCourierPageResponse list(@RequestHeader("Authorization") String authorization,
                                  @RequestParam(required = false) String query,
                                  @RequestParam(required = false) CourierCertificationStatus status,
                                  @RequestParam(defaultValue = "0") @Min(0) int page,
                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        authService.requirePermission(authorization, AdminPermission.COURIER_VIEW);
        return adminCourierService.list(query, status, page, size);
    }

    @GetMapping("/{courierId}/performance")
    AdminCourierPerformanceResponse performance(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long courierId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        authService.requirePermission(authorization, AdminPermission.COURIER_VIEW);
        return adminCourierService.performance(courierId, page, size);
    }

    public record AdminCourierPageResponse(
            List<AdminCourierItem> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) { }

    public record AdminCourierItem(
            Long courierId,
            String displayName,
            String phone,
            boolean approved,
            Instant registeredAt,
            long completedOrderCount,
            long completedPackageOrderCount,
            Long certificationId,
            String realName,
            String studentId,
            String campus,
            CourierCertificationStatus status,
            String rejectionReason,
            Instant submittedAt,
            Instant reviewedAt,
            String reviewerName,
            LocalDate validUntil,
            LocalDate approvedValidUntil,
            boolean photoUploaded
    ) { }

    public record AdminCourierPerformanceResponse(
            long todayCompletedPackageOrders,
            long lastSevenDaysCompletedPackageOrders,
            long currentMonthCompletedPackageOrders,
            long totalCompletedPackageOrders,
            long smallCompletedPackageOrders,
            long mediumCompletedPackageOrders,
            long mediumLargeCompletedPackageOrders,
            long largeCompletedPackageOrders,
            long specialLargeCompletedPackageOrders,
            long extraLargeCompletedPackageOrders,
            AdminCourierOrderPage orders
    ) { }

    public record AdminCourierOrderPage(
            List<AdminCourierOrderItem> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) { }

    public record AdminCourierOrderItem(
            Long id,
            String orderNo,
            OrderStatus status,
            ServiceType serviceType,
            String destination,
            int feeCents,
            String customerName,
            Instant createdAt,
            Instant updatedAt
    ) { }
}
