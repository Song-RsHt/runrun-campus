package com.zhangxuan.runruncampus.membership;

import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.zhangxuan.runruncampus.user.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

@Service
public class MembershipService {
    private final UserMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public MembershipService(UserMembershipRepository membershipRepository, UserRepository userRepository) {
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public MembershipOverview overview(AppUser customer) {
        requireCustomer(customer);
        Instant now = Instant.now();
        ActiveMembershipResponse active = membershipRepository.findFirstByUserOrderByPurchasedAtDesc(customer)
                .filter(membership -> membership.isActive(now))
                .map(membership -> response(membership, now))
                .orElse(null);
        List<PlanResponse> plans = Arrays.stream(MembershipPlanType.values()).map(this::planResponse).toList();
        return new MembershipOverview(active, plans);
    }

    @Transactional
    public ActiveMembershipResponse purchase(AppUser customer, MembershipPlanType plan) {
        validateCanPurchase(customer);
        return activatePaidPurchase(customer, plan);
    }

    @Transactional
    public void validateCanPurchase(AppUser customer) {
        requireCustomer(customer);
        AppUser lockedCustomer = userRepository.findLockedById(customer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        Instant now = Instant.now();
        if (membershipRepository.findActiveLocked(lockedCustomer, now).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前已有有效月卡");
        }
    }

    @Transactional
    public ActiveMembershipResponse activatePaidPurchase(AppUser customer, MembershipPlanType plan) {
        requireCustomer(customer);
        AppUser lockedCustomer = userRepository.findLockedById(customer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        Instant now = Instant.now();
        if (membershipRepository.findActiveLocked(lockedCustomer, now).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前已有有效月卡");
        }
        UserMembership membership = membershipRepository.save(
                new UserMembership(lockedCustomer, plan, now.plus(30, ChronoUnit.DAYS)));
        return response(membership, now);
    }

    @Transactional
    public MembershipApplication apply(AppUser customer, boolean useMembership) {
        requireCustomer(customer);
        if (!useMembership) return new MembershipApplication(false, null);
        UserMembership membership = membershipRepository.findActiveLocked(customer, Instant.now())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "没有可用的月卡次数"));
        membership.consumeTrip();
        return new MembershipApplication(true, membership.getPlan().getTitle(), membership.getId());
    }

    @Transactional
    public void refund(AppUser customer, Long membershipId) {
        requireCustomer(customer);
        if (membershipId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单缺少月卡使用记录");
        }
        UserMembership membership = membershipRepository.findLockedById(membershipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "订单使用的月卡不存在"));
        if (!membership.belongsTo(customer)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单与月卡归属不一致");
        }
        membership.refundTrip();
    }

    private ActiveMembershipResponse response(UserMembership membership, Instant now) {
        return new ActiveMembershipResponse(membership.getId(), membership.getPlan(),
                membership.getPlan().getTitle(), membership.getTotalTrips(), membership.getRemainingTrips(),
                membership.getPriceCents(), membership.getPurchasedAt(), membership.getExpiresAt(),
                membership.isActive(now));
    }

    private PlanResponse planResponse(MembershipPlanType plan) {
        return new PlanResponse(plan, plan.getTitle(), plan.getTrips(), plan.getPriceCents(), plan.getNote());
    }

    private void requireCustomer(AppUser user) {
        if (user.getRole() != UserRole.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有用户账号可以使用月卡");
        }
    }

    public record MembershipOverview(ActiveMembershipResponse active, List<PlanResponse> plans) { }
    public record PlanResponse(MembershipPlanType type, String title, int trips, int priceCents, String note) { }
    public record ActiveMembershipResponse(Long id, MembershipPlanType plan, String planTitle,
                                           int totalTrips, int remainingTrips, int priceCents,
                                           Instant purchasedAt, Instant expiresAt, boolean active) { }
    public record MembershipApplication(boolean used, String planTitle, Long membershipId) {
        public MembershipApplication(boolean used, String planTitle) {
            this(used, planTitle, null);
        }
    }
}
