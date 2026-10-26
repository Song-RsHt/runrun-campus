package com.zhangxuan.runruncampus.v2.foundation;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

public final class V2Time {
    private V2Time() {}
    /** UTC ISO-8601 includes the explicit Z offset and never uses the host default zone. */
    public static String format(Instant time) { return time == null ? null : DateTimeFormatter.ISO_INSTANT.format(time); }
}
