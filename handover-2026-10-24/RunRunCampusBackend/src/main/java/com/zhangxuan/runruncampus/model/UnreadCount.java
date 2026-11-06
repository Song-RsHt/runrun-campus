package com.zhangxuan.runruncampus.model;

/** 消息未读数量投影；仅承载内部协作所需的数据或校验逻辑。 */
public interface UnreadCount {

    Long getOrderId();

    long getUnreadCount();
}
