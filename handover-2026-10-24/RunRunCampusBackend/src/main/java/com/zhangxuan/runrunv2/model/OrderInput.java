package com.zhangxuan.runrunv2.model;

import com.zhangxuan.runrunv2.dto.OrderQuoteRequest;
import com.zhangxuan.runrunv2.exception.IdentityFailure;

import java.util.Map;

/** 服务配置输入校验；仅承载内部协作所需的数据或校验逻辑。 */
public final class OrderInput {

    private OrderInput() {}

    public static void validate(OrderQuoteRequest request) {
        if (request == null || request.serviceType() == null) {
            throw IdentityFailure.invalid();
        }
        OrderInput.code(request.zoneCode());
        OrderInput.code(request.servicePointCode());
        InputValidation.text(request.destination(), 500);
    }

    public static long id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw IdentityFailure.invalid();
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw IdentityFailure.invalid();
        }
    }

    public static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    public static void code(String value) {
        if (value == null || !value.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw IdentityFailure.invalid();
        }
    }
}
