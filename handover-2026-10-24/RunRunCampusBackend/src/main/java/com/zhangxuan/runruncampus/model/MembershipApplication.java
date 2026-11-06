package com.zhangxuan.runruncampus.model;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.UserMembership;
import com.zhangxuan.runruncampus.enums.MembershipPlanType;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.repository.UserMembershipRepository;
import com.zhangxuan.runruncampus.repository.UserRepository;
import com.zhangxuan.runruncampus.vo.ActiveMembershipResponse;
import com.zhangxuan.runruncampus.vo.MembershipOverview;
import com.zhangxuan.runruncampus.vo.MembershipPlanVO;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

public record MembershipApplication(boolean used, String planTitle, Long membershipId) {

    public MembershipApplication(boolean used, String planTitle) {
        this(used, planTitle, null);
    }
}
