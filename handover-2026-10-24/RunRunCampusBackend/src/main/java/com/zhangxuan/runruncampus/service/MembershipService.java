package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.UserMembership;
import com.zhangxuan.runruncampus.enums.MembershipPlanType;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.model.MembershipApplication;
import com.zhangxuan.runruncampus.repository.UserMembershipRepository;
import com.zhangxuan.runruncampus.repository.UserRepository;
import com.zhangxuan.runruncampus.vo.ActiveMembershipResponse;
import com.zhangxuan.runruncampus.vo.MembershipOverview;
import com.zhangxuan.runruncampus.vo.MembershipPlanVO;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.zhangxuan.runruncampus.exception.BusinessFailure;

/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface MembershipService {

    MembershipOverview overview(AppUser customer);

    ActiveMembershipResponse purchase(AppUser customer, MembershipPlanType plan);

    void validateCanPurchase(AppUser customer);

    ActiveMembershipResponse activatePaidPurchase(AppUser customer, MembershipPlanType plan);

    MembershipApplication apply(AppUser customer, boolean useMembership);

    void refund(AppUser customer, Long membershipId);
}
