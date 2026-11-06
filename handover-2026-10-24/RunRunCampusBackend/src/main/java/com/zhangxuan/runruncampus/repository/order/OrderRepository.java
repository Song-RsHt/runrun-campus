package com.zhangxuan.runruncampus.repository.order;

import com.zhangxuan.runruncampus.entity.order.CampusOrder;
import com.zhangxuan.runruncampus.entity.user.AppUser;
import com.zhangxuan.runruncampus.enums.order.OrderStatus;
import com.zhangxuan.runruncampus.model.order.CourierBusinessStats;
import com.zhangxuan.runruncampus.model.order.CourierCompletedOrderStats;
import com.zhangxuan.runruncampus.model.order.CourierPackagePerformanceStats;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 订单创建与状态管理 JPA 仓储；只定义持久化查询和写入，不承担权限及业务决策。 */
public interface OrderRepository extends JpaRepository<CampusOrder, Long> {

    Optional<CampusOrder> findByCustomerAndRequestKey(AppUser customer, UUID requestKey);

    @Query(value = "select nextval('order_number_seq')", nativeQuery = true)
    long nextOrderNumber();

    long countByStatus(OrderStatus status);

    @Query(
            """
            select count(o) from CampusOrder o
            where o.status = com.zhangxuan.runruncampus.enums.order.OrderStatus.PENDING_ASSIGNMENT
              and (o.paymentStatus = com.zhangxuan.runruncampus.enums.order.OrderPaymentStatus.PAID
                   or o.paymentMethod = com.zhangxuan.runruncampus.enums.order.OrderPaymentMethod.CASH_ON_DELIVERY
                   or o.paymentMethod = com.zhangxuan.runruncampus.enums.order.OrderPaymentMethod.PERSONAL_QR_MANUAL)
            """)
    long countFulfillablePendingOrders();

    long countByCourierAndStatusIn(AppUser courier, List<OrderStatus> statuses);

    long countByCustomerAndStatusIn(AppUser customer, List<OrderStatus> statuses);

    List<CampusOrder> findByCustomerOrderByCreatedAtDesc(AppUser customer);

    List<CampusOrder> findByCourierOrderByCreatedAtDesc(AppUser courier);

    List<CampusOrder> findByCustomerAndStatusInOrderByCreatedAtDesc(
            AppUser customer, List<OrderStatus> statuses);

    List<CampusOrder> findByCourierAndStatusInOrderByCreatedAtDesc(
            AppUser courier, List<OrderStatus> statuses);

    Page<CampusOrder> findByCustomerAndStatusIn(
            AppUser customer, List<OrderStatus> statuses, Pageable pageable);

    Page<CampusOrder> findByCourierAndStatusIn(
            AppUser courier, List<OrderStatus> statuses, Pageable pageable);

    @Query(
            """
            select o from CampusOrder o
            where o.customer = :customer and o.status in :statuses
              and (:hasStart = false or o.createdAt >= :startInclusive)
              and (:hasEnd = false or o.createdAt < :endExclusive)
            """)
    Page<CampusOrder> findHistoryByCustomer(
            @Param("customer") AppUser customer,
            @Param("statuses") List<OrderStatus> statuses,
            @Param("hasStart") boolean hasStart,
            @Param("hasEnd") boolean hasEnd,
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive,
            Pageable pageable);

    @Query(
            """
            select o from CampusOrder o
            where o.courier = :courier and o.status in :statuses
              and (:hasStart = false or o.createdAt >= :startInclusive)
              and (:hasEnd = false or o.createdAt < :endExclusive)
            """)
    Page<CampusOrder> findHistoryByCourier(
            @Param("courier") AppUser courier,
            @Param("statuses") List<OrderStatus> statuses,
            @Param("hasStart") boolean hasStart,
            @Param("hasEnd") boolean hasEnd,
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive,
            Pageable pageable);

