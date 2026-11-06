package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.entity.CourierCertification;
import com.zhangxuan.runruncampus.enums.CourierCertificationStatus;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.model.CourierCompletedOrderStats;
import com.zhangxuan.runruncampus.model.CourierPackagePerformanceStats;
import com.zhangxuan.runruncampus.repository.CourierCertificationRepository;
import com.zhangxuan.runruncampus.repository.OrderRepository;
import com.zhangxuan.runruncampus.repository.UserRepository;
import com.zhangxuan.runruncampus.vo.AdminCourierItem;
import com.zhangxuan.runruncampus.vo.AdminCourierOrderItem;
import com.zhangxuan.runruncampus.vo.AdminCourierOrderPage;
import com.zhangxuan.runruncampus.vo.AdminCourierPageResponse;
import com.zhangxuan.runruncampus.vo.AdminCourierPerformanceResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.function.Function;
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
public interface AdminCourierService {

    AdminCourierPageResponse list(String query, CourierCertificationStatus status, int page, int size);

    AdminCourierPerformanceResponse performance(Long courierId, int page, int size);
}
