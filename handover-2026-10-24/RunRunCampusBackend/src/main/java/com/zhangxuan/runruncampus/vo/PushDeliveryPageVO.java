package com.zhangxuan.runruncampus.vo;

import java.util.List;

public record PushDeliveryPageVO(List<PushDeliveryItemVO> items, int page, int size, long totalElements, int totalPages) {
}