    @Query(
            """
            select o from CampusOrder o
            where o.status = com.zhangxuan.runruncampus.enums.order.OrderStatus.PENDING_ASSIGNMENT
              and (o.paymentStatus = com.zhangxuan.runruncampus.enums.order.OrderPaymentStatus.PAID
                   or o.paymentMethod = com.zhangxuan.runruncampus.enums.order.OrderPaymentMethod.CASH_ON_DELIVERY
                   or o.paymentMethod = com.zhangxuan.runruncampus.enums.order.OrderPaymentMethod.PERSONAL_QR_MANUAL)
            order by o.createdAt asc
            """)
    List<CampusOrder> findFulfillablePendingOrders();

    long countByCourierAndRatingScoreIsNotNull(AppUser courier);

    @Query(
            """
            select count(e) as totalCompletedOrders,
                   sum(case when e.createdAt >= :todayStart then 1 else 0 end) as todayCompletedOrders,
                   coalesce(sum(o.feeCents), 0) as totalGrossServiceFeeCents,
                   coalesce(sum(case when e.createdAt >= :todayStart then o.feeCents else 0 end), 0)
                       as todayGrossServiceFeeCents
            from OrderEvent e
            join e.order o
            where o.courier = :courier
              and e.status = com.zhangxuan.runruncampus.enums.order.OrderStatus.COMPLETED
            """)
    CourierBusinessStats businessStatsForCourier(
            @Param("courier") AppUser courier, @Param("todayStart") Instant todayStart);

    @Query(
            """
            select o.courier.id as courierId,
                   count(o) as completedOrderCount,
                   sum(case when o.serviceType in (
                       com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_SMALL,
                       com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_MEDIUM,
                       com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_MEDIUM_LARGE,
                       com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_LARGE,
                       com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_EXTRA_LARGE,
                       com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_XL
                   ) then 1 else 0 end) as completedPackageOrderCount
            from CampusOrder o
            where o.courier.id in :courierIds
              and o.status = com.zhangxuan.runruncampus.enums.order.OrderStatus.COMPLETED
            group by o.courier.id
            """)
    List<CourierCompletedOrderStats> countCompletedOrdersByCourierIds(
            @Param("courierIds") List<Long> courierIds);

    @Query(
            """
            select count(e) as totalCompletedPackageOrders,
                   sum(case when e.createdAt >= :todayStart then 1 else 0 end)
                       as todayCompletedPackageOrders,
                   sum(case when e.createdAt >= :lastSevenDaysStart then 1 else 0 end)
                       as lastSevenDaysCompletedPackageOrders,
                   sum(case when e.createdAt >= :currentMonthStart then 1 else 0 end)
                       as currentMonthCompletedPackageOrders,
                   sum(case when o.serviceType = com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_SMALL
                       then 1 else 0 end) as smallCompletedPackageOrders,
                   sum(case when o.serviceType = com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_MEDIUM
                       then 1 else 0 end) as mediumCompletedPackageOrders,
                   sum(case when o.serviceType = com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_MEDIUM_LARGE
                       then 1 else 0 end) as mediumLargeCompletedPackageOrders,
                   sum(case when o.serviceType = com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_LARGE
                       then 1 else 0 end) as largeCompletedPackageOrders,
                   sum(case when o.serviceType = com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_EXTRA_LARGE
                       then 1 else 0 end) as specialLargeCompletedPackageOrders,
                   sum(case when o.serviceType = com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_XL
                       then 1 else 0 end) as extraLargeCompletedPackageOrders
            from OrderEvent e
            join e.order o
            where o.courier.id = :courierId
              and o.status = com.zhangxuan.runruncampus.enums.order.OrderStatus.COMPLETED
              and e.status = com.zhangxuan.runruncampus.enums.order.OrderStatus.COMPLETED
              and o.serviceType in (
                  com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_SMALL,
                  com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_MEDIUM,
                  com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_MEDIUM_LARGE,
                  com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_LARGE,
                  com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_EXTRA_LARGE,
                  com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_XL
              )
            """)
    CourierPackagePerformanceStats packagePerformanceForCourier(
            @Param("courierId") Long courierId,
            @Param("todayStart") Instant todayStart,
            @Param("lastSevenDaysStart") Instant lastSevenDaysStart,
            @Param("currentMonthStart") Instant currentMonthStart);

