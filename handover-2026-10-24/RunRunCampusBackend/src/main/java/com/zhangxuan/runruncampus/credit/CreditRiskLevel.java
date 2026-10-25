package com.zhangxuan.runruncampus.credit;

public enum CreditRiskLevel {
    NORMAL,
    WATCH,
    HIGH_RISK;

    public static CreditRiskLevel fromScore(int score) {
        if (score >= 80) return NORMAL;
        if (score >= 60) return WATCH;
        return HIGH_RISK;
    }
}
