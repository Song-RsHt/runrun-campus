package com.zhangxuan.runrunv2.service;

import com.zhangxuan.runrunv2.dto.OrderQuoteRequest;
import com.zhangxuan.runrunv2.dto.ServiceConfigChangeRequest;
import com.zhangxuan.runrunv2.model.OrderPricing;
import com.zhangxuan.runrunv2.vo.OrderQuoteVO;
import com.zhangxuan.runrunv2.vo.ServiceConfigVO;
import java.util.Map;








































/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface ServiceConfigService {

    // Updates lock the singleton first and commit all zone/point changes with one version.
    Map<String, Object> settings(boolean update);

    ServiceConfigVO config(String token);

    OrderQuoteVO quote(String token, OrderQuoteRequest request);

    OrderPricing price(OrderQuoteRequest request, Map<String, Object> settings);

    Map<String, Object> zone(String code);

    Map<String, Object> point(String code);

    void allowNewOrder(Map<String, Object> settings);

    Map<String, Object> policy(Map<String, Object> settings);

    ServiceConfigVO patch(String token, String key, ServiceConfigChangeRequest change);
}
