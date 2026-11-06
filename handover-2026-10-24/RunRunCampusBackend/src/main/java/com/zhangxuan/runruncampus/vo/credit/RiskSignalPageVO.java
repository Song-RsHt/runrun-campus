package com.zhangxuan.runruncampus.vo.credit;

import java.util.List;

/** 风险信号分页响应；保持现有 JSON 字段及序列化契约。 */
public record RiskSignalPageVO(
        List<RiskSignalVO> items, int page, int size, long totalElements, int totalPages) {}
