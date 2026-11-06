package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.constant.AdminOrderConstants;
import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.model.OrderExport;
import com.zhangxuan.runruncampus.repository.OrderEventRepository;
import com.zhangxuan.runruncampus.repository.OrderIssueRepository;
import com.zhangxuan.runruncampus.repository.OrderProofRepository;
import com.zhangxuan.runruncampus.repository.OrderRepository;
import com.zhangxuan.runruncampus.vo.AdminOrderDetailResponse;
import com.zhangxuan.runruncampus.vo.AdminOrderListItem;
import com.zhangxuan.runruncampus.vo.AdminOrderPageResponse;
import com.zhangxuan.runruncampus.vo.OrderEventItem;
import com.zhangxuan.runruncampus.vo.OrderIssueItem;
import com.zhangxuan.runruncampus.vo.OrderProofItem;
import com.zhangxuan.runruncampus.vo.UserSummary;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface AdminOrderService {

    AdminOrderPageResponse list(String query, OrderStatus status, boolean archived, int page, int size);

    AdminOrderDetailResponse detail(Long id);

    OrderExport exportCsv(String query, OrderStatus status, boolean archived);
}
