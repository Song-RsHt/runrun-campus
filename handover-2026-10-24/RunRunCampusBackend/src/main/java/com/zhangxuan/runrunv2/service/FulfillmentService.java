package com.zhangxuan.runrunv2.service;

import com.zhangxuan.runrunv2.dto.FulfillmentDeliverRequest;
import com.zhangxuan.runrunv2.dto.FulfillmentLocationRequest;
import com.zhangxuan.runrunv2.dto.FulfillmentPickupRequest;
import com.zhangxuan.runrunv2.dto.FulfillmentReleaseRequest;
import com.zhangxuan.runrunv2.dto.FulfillmentRespondRequest;
import com.zhangxuan.runrunv2.dto.FulfillmentUploadRequest;
import com.zhangxuan.runrunv2.dto.FulfillmentVersionRequest;
import com.zhangxuan.runrunv2.dto.RegisterFulfillmentProofRequest;
import com.zhangxuan.runrunv2.vo.FulfillmentOrderVO;
import com.zhangxuan.runrunv2.vo.FulfillmentResultVO;
import com.zhangxuan.runrunv2.vo.FulfillmentTaskVO;
import com.zhangxuan.runrunv2.vo.LocationEventVO;
import com.zhangxuan.runrunv2.vo.OrderProofVO;
import com.zhangxuan.runrunv2.vo.UploadTicketVO;



























































/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface FulfillmentService {

    FulfillmentResultVO accept(String token, String orderId, String key, FulfillmentVersionRequest body);

    /**
     * M05 invokes this only after an audited exception/refulfillment decision. It creates a fresh
     * invitation; historical tasks are never reactivated.
     */
    FulfillmentTaskVO invite(String token, String orderId, String courierId, long expectedVersion, String reason, String key);

    FulfillmentResultVO respond(String token, String taskId, String key, FulfillmentRespondRequest body);

    FulfillmentResultVO release(String token, String orderId, String key, FulfillmentReleaseRequest body);

    UploadTicketVO upload(String token, String orderId, String key, FulfillmentUploadRequest body);

    OrderProofVO proof(String token, String orderId, String key, RegisterFulfillmentProofRequest body);

    LocationEventVO location(String token, String orderId, String key, FulfillmentLocationRequest body);

    FulfillmentResultVO pickup(String token, String orderId, String key, FulfillmentPickupRequest body);

    FulfillmentResultVO deliver(String token, String orderId, String key, FulfillmentDeliverRequest body);

    FulfillmentOrderVO confirm(String token, String orderId, String key, FulfillmentVersionRequest body);

    int autoComplete();

    /**
     * M05 calls under the SAME transaction as creating an adjustment/case. Uploading a specification
     * photograph alone deliberately never calls this.
     */
    void blockCompletion(String orderId, String type, String sourceId);

    void unblockCompletion(String orderId, String type, String sourceId);

    /**
     * After M03 financial resolution M05 rechecks ALL reviews/cases here.
     */
    void restartWindow(String orderId);

    /**
     * M05 must call again at adjustment registration, even if upload succeeded. Invoke before
     * acquiring other order locks: actor -> recipient -> order is shared with cancellation.
     */
    void authorizeSpecification(String token, String orderId, String taskId, long expectedVersion, String proofId);
}
