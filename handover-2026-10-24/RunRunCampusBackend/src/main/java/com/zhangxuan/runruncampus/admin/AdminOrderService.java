package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.order.CampusOrder;
import com.zhangxuan.runruncampus.order.OrderEventRepository;
import com.zhangxuan.runruncampus.order.OrderIssueRepository;
import com.zhangxuan.runruncampus.order.OrderProofRepository;
import com.zhangxuan.runruncampus.order.OrderRepository;
import com.zhangxuan.runruncampus.order.OrderStatus;
import com.zhangxuan.runruncampus.user.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class AdminOrderService {
    static final int EXPORT_LIMIT = 10_000;
    private static final String CSV_HEADER = "订单号,状态,服务类型,商家,送达位置,配送费(元),支付方式,支付状态,已结清(元),用户昵称,用户手机,骑手昵称,骑手手机,创建时间,更新时间\r\n";
    private final OrderRepository orderRepository;
    private final OrderEventRepository eventRepository;
    private final OrderIssueRepository issueRepository;
    private final OrderProofRepository proofRepository;

    public AdminOrderService(OrderRepository orderRepository, OrderEventRepository eventRepository,
                             OrderIssueRepository issueRepository, OrderProofRepository proofRepository) {
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.issueRepository = issueRepository;
        this.proofRepository = proofRepository;
    }

    @Transactional(readOnly = true)
    public AdminOrderController.AdminOrderPageResponse list(String query, OrderStatus status,
                                                            boolean archived, int page, int size) {
        Page<CampusOrder> orders = findOrders(query, status, archived,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new AdminOrderController.AdminOrderPageResponse(
                orders.getContent().stream().map(this::listItem).toList(),
                orders.getNumber(), orders.getSize(), orders.getTotalElements(), orders.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminOrderController.AdminOrderDetailResponse detail(Long id) {
        CampusOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        return new AdminOrderController.AdminOrderDetailResponse(
                listItem(order),
                order.getPickupCode(),
                order.getPickupLocation(),
                order.getOriginalFeeCents(),
                order.getDiscountCents(),
                order.getCouponTitle(),
                order.isMembershipUsed(),
                order.getMembershipPlanTitle(),
                order.getRatingScore(),
                order.getRatingComment(),
                order.getRatedAt(),
                order.getCourierLatitude(),
                order.getCourierLongitude(),
                order.getCourierLocationUpdatedAt(),
                order.getPayerReference(),
                order.getPaymentRequestedAt(),
                order.getPaymentSubmittedAt(),
                order.getPaymentReviewedAt(),
                order.getPaymentReviewer() == null ? null : order.getPaymentReviewer().getDisplayName(),
                order.getPaymentReviewNote(),
                order.getCourierSettledAt(),
                order.getCourierSettlementReviewer() == null
                        ? null : order.getCourierSettlementReviewer().getDisplayName(),
                eventRepository.findByOrderOrderByCreatedAtAsc(order).stream()
                        .map(event -> new AdminOrderController.OrderEventItem(
                                event.getId(), event.getStatus(), user(event.getActor()), event.getCreatedAt()))
                        .toList(),
                issueRepository.findByOrderOrderByCreatedAtAsc(order).stream()
                        .map(issue -> new AdminOrderController.OrderIssueItem(
                                issue.getId(), issue.getType(), issue.getDescription(), user(issue.getReporter()),
                                issue.getCreatedAt()))
                        .toList(),
                proofRepository.findByOrderOrderByCreatedAtAsc(order).stream()
                        .map(proof -> new AdminOrderController.OrderProofItem(
                                proof.getId(), proof.getType(), proof.getOriginalFileName(), proof.getContentType(),
                                proof.getSizeBytes(), user(proof.getUploader()), proof.getCreatedAt()))
                        .toList());
    }

    @Transactional(readOnly = true)
    public OrderExport exportCsv(String query, OrderStatus status, boolean archived) {
        Page<CampusOrder> page = findOrders(query, status, archived,
                PageRequest.of(0, EXPORT_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        StringBuilder csv = new StringBuilder(CSV_HEADER);
        page.getContent().forEach(order -> appendCsvRow(csv, order));
        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        return new OrderExport(withBom, page.getTotalElements() > EXPORT_LIMIT);
    }

    private void appendCsvRow(StringBuilder csv, CampusOrder order) {
        List<String> values = List.of(
                order.getOrderNo(), order.getStatus().name(), order.getServiceType().name(), order.getMerchant(),
                order.getDestination(), String.format(java.util.Locale.ROOT, "%.2f", order.getFeeCents() / 100.0),
                order.getPaymentMethod().name(), order.getPaymentStatus().name(),
                String.format(java.util.Locale.ROOT, "%.2f", order.getPaidCents() / 100.0),
                order.getCustomer().getDisplayName(), order.getCustomer().getPhone(),
                order.getCourier() == null ? "" : order.getCourier().getDisplayName(),
                order.getCourier() == null ? "" : order.getCourier().getPhone(),
                order.getCreatedAt().toString(), order.getUpdatedAt().toString());
        csv.append(values.stream().map(this::csvCell).collect(java.util.stream.Collectors.joining(",")))
                .append("\r\n");
    }

    private String csvCell(String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    public record OrderExport(byte[] content, boolean truncated) { }

    private Page<CampusOrder> findOrders(String query, OrderStatus status, boolean archived, PageRequest pageable) {
        String normalizedQuery = normalize(query);
        return normalizedQuery == null
                ? orderRepository.filterForAdmin(status, archived, pageable)
                : orderRepository.searchForAdmin(normalizedQuery, status, archived, pageable);
    }

    private AdminOrderController.AdminOrderListItem listItem(CampusOrder order) {
        return new AdminOrderController.AdminOrderListItem(
                order.getId(), order.getOrderNo(), order.getStatus(), order.getServiceType(), order.getMerchant(),
                order.getDestination(), order.getFeeCents(), order.getPaymentMethod(), order.getPaymentStatus(),
                order.getPaidCents(), order.getManualPaymentStatus(), order.getCourierSettlementStatus(),
                user(order.getCustomer()), user(order.getCourier()),
                order.getCreatedAt(), order.getUpdatedAt(), order.getArchivedAt());
    }

    private AdminOrderController.UserSummary user(AppUser user) {
        return user == null ? null : new AdminOrderController.UserSummary(
                user.getId(), user.getDisplayName(), user.getPhone(), user.getRole());
    }

    private String normalize(String query) {
        if (query == null || query.isBlank()) return null;
        return query.trim();
    }
}
