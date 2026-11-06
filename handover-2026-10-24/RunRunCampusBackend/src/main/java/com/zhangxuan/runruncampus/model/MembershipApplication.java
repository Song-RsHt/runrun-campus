package com.zhangxuan.runruncampus.model;





















public record MembershipApplication(boolean used, String planTitle, Long membershipId) {

    public MembershipApplication(boolean used, String planTitle) {
        this(used, planTitle, null);
    }
}
