package com.zhangxuan.runruncampus.membership;

public enum MembershipPlanType {
    LIGHT("轻享卡", 10, 1600, "适合偶尔点餐"),
    STUDENT("学生月卡", 20, 2900, "最受欢迎"),
    ROOMMATE("宿舍搭子", 30, 3900, "30 次大额套餐");

    private final String title;
    private final int trips;
    private final int priceCents;
    private final String note;

    MembershipPlanType(String title, int trips, int priceCents, String note) {
        this.title = title;
        this.trips = trips;
        this.priceCents = priceCents;
        this.note = note;
    }

    public String getTitle() { return title; }
    public int getTrips() { return trips; }
    public int getPriceCents() { return priceCents; }
    public String getNote() { return note; }
}
