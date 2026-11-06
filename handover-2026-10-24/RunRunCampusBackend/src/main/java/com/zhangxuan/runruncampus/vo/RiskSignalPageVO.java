package com.zhangxuan.runruncampus.vo;

import java.util.List;



























public record RiskSignalPageVO(List<RiskSignalVO> items, int page, int size, long totalElements, int totalPages) {
}
