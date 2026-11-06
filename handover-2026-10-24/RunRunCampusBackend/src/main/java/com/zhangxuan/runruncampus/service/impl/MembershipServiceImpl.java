package com.zhangxuan.runruncampus.service.impl;

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
import com.zhangxuan.runruncampus.service.MembershipService;

@Service
public class MembershipServiceImpl implements MembershipService {

    private final UserMembershipRepository membershipRepository;

    private final UserRepository userRepository;

    public MembershipServiceImpl(UserMembershipRepository membershipRepository, UserRepository userRepository) {
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public MembershipOverview overview(AppUser customer) {
        requireCustomer(customer);
        Instant now = Instant.now();
        ActiveMembershipResponse active = membershipRepository.findFirstByUserOrderByPurchasedAtDesc(customer).filter(membership -> membership.isActive(now)).map(membership -> response(membership, now)).orElse(null);
        List<MembershipPlanVO> plans = Arrays.stream(MembershipPlanType.values()).map(this::planResponse).toList();
        return new MembershipOverview(active, plans);
    }

    @Transactional
    @Override
    public ActiveMembershipResponse purchase(AppUser customer, MembershipPlanType plan) {
        validateCanPurchase(customer);
        return activatePaidPurchase(customer, plan);
    }

    @Transactional
    @Override
    public void validateCanPurchase(AppUser customer) {
        requireCustomer(customer);
        AppUser lockedCustomer = userRepository.findLockedById(customer.getId()).orElseThrow(() -> new BusinessFailure(404, "用户不存在"));
        Instant now = Instant.now();
        if (membershipRepository.findActiveLocked(lockedCustomer, now).isPresent()) {
            throw new BusinessFailure(409, "当前已有有效月卡");
        }
    }

    @Transactional
    @Override
    public ActiveMembershipResponse activatePaidPurchase(AppUser customer, MembershipPlanType plan) {
        requireCustomer(customer);
        AppUser lockedCustomer = userRepository.findLockedById(customer.getId()).orElseThrow(() -> new BusinessFailure(404, "用户不存在"));
        Instant now = Instant.now();
        if (membershipRepository.findActiveLocked(lockedCustomer, now).isPresent()) {
            throw new BusinessFailure(409, "当前已有有效月卡");
        }
        UserMembership membership = membershipRepository.save(new UserMembership(lockedCustomer, plan, now.plus(30, ChronoUnit.DAYS)));
        return response(membership, now);
    }

    @Transactional
    @Override
    public MembershipApplication apply(AppUser customer, boolean useMembership) {
        requireCustomer(customer);
        if (!useMembership)
            return new MembershipApplication(false, null);
        UserMembership membership = membershipRepository.findActiveLocked(customer, Instant.now()).orElseThrow(() -> new BusinessFailure(409, "没有可用的月卡次数"));
        membership.consumeTrip();
        return new MembershipApplication(true, membership.getPlan().getTitle(), membership.getId());
    }

    @Transactional
    @Override
    public void refund(AppUser customer, Long membershipId) {
        requireCustomer(customer);
        if (membershipId == null) {
            throw new BusinessFailure(409, "订单缺少月卡使用记录");
        }
        UserMembership membership = membershipRepository.findLockedById(membershipId).orElseThrow(() -> new BusinessFailure(409, "订单使用的月卡不存在"));
        if (!membership.belongsTo(customer)) {
            throw new BusinessFailure(409, "订单与月卡归属不一致");
        }
        membership.refundTrip();
    }

    private ActiveMembershipResponse response(UserMembership membership, Instant now) {
        return new ActiveMembershipResponse(membership.getId(), membership.getPlan(), membership.getPlan().getTitle(), membership.getTotalTrips(), membership.getRemainingTrips(), membership.getPriceCents(), membership.getPurchasedAt(), membership.getExpiresAt(), membership.isActive(now));
    }

    private MembershipPlanVO planResponse(MembershipPlanType plan) {
        return new MembershipPlanVO(plan, plan.getTitle(), plan.getTrips(), plan.getPriceCents(), plan.getNote());
    }

    private void requireCustomer(AppUser user) {
        if (user.getRole() != UserRole.CUSTOMER) {
            throw new BusinessFailure(403, "只有用户账号可以使用月卡");
        }
    }
}