    @Query(
            value =
                    """
                    select o from CampusOrder o
                    join fetch o.customer
                    where o.courier.id = :courierId
                      and o.serviceType in (
                          com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_SMALL,
                          com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_MEDIUM,
                          com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_MEDIUM_LARGE,
                          com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_LARGE,
                          com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_EXTRA_LARGE,
                          com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_XL
                      )
                    """,
            countQuery =
                    """
                    select count(o) from CampusOrder o
                    where o.courier.id = :courierId
                      and o.serviceType in (
                          com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_SMALL,
                          com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_MEDIUM,
                          com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_MEDIUM_LARGE,
                          com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_LARGE,
                          com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_EXTRA_LARGE,
                          com.zhangxuan.runruncampus.enums.order.ServiceType.PACKAGE_XL
                      )
                    """)
    Page<CampusOrder> findPackageOrdersForCourier(
            @Param("courierId") Long courierId, Pageable pageable);

    @Query(
            "select avg(o.ratingScore) from CampusOrder o where o.courier = :courier and"
                    + " o.ratingScore is not null")
    Double averageRatingForCourier(@Param("courier") AppUser courier);

    @Query(
            value =
                    """
                    select o from CampusOrder o
                    join fetch o.customer customer
                    left join fetch o.courier courier
                    where (:status is null or o.status = :status)
                      and ((:archived = true and o.archivedAt is not null)
                           or (:archived = false and o.archivedAt is null))
                      and (:query is null
                        or lower(o.orderNo) like lower(concat('%', :query, '%'))
                        or lower(o.merchant) like lower(concat('%', :query, '%'))
                        or lower(o.destination) like lower(concat('%', :query, '%'))
                        or lower(customer.displayName) like lower(concat('%', :query, '%'))
                        or customer.phone like concat('%', :query, '%')
                        or lower(courier.displayName) like lower(concat('%', :query, '%'))
                        or courier.phone like concat('%', :query, '%'))
                    """,
            countQuery =
                    """
                    select count(o) from CampusOrder o
                    join o.customer customer
                    left join o.courier courier
                    where (:status is null or o.status = :status)
                      and ((:archived = true and o.archivedAt is not null)
                           or (:archived = false and o.archivedAt is null))
                      and (:query is null
                        or lower(o.orderNo) like lower(concat('%', :query, '%'))
                        or lower(o.merchant) like lower(concat('%', :query, '%'))
                        or lower(o.destination) like lower(concat('%', :query, '%'))
                        or lower(customer.displayName) like lower(concat('%', :query, '%'))
                        or customer.phone like concat('%', :query, '%')
                        or lower(courier.displayName) like lower(concat('%', :query, '%'))
                        or courier.phone like concat('%', :query, '%'))
                    """)
    Page<CampusOrder> searchForAdmin(
            @Param("query") String query,
            @Param("status") OrderStatus status,
            @Param("archived") boolean archived,
            Pageable pageable);

    @Query(
            value =
                    """
                    select o from CampusOrder o
                    join fetch o.customer
                    left join fetch o.courier
                    where (:status is null or o.status = :status)
                      and ((:archived = true and o.archivedAt is not null)
                           or (:archived = false and o.archivedAt is null))
                    """,
            countQuery =
                    """
                    select count(o) from CampusOrder o where (:status is null or o.status = :status)
                      and ((:archived = true and o.archivedAt is not null)
                           or (:archived = false and o.archivedAt is null))
                    """)
    Page<CampusOrder> filterForAdmin(
            @Param("status") OrderStatus status,
            @Param("archived") boolean archived,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CampusOrder o where o.id = :id")
    Optional<CampusOrder> findLockedById(@Param("id") Long id);
}
