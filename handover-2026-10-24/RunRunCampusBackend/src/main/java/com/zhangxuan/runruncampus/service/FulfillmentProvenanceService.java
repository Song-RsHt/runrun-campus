package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.dto.CreateRequest;
import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.Channel;
import com.zhangxuan.runruncampus.enums.OrderPaymentMethod;
import com.zhangxuan.runruncampus.enums.OrderPaymentStatus;
import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.mapper.FulfillmentProvenanceMapper;
import com.zhangxuan.runruncampus.repository.OrderRepository;
import com.zhangxuan.runruncampus.vo.Entry;
import com.zhangxuan.runruncampus.vo.Summary;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.zhangxuan.runruncampus.exception.BusinessFailure;

/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface FulfillmentProvenanceService {

    Entry record(AppUser administrator, CreateRequest request);

    Entry verify(AppUser administrator, long id);

    List<Entry> list(int page);

    Summary summary();
}
