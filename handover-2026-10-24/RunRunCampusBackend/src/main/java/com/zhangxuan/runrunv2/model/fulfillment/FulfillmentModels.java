package com.zhangxuan.runrunv2.model.fulfillment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class FulfillmentModels {
  private FulfillmentModels() {}

  public enum Kind {
    PICKUP,
    SPECIFICATION,
    DELIVERY
  }

  public enum EventType {
    ARRIVAL,
    DELIVERY
  }

  public enum Decision {
    ACCEPT,
    DECLINE
  }

  public record Version(Long expectedVersion) {}

  public record Respond(Long expectedVersion, Decision decision) {}

  public record Release(Long expectedVersion, String taskId, String reason) {}

  public record Pickup(Long expectedVersion, String taskId, String pickupProofId) {}

  public record Deliver(
      Long expectedVersion, String taskId, String deliveryProofId, String locationEventId) {}

  public record Location(
      Long expectedVersion,
      String taskId,
      EventType eventType,
      BigDecimal latitude,
      BigDecimal longitude,
      String coordinateSystem,
      String requestId) {}

  public record RegisterProof(
      Long expectedVersion, Kind kind, String taskId, String uploadTicket) {}

  public record Upload(Long expectedVersion, Kind kind, String contentType, Long sizeBytes) {}

  public record Ticket(String uploadTicket, String uploadUrl, OffsetDateTime expiresAt) {}

  public record Proof(
      String id,
      String orderId,
      String taskId,
      Kind kind,
      String storageStatus,
      long orderVersion) {}

  public record Task(
      String id,
      String orderId,
      String courierId,
      String status,
      long version,
      OffsetDateTime createdAt) {}

  public record Event(
      String eventId, OffsetDateTime receivedAt, boolean accepted, long orderVersion) {}

  public record Order(
      String id,
      String orderNo,
      int flowVersion,
      String rawStatus,
      String displayStatus,
      long version,
      long originalFeeCents,
      long effectiveFeeCents,
      OffsetDateTime deliveredAt,
      OffsetDateTime confirmStartedAt,
      OffsetDateTime confirmDeadline) {}

  public record Result(Order order, Task task, OffsetDateTime confirmDeadline) {}

  public record TaskItem(
      String taskId,
      String taskStatus,
      long taskVersion,
      int flowVersion,
      String orderId,
      String orderNo,
      String orderStatus,
      long orderVersion,
      OffsetDateTime createdAt,
      List<String> allowedActions) {}

  public record Page<T>(List<T> items, String nextCursor) {}
}
