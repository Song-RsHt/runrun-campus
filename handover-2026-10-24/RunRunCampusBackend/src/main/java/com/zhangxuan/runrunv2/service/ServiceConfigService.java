package com.zhangxuan.runrunv2.service;

import com.zhangxuan.runrunv2.config.OrderJson;
import com.zhangxuan.runrunv2.dto.ConfigChange;
import com.zhangxuan.runrunv2.dto.PointChange;
import com.zhangxuan.runrunv2.dto.QuoteRequest;
import com.zhangxuan.runrunv2.dto.ZoneChange;
import com.zhangxuan.runrunv2.enums.ServiceType;
import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.integration.idempotency.OrderIdempotency;
import com.zhangxuan.runrunv2.mapper.ServiceConfigMapper;
import com.zhangxuan.runrunv2.model.InputValidation;
import com.zhangxuan.runrunv2.model.OrderInput;
import com.zhangxuan.runrunv2.model.Pricing;
import com.zhangxuan.runrunv2.model.Schedule;
import com.zhangxuan.runrunv2.vo.OrderQuoteVO;
import com.zhangxuan.runrunv2.vo.ServiceConfigVO;
import com.zhangxuan.runrunv2.vo.ServiceOptionVO;
import java.math.BigDecimal;
import java.sql.Time;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface ServiceConfigService {

    // Updates lock the singleton first and commit all zone/point changes with one version.
    Map<String, Object> settings(boolean update);

    ServiceConfigVO config(String token);

    OrderQuoteVO quote(String token, QuoteRequest request);

    Pricing price(QuoteRequest request, Map<String, Object> settings);

    Map<String, Object> zone(String code);

    Map<String, Object> point(String code);

    void allowNewOrder(Map<String, Object> settings);

    Map<String, Object> policy(Map<String, Object> settings);

    ServiceConfigVO patch(String token, String key, ConfigChange change);
}
