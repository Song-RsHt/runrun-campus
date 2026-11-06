package com.zhangxuan.runruncampus.service.admin;

import com.zhangxuan.runruncampus.dto.response.admin.order.AdminOrderDetailResponse;
import com.zhangxuan.runruncampus.dto.response.admin.order.AdminOrderListItem;
import com.zhangxuan.runruncampus.dto.response.admin.order.AdminOrderPageResponse;
import com.zhangxuan.runruncampus.dto.response.admin.order.OrderEventItem;
import com.zhangxuan.runruncampus.dto.response.admin.order.OrderIssueItem;
import com.zhangxuan.runruncampus.dto.response.admin.order.OrderProofItem;
import com.zhangxuan.runruncampus.dto.response.admin.order.UserSummary;
import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.order.OrderStatus;
import com.zhangxuan.runruncampus.model.order.CampusOrder;
import com.zhangxuan.runruncampus.repository.order.OrderEventRepository;
import com.zhangxuan.runruncampus.repository.order.OrderIssueRepository;
import com.zhangxuan.runruncampus.repository.order.OrderProofRepository;
import com.zhangxuan.runruncampus.repository.order.OrderRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;


























@Service
public class AdminOrderService {

  public static final int EXPORT_LIMIT = 10_000;

  private static final String CSV_HEADER =
      "订单号,状态,服务类型,商家,送达位置,配送费(元),支付方式,支付状态,已结清(元),用户昵称,用户手机,骑手昵称,骑手手机,创建时间,更新时间\r\n";

  private final OrderRepository orderRepository;

  private final OrderEventRepository eventRepository;

  private final OrderIssueRepository issueRepository;

  private final OrderProofRepository proofRepository;

  public AdminOrderService(
      OrderRepository orderRepository,
      OrderEventRepository eventRepository,
      OrderIssueRepository issueRepository,
      OrderProofRepository proofRepository) {
    this.orderRepository = orderRepository;
    this.eventRepository = eventRepository;
    this.issueRepository = issueRepository;
    this.proofRepository = proofRepository;
  }

  @Transactional(readOnly = true)
  public AdminOrderPageResponse list(
      String query, OrderStatus status, boolean archived, int page, int size) {
    Page<CampusOrder> orders =
        findOrders(
            query,
            status,
            archived,
            PageRequest.of(page, size, Sort.by(Direction.DESC, "createdAt", "id")));
    return new AdminOrderPageResponse(
        orders.getContent().stream().map(this::listItem).toList(),
        orders.getNumber(),
        orders.getSize(),
        orders.getTotalElements(),
        orders.getTotalPages());
  }

  @Transactional(readOnly = true)
  public AdminOrderDetailResponse detail(Long id) {
    CampusOrder order =
        orderRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
    return new AdminOrderDetailResponse(
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
            ? null
            : order.getCourierSettlementReviewer().getDisplayName(),
        eventRepository.findByOrderOrderByCreatedAtAsc(order).stream()
            .map(
                event ->
                    new OrderEventItem(
                        event.getId(),
                        event.getStatus(),
                        user(event.getActor()),
                        event.getCreatedAt()))
            .toList(),
        issueRepository.findByOrderOrderByCreatedAtAsc(order).stream()
            .map(
                issue ->
                    new OrderIssueItem(
                        issue.getId(),
                        issue.getType(),
                        issue.getDescription(),
                        user(issue.getReporter()),
                        issue.getCreatedAt()))
            .toList(),
        proofRepository.findByOrderOrderByCreatedAtAsc(order).stream()
            .map(
                proof ->
                    new OrderProofItem(
                        proof.getId(),
                        proof.getType(),
                        proof.getOriginalFileName(),
                        proof.getContentType(),
                        proof.getSizeBytes(),
                        user(proof.getUploader()),
                        proof.getCreatedAt()))
            .toList());
  }

  @Transactional(readOnly = true)
  public OrderExport exportCsv(String query, OrderStatus status, boolean archived) {
    Page<CampusOrder> page =
        findOrders(
            query,
            status,
            archived,
            PageRequest.of(0, EXPORT_LIMIT, Sort.by(Direction.DESC, "createdAt", "id")));
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
    List<String> values =
        List.of(
            order.getOrderNo(),
            order.getStatus().name(),
            order.getServiceType().name(),
            order.getMerchant(),
            order.getDestination(),
            String.format(Locale.ROOT, "%.2f", order.getFeeCents() / 100.0),
            order.getPaymentMethod().name(),
            order.getPaymentStatus().name(),
            String.format(Locale.ROOT, "%.2f", order.getPaidCents() / 100.0),
            order.getCustomer().getDisplayName(),
            order.getCustomer().getPhone(),
            order.getCourier() == null ? "" : order.getCourier().getDisplayName(),
            order.getCourier() == null ? "" : order.getCourier().getPhone(),
            order.getCreatedAt().toString(),
            order.getUpdatedAt().toString());
    csv.append(values.stream().map(this::csvCell).collect(Collectors.joining(",")))
        .append("\r\n");
  }

  private String csvCell(String value) {
    String safe = value == null ? "" : value;
    if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
    return "\"" + safe.replace("\"", "\"\"") + "\"";
  }

  public record OrderExport(byte[] content, boolean truncated) {}

  private Page<CampusOrder> findOrders(
      String query, OrderStatus status, boolean archived, PageRequest pageable) {
    String normalizedQuery = normalize(query);
    return normalizedQuery == null
        ? orderRepository.filterForAdmin(status, archived, pageable)
        : orderRepository.searchForAdmin(normalizedQuery, status, archived, pageable);
  }

  private AdminOrderListItem listItem(CampusOrder order) {
    return new AdminOrderListItem(
        order.getId(),
        order.getOrderNo(),
        order.getStatus(),
        order.getServiceType(),
        order.getMerchant(),
        order.getDestination(),
        order.getFeeCents(),
        order.getPaymentMethod(),
        order.getPaymentStatus(),
        order.getPaidCents(),
        order.getManualPaymentStatus(),
        order.getCourierSettlementStatus(),
        user(order.getCustomer()),
        user(order.getCourier()),
        order.getCreatedAt(),
        order.getUpdatedAt(),
        order.getArchivedAt());
  }

  private UserSummary user(AppUser user) {
    return user == null
        ? null
        : new UserSummary(user.getId(), user.getDisplayName(), user.getPhone(), user.getRole());
  }

  private String normalize(String query) {
    if (query == null || query.isBlank()) return null;
    return query.trim();
  }
}
