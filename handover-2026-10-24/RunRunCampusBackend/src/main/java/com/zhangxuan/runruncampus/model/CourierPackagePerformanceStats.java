package com.zhangxuan.runruncampus.model;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourierPackagePerformanceStats {

    Long getTodayCompletedPackageOrders();

    Long getLastSevenDaysCompletedPackageOrders();

    Long getCurrentMonthCompletedPackageOrders();

    Long getTotalCompletedPackageOrders();

    Long getSmallCompletedPackageOrders();

    Long getMediumCompletedPackageOrders();

    Long getMediumLargeCompletedPackageOrders();

    Long getLargeCompletedPackageOrders();

    Long getSpecialLargeCompletedPackageOrders();

    Long getExtraLargeCompletedPackageOrders();
}
