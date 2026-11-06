package com.zhangxuan.runrunv2.dto.response.fulfillment;

import com.zhangxuan.runrunv2.enums.fulfillment.Kind;
import java.time.OffsetDateTime;
import java.util.List;

public final class FulfillmentModels {

  private FulfillmentModels() {}

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
