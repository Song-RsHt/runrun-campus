package com.zhangxuan.runrunv2.model;

import com.zhangxuan.runrunv2.config.OrderJson;
import com.zhangxuan.runrunv2.dto.ConfigChange;
import com.zhangxuan.runrunv2.dto.PointChange;
import com.zhangxuan.runrunv2.dto.QuoteRequest;
import com.zhangxuan.runrunv2.dto.ZoneChange;
import com.zhangxuan.runrunv2.enums.ServiceType;
import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.integration.idempotency.OrderIdempotency;
import com.zhangxuan.runrunv2.mapper.ServiceConfigMapper;
import com.zhangxuan.runrunv2.service.IdentityService;
import com.zhangxuan.runrunv2.service.OriginalPaymentWriter;
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

public final class OrderInput {

    private OrderInput() {
    }

    public static void validate(QuoteRequest request) {
        if (request == null || request.serviceType() == null)
            throw IdentityFailure.invalid();
        OrderInput.code(request.zoneCode());
        OrderInput.code(request.servicePointCode());
        InputValidation.text(request.destination(), 500);
    }

    public static long id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}"))
            throw IdentityFailure.invalid();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw IdentityFailure.invalid();
        }
    }

    public static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    static public void code(String value) {
        if (value == null || !value.matches("[A-Z][A-Z0-9_]{0,63}"))
            throw IdentityFailure.invalid();
    }
}
