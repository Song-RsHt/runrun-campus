package com.zhangxuan.runruncampus.enums;

/** 信用分及风险信号风险级别枚举；名称和取值沿用既有业务与协议定义。 */
public enum CreditRiskLevel {
    NORMAL,
    WATCH,
    HIGH_RISK;

    public static CreditRiskLevel fromScore(int score) {
        if (score >= 80) {
            return NORMAL;
        }
        if (score >= 60) {
            return WATCH;
        }
        return HIGH_RISK;
    }
}
