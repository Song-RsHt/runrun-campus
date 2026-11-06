package com.zhangxuan.runrunv2.service;

import com.zhangxuan.runrunv2.dto.Deliver;
import com.zhangxuan.runrunv2.dto.Location;
import com.zhangxuan.runrunv2.dto.Pickup;
import com.zhangxuan.runrunv2.dto.RegisterProof;
import com.zhangxuan.runrunv2.dto.Release;
import com.zhangxuan.runrunv2.dto.Respond;
import com.zhangxuan.runrunv2.dto.Upload;
import com.zhangxuan.runrunv2.dto.Version;
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

    FulfillmentResultVO accept(String token, String orderId, String key, Version body);

    /**
     * M05 invokes this only after an audited exception/refulfillment decision. It creates a fresh
     * invitation; historical tasks are never reactivated.
     */
    FulfillmentTaskVO invite(String token, String orderId, String courierId, long expectedVersion, String reason, String key);

    FulfillmentResultVO respond(String token, String taskId, String key, Respond body);

    FulfillmentResultVO release(String token, String orderId, String key, Release body);

    UploadTicketVO upload(String token, String orderId, String key, Upload body);

    OrderProofVO proof(String token, String orderId, String key, RegisterProof body);

    LocationEventVO location(String token, String orderId, String key, Location body);

    FulfillmentResultVO pickup(String token, String orderId, String key, Pickup body);

    FulfillmentResultVO deliver(String token, String orderId, String key, Deliver body);

    FulfillmentOrderVO confirm(String token, String orderId, String key, Version body);

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
