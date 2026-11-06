package com.zhangxuan.runrunv2.service;

import com.zhangxuan.runrunv2.config.OrderJson;
import com.zhangxuan.runrunv2.dto.CreateRequest;
import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.integration.idempotency.OrderIdempotency;
import com.zhangxuan.runrunv2.mapper.OrderMapper;
import com.zhangxuan.runrunv2.mapper.OrderStore;
import com.zhangxuan.runrunv2.model.Address;
import com.zhangxuan.runrunv2.model.OrderInput;
import com.zhangxuan.runrunv2.model.Pricing;
import com.zhangxuan.runrunv2.vo.CreatedOrderVO;
import com.zhangxuan.runrunv2.vo.OrderVO;
import com.zhangxuan.runrunv2.vo.OriginalPaymentVO;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface OrderService {

    CreatedOrderVO create(String token, String key, CreateRequest request);

    OrderVO get(String token, String id);
}
