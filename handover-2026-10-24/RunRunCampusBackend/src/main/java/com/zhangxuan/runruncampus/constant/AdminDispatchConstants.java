package com.zhangxuan.runruncampus.constant;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.entity.OrderEvent;
import com.zhangxuan.runruncampus.enums.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.AdminAuditTargetType;
import com.zhangxuan.runruncampus.enums.OrderMessageType;
import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.mapper.AdminDispatchMapper;
import com.zhangxuan.runruncampus.model.DispatchScoreCalculator;
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

public final class AdminDispatchConstants {

    private AdminDispatchConstants() {
    }

    public static final int PAGE_SIZE = 20;
}
