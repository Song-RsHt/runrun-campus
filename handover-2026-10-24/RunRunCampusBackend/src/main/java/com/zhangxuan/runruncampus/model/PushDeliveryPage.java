package com.zhangxuan.runruncampus.model;

import com.zhangxuan.runruncampus.enums.PushDeliveryStatus;
import com.zhangxuan.runruncampus.enums.PushDestination;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

public record PushDeliveryPage(List<PushDeliveryItem> items, int page, int size, long totalElements, int totalPages) {
}
