package com.zhangxuan.runrunv2.service.fulfillment;

import com.zhangxuan.runrunv2.config.OrderJson;
import com.zhangxuan.runrunv2.dto.request.fulfillment.Deliver;
import com.zhangxuan.runrunv2.dto.request.fulfillment.Location;
import com.zhangxuan.runrunv2.dto.request.fulfillment.Pickup;
import com.zhangxuan.runrunv2.dto.request.fulfillment.RegisterProof;
import com.zhangxuan.runrunv2.dto.request.fulfillment.Release;
import com.zhangxuan.runrunv2.dto.request.fulfillment.Respond;
import com.zhangxuan.runrunv2.dto.request.fulfillment.Upload;
import com.zhangxuan.runrunv2.dto.request.fulfillment.Version;
import com.zhangxuan.runrunv2.dto.response.fulfillment.FulfillmentModels.Event;
import com.zhangxuan.runrunv2.dto.response.fulfillment.FulfillmentModels.Proof;
import com.zhangxuan.runrunv2.dto.response.fulfillment.FulfillmentModels.Result;
import com.zhangxuan.runrunv2.dto.response.fulfillment.FulfillmentModels.Task;
import com.zhangxuan.runrunv2.dto.response.fulfillment.FulfillmentModels.Ticket;
import com.zhangxuan.runrunv2.enums.fulfillment.Decision;
import com.zhangxuan.runrunv2.enums.fulfillment.EventType;
import com.zhangxuan.runrunv2.enums.fulfillment.Kind;
import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.mapper.fulfillment.FulfillmentMapper;
import com.zhangxuan.runrunv2.mapper.order.OrderStore;
import com.zhangxuan.runrunv2.model.InputValidation;
import com.zhangxuan.runrunv2.model.ResourceIds;
import com.zhangxuan.runrunv2.service.IdentityService;
import com.zhangxuan.runrunv2.service.fulfillment.FulfillmentEvidence.Binding;
import com.zhangxuan.runrunv2.service.idempotency.OrderIdempotency;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 协调 v2 接单、邀请、凭证和完成确认。状态判定、幂等与事务保留在本层，Mapper 保留原行锁与更新条件。 凭证未接入时继续拒绝推进，不把占位适配器视为已验证证据。参见《订单与履约规则》。
 */
@Service
public class FulfillmentService {

  private final FulfillmentMapper mapper;

  private final IdentityService identity;

  private final OrderStore orders;

  private final OrderIdempotency requests;

  private final OrderJson json;

  private final FulfillmentEvidence evidence;

  private final Clock clock;

  private final int capacity;

  public FulfillmentService(
      FulfillmentMapper mapper,
      IdentityService identity,
      OrderStore orders,
      OrderIdempotency requests,
      OrderJson json,
      FulfillmentEvidence evidence,
      Clock clock,
      @Value("${rrc.fulfillment.capacity:3}") int capacity) {
    if (capacity < 0 || capacity > 100)
      throw new IllegalArgumentException("Invalid courier capacity");
    this.mapper = mapper;
    this.identity = identity;
    this.orders = orders;
    this.requests = requests;
    this.json = json;
    this.evidence = evidence;
    this.clock = clock;
    this.capacity = capacity;
  }

  @Transactional
  public Result accept(String token, String orderId, String key, Version body) {
    var actor = identity.authorizeForWrite(token, "COURIER", true);
    var order = lock(orderId);
    required(body == null ? null : body.expectedVersion());
    Result replay =
        requests.replay(
            actor.id(), "fulfillment:accept", key, List.of(orderId, body), Result.class);
    if (replay != null) return replay;
    version(order.version(), body.expectedVersion());
    state(order.status().equals("PENDING_ASSIGNMENT"));
    capacity(actor.id());
    String task = mapper.insertActiveTask(id(orderId), id(actor.id()), now(), now());
    supersede(orderId, task);
    closeOffers(orderId, task);
    mapper.markOrderAccepted(now(), id(orderId));
    event(orderId, task, actor.id(), "ACCEPTED", key, Map.of());
    Result result = result(orderId, task);
    finish(actor.id(), "accept", key, orderId, result);
    return result;
  }

