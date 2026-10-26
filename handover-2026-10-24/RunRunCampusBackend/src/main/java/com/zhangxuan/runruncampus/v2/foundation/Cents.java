package com.zhangxuan.runruncampus.v2.foundation;

import com.fasterxml.jackson.databind.JsonNode;

public final class Cents {
    private Cents() {}
    public static long nonNegative(long value) {
        if (value < 0) throw new V2Exception(V2Error.VALIDATION_ERROR, "金额必须为非负整数分");
        return value;
    }
    public static long parse(JsonNode value) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong())
            throw new V2Exception(V2Error.VALIDATION_ERROR, "金额必须为整数分");
        return nonNegative(value.longValue());
    }
    public static long add(long first, long second) {
        try { return Math.addExact(nonNegative(first), nonNegative(second)); }
        catch (ArithmeticException e) { throw new V2Exception(V2Error.VALIDATION_ERROR, "金额超出范围"); }
    }
    /** C M46: quotient/remainder avoids overflow; the rider absorbs the rounding remainder. */
    public static Split split(long value) {
        nonNegative(value);
        long platform = value / 100 * 30 + value % 100 * 30 / 100;
        return new Split(platform, value - platform);
    }
    public record Split(long platformCents, long courierCents) {}
}
