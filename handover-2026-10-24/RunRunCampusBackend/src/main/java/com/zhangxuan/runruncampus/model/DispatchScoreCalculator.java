package com.zhangxuan.runruncampus.model;

import com.zhangxuan.runruncampus.constant.AdminDispatchConstants;
import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.entity.OrderEvent;
import com.zhangxuan.runruncampus.enums.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.AdminAuditTargetType;
import com.zhangxuan.runruncampus.enums.OrderMessageType;
import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.mapper.AdminDispatchMapper;
import com.zhangxuan.runruncampus.repository.OrderEventRepository;
import com.zhangxuan.runruncampus.repository.OrderRepository;
import com.zhangxuan.runruncampus.repository.UserRepository;
import com.zhangxuan.runruncampus.service.AdminAuditService;
import com.zhangxuan.runruncampus.service.CourierCertificationService;
import com.zhangxuan.runruncampus.service.OrderMessageService;
import com.zhangxuan.runruncampus.service.PlatformSettingsService;
import com.zhangxuan.runruncampus.vo.DispatchCandidateVO;
import com.zhangxuan.runruncampus.vo.DispatchCandidatesVO;
import com.zhangxuan.runruncampus.vo.DispatchResultVO;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

public final class DispatchScoreCalculator {

    private DispatchScoreCalculator() {
    }

    public static int recommendationScore(long activeOrders, long onlineMinutes, long completedOrders, long matchedCompletedOrders, Double averageRating, long ratingCount) {
        long score = 60;
        score -= Math.min(Math.max(activeOrders, 0), 2) * 45;
        score += Math.min(Math.max(onlineMinutes, 0) / 12, 10);
        score += Math.min(Math.max(completedOrders, 0) / 4, 10);
        score += Math.min(Math.max(matchedCompletedOrders, 0) / 2, 10);
        if (averageRating != null && ratingCount >= 3) {
            score += Math.min(10, Math.max(0, Math.round((averageRating - 3.0) * 5)));
        }
        return (int) Math.min(100, Math.max(0, score));
    }
}