  /**
   * M05 invokes this only after an audited exception/refulfillment decision. It creates a fresh
   * invitation; historical tasks are never reactivated.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public Task invite(
      String token,
      String orderId,
      String courierId,
      long expectedVersion,
      String reason,
      String key) {
    var actor = identity.authorizeForWrite(token, "ADMIN", false);
    InputValidation.text(reason, 500);
    var targets = mapper.lockActiveCourier(id(courierId));
    if (targets.isEmpty()) throw missing();
    var order = lock(orderId);
    Task replay =
        requests.replay(
            actor.id(),
            "fulfillment:invite",
            key,
            List.of(orderId, courierId, expectedVersion, reason),
            Task.class);
    if (replay != null) return replay;
    version(order.version(), expectedVersion);
    state(order.status().equals("EXCEPTION"));
    state(mapper.countActiveTasksForInvitation(id(orderId)) == 0);
    String source = mapper.findLatestTaskId(id(orderId));
    closeOffers(orderId, null);
    String task =
        mapper.insertTaskOffer(
            id(orderId), id(courierId), source == null ? null : id(source), now());
    supersede(orderId, task);
    mapper.clearOrderConfirmation(now(), id(orderId));
    event(orderId, task, actor.id(), "INVITED", key, Map.of("reason", reason));
    notify(courierId, "invitation:" + task, orderId, "TASK_OFFERED");
    Task result = task(task);
    finish(actor.id(), "invite", key, orderId, result);
    return result;
  }

  @Transactional
  public Result respond(String token, String taskId, String key, Respond body) {
    // A declined invitation needs no eligibility, but accepting it always does.
    if (body == null || body.decision() == null) throw IdentityFailure.invalid();
    var actor = identity.authorizeForWrite(token, "COURIER", body.decision() == Decision.ACCEPT);
    Task task = own(taskId, actor.id());
    var order = lock(task.orderId());
    task = own(taskId, actor.id());
    Result replay =
        requests.replay(
            actor.id(), "fulfillment:respond", key, List.of(taskId, body), Result.class);
    if (replay != null) return replay;
    version(task.version(), body.expectedVersion());
    state(
        task.status().equals("OFFERED")
            && unsuperseded(taskId)
            && order.status().equals("EXCEPTION"));
    if (body.decision() == Decision.DECLINE) end(taskId, "RELEASED");
    else {
      capacity(actor.id());
      state(mapper.countActiveTasksForResponse(id(task.orderId())) == 0);
      mapper.acceptTaskOffer(now(), id(taskId));
      mapper.acceptOrderAndClearConfirmation(now(), id(task.orderId()));
    }
    if (body.decision() == Decision.DECLINE)
      mapper.clearOrderConfirmation(now(), id(task.orderId()));
    event(task.orderId(), taskId, actor.id(), "INVITATION_" + body.decision(), key, Map.of());
    Result result = result(task.orderId(), taskId);
    finish(actor.id(), "respond", key, task.orderId(), result);
    return result;
  }

  @Transactional
  public Result release(String token, String orderId, String key, Release body) {
    var actor = identity.authorizeForWrite(token, "COURIER", true);
    if (body == null) throw IdentityFailure.invalid();
    var order = lock(orderId);
    Task task = ownInOrder(body.taskId(), actor.id(), orderId);
    InputValidation.text(body.reason(), 500);
    Result replay =
        requests.replay(
            actor.id(), "fulfillment:release", key, List.of(orderId, body), Result.class);
    if (replay != null) return replay;
    version(order.version(), body.expectedVersion());
    active(task, order);
    state(order.status().equals("ACCEPTED"));
    // A pickup photo may mean physical custody even before the pickup click.
    // Never discard that ambiguity by allowing a normal self-release (§8).
    state(mapper.countPickupProofs(id(task.id())) == 0);
    end(task.id(), "RELEASED");
    mapper.markOrderPendingAssignment(now(), id(orderId));
    event(orderId, task.id(), actor.id(), "RELEASED", key, Map.of("reason", body.reason()));
    Result result = result(orderId, task.id());
    finish(actor.id(), "release", key, orderId, result);
    return result;
  }

  @Transactional
  public Ticket upload(String token, String orderId, String key, Upload body) {
    var actor = identity.authorizeForWrite(token, "COURIER", true);
    if (body == null
        || body.kind() == null
        || body.sizeBytes() == null
        || body.sizeBytes() < 1
        || body.sizeBytes() > 8L * 1024 * 1024
        || !Set.of("image/jpeg", "image/png")
            .contains(body.contentType() == null ? "" : body.contentType()))
      throw new IdentityFailure(400, "UPLOAD_INVALID");
    var order = lock(orderId);
    Task task = current(actor.id(), orderId);
    proofPermission(task, order, body.kind());
    // Tickets are issued by the private lifecycle service. A replay must still
    // recheck current write rights and ticket expiration in that service.
    Ticket replay =
        requests.replay(
            actor.id(), "fulfillment:upload", key, List.of(orderId, body), Ticket.class);
    if (replay != null) {
      if (!now().isBefore(replay.expiresAt())) throw new IdentityFailure(409, "PROOF_NOT_READY");
      return replay;
    }
    version(order.version(), body.expectedVersion());
    Ticket ticket =
        evidence.issue(binding(task, body.kind()), body.contentType(), body.sizeBytes(), now());
    if (ticket == null
        || ticket.uploadTicket() == null
        || ticket.uploadUrl() == null
        || ticket.expiresAt() == null
        || !ticket.expiresAt().isAfter(now())) throw new IdentityFailure(409, "PROOF_NOT_READY");
    mapper.advanceOrderVersion(now(), id(orderId));
    event(orderId, task.id(), actor.id(), "UPLOAD_ISSUED", key, Map.of("kind", body.kind()));
    finish(actor.id(), "upload", key, orderId, ticket);
    return ticket;
  }

  @Transactional
  public Proof proof(String token, String orderId, String key, RegisterProof body) {
    var actor = identity.authorizeForWrite(token, "COURIER", true);
    if (body == null || body.kind() == null) throw IdentityFailure.invalid();
    var order = lock(orderId);
    Task task = ownInOrder(body.taskId(), actor.id(), orderId);
    proofPermission(task, order, body.kind());
    InputValidation.text(body.uploadTicket(), 512);
    Proof replay =
        requests.replay(actor.id(), "fulfillment:proof", key, List.of(orderId, body), Proof.class);
    if (replay != null) return replay;
    version(order.version(), body.expectedVersion());
    if (body.kind() != Kind.SPECIFICATION
        && mapper.countTaskProofs(id(task.id()), body.kind().name()) > 0) throw conflict();
    var binding = binding(task, body.kind());
    var verified = evidence.verify(binding, body.uploadTicket(), now());
    if (verified == null
        || !binding.equals(verified.binding())
        || verified.storageKey() == null
        || verified.storageKey().isBlank()) throw new IdentityFailure(409, "PROOF_NOT_READY");
    if (mapper.countProofsByStorageKey(verified.storageKey()) > 0)
      throw new IdentityFailure(409, "PROOF_NOT_READY");
    String proof =
        mapper.insertOrderProof(
            id(orderId), id(task.id()), id(actor.id()), body.kind().name(), verified.storageKey());
    mapper.advanceOrderVersion(now(), id(orderId));
    event(
        orderId,
        task.id(),
        actor.id(),
        "PROOF_REGISTERED",
        key,
        Map.of("proofId", proof, "kind", body.kind()));
    Proof result =
        new Proof(proof, orderId, task.id(), body.kind(), "AVAILABLE", order.version() + 1);
    finish(actor.id(), "proof", key, orderId, result);
    return result;
  }

  @Transactional
  public Event location(String token, String orderId, String key, Location body) {
    var actor = identity.authorizeForWrite(token, "COURIER", true);
    validate(body);
    var order = lock(orderId);
    Task task = ownInOrder(body.taskId(), actor.id(), orderId);
    Event replay =
        requests.replay(
            actor.id(), "fulfillment:location", key, List.of(orderId, body), Event.class);
    // Replay is a read of a committed event; it cannot append after release.
    if (replay != null) return replay;
    active(task, order);
    state(
        body.eventType() == EventType.ARRIVAL
            ? order.status().equals("ACCEPTED")
            : order.status().equals("PICKED_UP"));
    var existing =
        mapper.findLocationRequest(id(task.id()), body.eventType().name(), body.requestId());
    if (!existing.isEmpty()) {
      var row = existing.get(0);
      if (!Objects.equals(row.get("coordinate_system"), body.coordinateSystem())
          || row.get("latitude") == null
          || ((BigDecimal) row.get("latitude")).compareTo(body.latitude()) != 0
          || ((BigDecimal) row.get("longitude")).compareTo(body.longitude()) != 0)
        throw new IdentityFailure(409, "IDEMPOTENCY_KEY_CONFLICT");
      Event result = readEvent(row.get("id").toString(), order.version());
      finish(actor.id(), "location", key, orderId, result);
      return result;
    }
    version(order.version(), body.expectedVersion());
    // Every arrival is retained, including rejected or unconfigured points.
    // Subsidy later also checks cancellation time and custody; acceptance alone never pays.
    String rejection;
    if (body.eventType() == EventType.ARRIVAL) {
      var point = order.addressSnapshot();
      rejection =
          point.latitude() == null || point.longitude() == null
              ? "SERVICE_LOCATION_UNCONFIGURED"
              : distanceMeters(
                          point.latitude().doubleValue(),
                          point.longitude().doubleValue(),
                          body.latitude().doubleValue(),
                          body.longitude().doubleValue())
                      <= 100
                  ? null
                  : "ARRIVAL_OUTSIDE_RADIUS";
    } else
      rejection =
          mapper.countAcceptedDeliveryLocations(id(task.id())) == 0
              ? null
              : "DELIVERY_LOCATION_ALREADY_SELECTED";
    boolean accepted = rejection == null;
    String event =
        mapper.insertLocationEvent(
            id(orderId),
            id(task.id()),
            id(actor.id()),
            body.eventType().name(),
            body.latitude(),
            body.longitude(),
            body.coordinateSystem(),
            now(),
            body.requestId(),
            accepted,
            rejection);
    mapper.advanceOrderVersion(now(), id(orderId));
    event(
        orderId,
        task.id(),
        actor.id(),
        "LOCATION_RECORDED",
        key,
        Map.of("eventId", event, "eventType", body.eventType()));
    Event result = readEvent(event, order.version() + 1);
    finish(actor.id(), "location", key, orderId, result);
    return result;
  }

  @Transactional
  public Result pickup(String token, String orderId, String key, Pickup body) {
    var actor = identity.authorizeForWrite(token, "COURIER", true);
    if (body == null) throw IdentityFailure.invalid();
    var order = lock(orderId);
    Task task = ownInOrder(body.taskId(), actor.id(), orderId);
    Result replay =
        requests.replay(
            actor.id(), "fulfillment:pickup", key, List.of(orderId, body), Result.class);
    if (replay != null) return replay;
    version(order.version(), body.expectedVersion());
    active(task, order);
    state(order.status().equals("ACCEPTED"));
    requireProof(orderId, task.id(), body.pickupProofId(), Kind.PICKUP);
    mapper.markTaskPickedUp(now(), id(task.id()));
    mapper.markOrderPickedUp(now(), id(orderId));
    event(
        orderId, task.id(), actor.id(), "PICKED_UP", key, Map.of("proofId", body.pickupProofId()));
    Result result = result(orderId, task.id());
    finish(actor.id(), "pickup", key, orderId, result);
    return result;
  }

  @Transactional
  public Result deliver(String token, String orderId, String key, Deliver body) {
    var actor = identity.authorizeForWrite(token, "COURIER", true);
    if (body == null) throw IdentityFailure.invalid();
    var order = lock(orderId);
    Task task = ownInOrder(body.taskId(), actor.id(), orderId);
    Result replay =
        requests.replay(
            actor.id(), "fulfillment:deliver", key, List.of(orderId, body), Result.class);
    if (replay != null) return replay;
    version(order.version(), body.expectedVersion());
    active(task, order);
    state(order.status().equals("PICKED_UP"));
    requireProof(orderId, task.id(), body.deliveryProofId(), Kind.DELIVERY);
    if (mapper.countValidDeliveryLocations(
            id(body.locationEventId()), id(orderId), id(task.id()), id(actor.id()))
        != 1) throw new IdentityFailure(409, "PROOF_NOT_READY");
    // Task status and order delivery commit together: pending money or review
    // may block confirmation but must never continue consuming rider capacity.
    mapper.markTaskDelivered(now(), now(), id(task.id()));
    OffsetDateTime start = blocked(orderId) ? null : now();
    mapper.markOrderDelivered(
        now(), start, start == null ? null : start.plusMinutes(10), now(), id(orderId));
    event(
        orderId,
        task.id(),
        actor.id(),
        "DELIVERED",
        key,
        Map.of("proofId", body.deliveryProofId(), "locationEventId", body.locationEventId()));
    notify(order.userId(), "delivered:" + task.id(), orderId, "ORDER_DELIVERED");
    Result result = result(orderId, task.id());
    finish(actor.id(), "deliver", key, orderId, result);
    return result;
  }

  @Transactional
  public com.zhangxuan.runrunv2.dto.response.fulfillment.FulfillmentModels.Order confirm(String token, String orderId, String key, Version body) {
    var actor = identity.authorizeForWrite(token, "USER", false);
    var order = lock(orderId);
    if (!actor.id().equals(order.userId())) throw missing();
    required(body == null ? null : body.expectedVersion());
    com.zhangxuan.runrunv2.dto.response.fulfillment.FulfillmentModels.Order replay =
        requests.replay(
            actor.id(), "fulfillment:confirm", key, List.of(orderId, body), com.zhangxuan.runrunv2.dto.response.fulfillment.FulfillmentModels.Order.class);
    if (replay != null) return replay;
    version(order.version(), body.expectedVersion());
    complete(orderId, false, actor.id(), key);
    com.zhangxuan.runrunv2.dto.response.fulfillment.FulfillmentModels.Order result = order(orderId);
    finish(actor.id(), "confirm", key, orderId, result);
    return result;
  }

  @Transactional
  public int autoComplete() {
    int completed = 0;
    var ids = mapper.findOrdersDueForConfirmation(now());
    for (String orderId : ids) {
      lock(orderId);
      com.zhangxuan.runrunv2.dto.response.fulfillment.FulfillmentModels.Order order = order(orderId);
      if (order.rawStatus().equals("DELIVERED_PENDING_CONFIRMATION")
          && order.confirmDeadline() != null
          && !now().isBefore(order.confirmDeadline())
          && !blocked(orderId)) {
        complete(orderId, true, null, "automatic:" + order.version());
        completed++;
      }
    }
    return completed;
  }

  /**
   * M05 calls under the SAME transaction as creating an adjustment/case. Uploading a specification
   * photograph alone deliberately never calls this.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void blockCompletion(String orderId, String type, String sourceId) {
    var order = lock(orderId);
    blockType(type);
    InputValidation.text(sourceId, 128);
    state(!Set.of("COMPLETED", "CANCELLED").contains(order.status()));
    if (mapper.insertCompletionBlock(id(orderId), type, sourceId) > 0)
      mapper.clearOrderConfirmation(now(), id(orderId));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void unblockCompletion(String orderId, String type, String sourceId) {
    lock(orderId);
    blockType(type);
    if (mapper.deleteCompletionBlock(id(orderId), type, sourceId) > 0) restartWindow(orderId);
  }

  /** After M03 financial resolution M05 rechecks ALL reviews/cases here. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void restartWindow(String orderId) {
    lock(orderId);
    com.zhangxuan.runrunv2.dto.response.fulfillment.FulfillmentModels.Order order = order(orderId);
    if (order.rawStatus().equals("DELIVERED_PENDING_CONFIRMATION")
        && order.confirmStartedAt() == null
        && !blocked(orderId))
      mapper.restartConfirmationWindow(now(), now().plusMinutes(10), now(), id(orderId));
  }

  /**
   * M05 must call again at adjustment registration, even if upload succeeded. Invoke before
   * acquiring other order locks: actor -> recipient -> order is shared with cancellation.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void authorizeSpecification(
      String token, String orderId, String taskId, long expectedVersion, String proofId) {
    var actor = identity.authorizeForWrite(token, "COURIER", true);
    var order = lock(orderId);
    Task task = ownInOrder(taskId, actor.id(), orderId);
    version(order.version(), expectedVersion);
    proofPermission(task, order, Kind.SPECIFICATION);
    requireProof(orderId, taskId, proofId, Kind.SPECIFICATION);
  }

  private void complete(String orderId, boolean automatic, String actor, String requestId) {
    com.zhangxuan.runrunv2.dto.response.fulfillment.FulfillmentModels.Order order = order(orderId);
    state(
        order.rawStatus().equals("DELIVERED_PENDING_CONFIRMATION") && order.deliveredAt() != null);
    if (adjustmentBlocked(orderId)) throw new IdentityFailure(409, "ADJUSTMENT_UNRESOLVED");
    if (caseBlocked(orderId)) throw new IdentityFailure(409, "AFTER_SALES_UNRESOLVED");
    state(
        order.confirmStartedAt() != null
            && order.confirmDeadline() != null
            && (!automatic || !now().isBefore(order.confirmDeadline())));
    mapper.markOrderCompleted(now(), now(), id(orderId));
    event(orderId, null, actor, "COMPLETED", requestId, Map.of("automatic", automatic));
  }

  private boolean blocked(String order) {
    return adjustmentBlocked(order) || caseBlocked(order);
  }

  private boolean adjustmentBlocked(String order) {
    return mapper.hasAdjustmentCompletionBlock(id(order), id(order));
  }

  private boolean caseBlocked(String order) {
    return mapper.hasAfterSalesCompletionBlock(id(order));
  }

  private void blockType(String type) {
    if (type == null || !Set.of("ADJUSTMENT", "AFTER_SALES").contains(type))
      throw IdentityFailure.invalid();
  }

  private void capacity(String courier) {
    // authorizeForWrite holds the courier ACCOUNT row until commit, so counts
    // serialize across orders/processes without a drifting capacity counter.
    if (capacity == 0 || mapper.countActiveCourierTasks(id(courier)) >= capacity)
      throw new IdentityFailure(409, "COURIER_CAPACITY_EXCEEDED");
  }

  private com.zhangxuan.runrunv2.dto.response.order.OrderModels.Order lock(String order) {
    // Same recipient-before-order lock order as M03 callbacks/cancellation.
    mapper.lockOrderUser(id(order));
    return orders.read(order, true);
  }

  private Task own(String task, String actor) {
    Task result = task(task);
    if (!result.courierId().equals(actor)) throw missing();
    return result;
  }

  private Task ownInOrder(String task, String actor, String order) {
    Task result = own(task, actor);
    if (!result.orderId().equals(order)) throw missing();
    return result;
  }

  private Task current(String actor, String order) {
    var ids = mapper.findLatestCourierTaskId(id(actor), id(order));
    if (ids.isEmpty()) throw missing();
    return task(ids.get(0));
  }

  private Task task(String task) {
    var rows = mapper.findTask(id(task));
    if (rows.isEmpty()) throw missing();
    return rows.get(0);
  }

  private boolean unsuperseded(String task) {
    return mapper.isTaskUnsuperseded(id(task));
  }

  private void active(Task task, com.zhangxuan.runrunv2.dto.response.order.OrderModels.Order order) {
    state(
        task.status().equals("ACTIVE")
            && unsuperseded(task.id())
            && Set.of("ACCEPTED", "PICKED_UP").contains(order.status()));
  }

  private void proofPermission(Task task, com.zhangxuan.runrunv2.dto.response.order.OrderModels.Order order, Kind kind) {
    if (kind == Kind.SPECIFICATION && task.status().equals("DELIVERED")) {
      state(
          order.status().equals("DELIVERED_PENDING_CONFIRMATION")
              && unsuperseded(task.id())
              && mapper.isLatestOrderTask(id(task.id()), id(order.id())));
      return;
    }
    active(task, order);
    if (kind == Kind.PICKUP) state(order.status().equals("ACCEPTED"));
    if (kind == Kind.DELIVERY) state(order.status().equals("PICKED_UP"));
  }

  private void requireProof(String order, String task, String proof, Kind kind) {
    if (mapper.countAvailableTaskProofs(id(proof), id(order), id(task), kind.name()) != 1)
      throw new IdentityFailure(409, "PROOF_NOT_READY");
  }

  private Binding binding(Task task, Kind kind) {
    return new Binding(task.orderId(), task.id(), task.courierId(), kind);
  }

  private void supersede(String order, String keep) {
    mapper.supersedeOtherTasks(now(), id(order), id(keep));
  }

  private void closeOffers(String order, String keep) {
    mapper.cancelOtherOffers(
        now(), id(order), keep == null ? null : id(keep), keep == null ? null : id(keep));
  }

  private void end(String task, String status) {
    mapper.endTask(status, now(), id(task));
  }

  private com.zhangxuan.runrunv2.dto.response.fulfillment.FulfillmentModels.Order order(String order) {
    var row = mapper.findOrder(id(order));
    return new com.zhangxuan.runrunv2.dto.response.fulfillment.FulfillmentModels.Order(
        row.id(),
        row.orderNo(),
        row.flowVersion(),
        row.rawStatus(),
        displayStatus(row.rawStatus(), order),
        row.version(),
        row.originalFeeCents(),
        row.effectiveFeeCents(),
        row.deliveredAt(),
        row.confirmStartedAt(),
        row.confirmDeadline());
  }

  private String displayStatus(String status, String orderId) {
    if (status.equals("PICKED_UP")) return "DELIVERING";
    if (!status.equals("DELIVERED_PENDING_CONFIRMATION")) return status;
    if (mapper.hasPendingSupplement(id(orderId))) return "PENDING_SUPPLEMENT";
    return adjustmentBlocked(orderId) ? "ADJUSTMENT_PROCESSING" : "PENDING_CONFIRMATION";
  }

  private Result result(String order, String task) {
    com.zhangxuan.runrunv2.dto.response.fulfillment.FulfillmentModels.Order o = order(order);
    return new Result(o, task == null ? null : task(task), o.confirmDeadline());
  }

  private Event readEvent(String event, long version) {
    return mapper.findLocationEvent(id(event), version);
  }

  private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
    double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
    double a =
        Math.pow(Math.sin(dLat / 2), 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(dLon / 2), 2);
    return 6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0, 1 - a)));
  }

  private void validate(Location b) {
    if (b == null
        || b.eventType() == null
        || b.latitude() == null
        || b.longitude() == null
        || b.latitude().scale() > 7
        || b.longitude().scale() > 7
        || b.latitude().abs().compareTo(BigDecimal.valueOf(90)) > 0
        || b.longitude().abs().compareTo(BigDecimal.valueOf(180)) > 0
        || !"GCJ02".equals(b.coordinateSystem())) throw IdentityFailure.invalid();
    InputValidation.text(b.requestId(), 128);
    required(b.expectedVersion());
  }

  private void finish(String actor, String scope, String key, String order, Object result) {
    requests.finish(actor, "fulfillment:" + scope, key, "V2_ORDER", order, result);
  }

  private void event(
      String order, String task, String actor, String type, String key, Object payload) {
    mapper.insertOrderEvent(
        id(order),
        task == null ? null : id(task),
        actor == null ? null : id(actor),
        "FULFILLMENT_" + type,
        key,
        json.write(payload));
  }

  private void notify(String recipient, String key, String order, String type) {
    mapper.insertNotificationIfAbsent(
        id(recipient), "fulfillment:" + key, json.write(Map.of("orderId", order, "kind", type)));
  }

  private OffsetDateTime now() {
    return clock
        .instant()
        .truncatedTo(ChronoUnit.MICROS)
        .atOffset(ZoneOffset.UTC);
  }

  private static Long id(String value) {
    return ResourceIds.parse(value);
  }

  private static void required(Long value) {
    if (value == null || value < 0) throw IdentityFailure.invalid();
  }

  private static void version(long actual, Long expected) {
    required(expected);
    if (actual != expected) throw conflict();
  }

  private static void state(boolean allowed) {
    if (!allowed) throw conflict();
  }

  private static IdentityFailure conflict() {
    return new IdentityFailure(409, "ORDER_STATE_CONFLICT");
  }

  private static IdentityFailure missing() {
    return new IdentityFailure(404, "RESOURCE_NOT_FOUND");
  }
}
