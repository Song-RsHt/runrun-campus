package com.zhangxuan.runruncampus.courier;

import com.zhangxuan.runruncampus.auth.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

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
    OperationsResponse overview(@RequestHeader("Authorization") String authorization) {
        return operationsService.overview(authService.requireUser(authorization));
    }

    @PutMapping("/me/availability")
    OperationsResponse updateAvailability(@RequestHeader("Authorization") String authorization,
                                          @RequestBody AvailabilityRequest request) {
        return operationsService.updateAvailability(authService.requireUser(authorization), request.acceptingOrders());
    }

    public record AvailabilityRequest(boolean acceptingOrders) { }

    public record OperationsResponse(
            boolean courierApproved,
            boolean acceptingOrders,
            Instant availabilityUpdatedAt,
            long availableOrders,
            long activeOrders,
            int maxActiveOrders,
            long todayCompletedOrders,
            long totalCompletedOrders,
            long todayGrossServiceFeeCents,
            long totalGrossServiceFeeCents,
            double averageRating,
            long ratingCount,
            Instant generatedAt
    ) { }
}
