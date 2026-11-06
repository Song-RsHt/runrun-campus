package com.zhangxuan.runruncampus.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.UserMembership;
import com.zhangxuan.runruncampus.enums.MembershipPlanType;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.model.MembershipApplication;
import com.zhangxuan.runruncampus.repository.UserMembershipRepository;
import com.zhangxuan.runruncampus.repository.UserRepository;
import com.zhangxuan.runruncampus.service.impl.MembershipServiceImpl;
import com.zhangxuan.runruncampus.vo.ActiveMembershipResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {

    @Mock private UserMembershipRepository membershipRepository;

    @Mock private UserRepository userRepository;

    private MembershipService membershipService;

    private AppUser customer;

    @BeforeEach
    void setUp() {
        membershipService = new MembershipServiceImpl(membershipRepository, userRepository);
        customer = user(1L, UserRole.CUSTOMER);
    }

    @Test
    void customerCanPurchasePlan() {
        when(userRepository.findLockedById(1L)).thenReturn(Optional.of(customer));
        when(membershipRepository.findActiveLocked(eq(customer), any(Instant.class)))
                .thenReturn(Optional.empty());
        when(membershipRepository.save(any(UserMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ActiveMembershipResponse response =
                membershipService.purchase(customer, MembershipPlanType.STUDENT);
        assertEquals("学生月卡", response.planTitle());
        assertEquals(20, response.totalTrips());
        assertEquals(20, response.remainingTrips());
        assertTrue(response.active());
    }

    @Test
    void activeMembershipPreventsDuplicatePurchase() {
        UserMembership active = membership(customer, MembershipPlanType.LIGHT, 10);
        when(userRepository.findLockedById(1L)).thenReturn(Optional.of(customer));
        when(membershipRepository.findActiveLocked(eq(customer), any(Instant.class)))
                .thenReturn(Optional.of(active));
        BusinessFailure exception =
                assertThrows(
                        BusinessFailure.class,
                        () -> membershipService.purchase(customer, MembershipPlanType.STUDENT));
        assertEquals(HttpStatus.CONFLICT.value(), exception.status());
    }

    @Test
    void activeMembershipConsumesOneTrip() {
        UserMembership active = membership(customer, MembershipPlanType.STUDENT, 12);
        ReflectionTestUtils.setField(active, "id", 41L);
        when(membershipRepository.findActiveLocked(eq(customer), any(Instant.class)))
                .thenReturn(Optional.of(active));
        MembershipApplication application = membershipService.apply(customer, true);
        assertTrue(application.used());
        assertEquals(41L, application.membershipId());
        assertEquals("学生月卡", application.planTitle());
        assertEquals(11, active.getRemainingTrips());
    }

    @Test
    void cancellingOrderRestoresTripToMembershipUsedByThatOrder() {
        UserMembership membership = membership(customer, MembershipPlanType.STUDENT, 11);
        ReflectionTestUtils.setField(membership, "id", 41L);
        when(membershipRepository.findLockedById(41L)).thenReturn(Optional.of(membership));
        membershipService.refund(customer, 41L);
        assertEquals(12, membership.getRemainingTrips());
    }

    @Test
    void cannotRefundAnotherCustomersMembership() {
        AppUser otherCustomer = user(2L, UserRole.CUSTOMER);
        UserMembership membership = membership(otherCustomer, MembershipPlanType.STUDENT, 11);
        ReflectionTestUtils.setField(membership, "id", 41L);
        when(membershipRepository.findLockedById(41L)).thenReturn(Optional.of(membership));
        BusinessFailure exception =
                assertThrows(BusinessFailure.class, () -> membershipService.refund(customer, 41L));
        assertEquals(HttpStatus.CONFLICT.value(), exception.status());
        assertEquals(11, membership.getRemainingTrips());
    }

    @Test
    void orderWithoutMembershipDoesNotAccessRepository() {
        MembershipApplication application = membershipService.apply(customer, false);
        assertFalse(application.used());
        verifyNoInteractions(membershipRepository, userRepository);
    }

    @Test
    void membershipCannotBeUsedWithoutActiveBalance() {
        when(membershipRepository.findActiveLocked(eq(customer), any(Instant.class)))
                .thenReturn(Optional.empty());
        BusinessFailure exception =
                assertThrows(BusinessFailure.class, () -> membershipService.apply(customer, true));
        assertEquals(HttpStatus.CONFLICT.value(), exception.status());
    }

    @Test
    void courierCannotPurchaseMembership() {
        AppUser courier = user(2L, UserRole.COURIER);
        BusinessFailure exception =
                assertThrows(
                        BusinessFailure.class,
                        () -> membershipService.purchase(courier, MembershipPlanType.LIGHT));
        assertEquals(HttpStatus.FORBIDDEN.value(), exception.status());
        verifyNoInteractions(membershipRepository);
    }

    private UserMembership membership(AppUser user, MembershipPlanType plan, int remainingTrips) {
        UserMembership membership = new UserMembership(user, plan, Instant.now().plusSeconds(3600));
        ReflectionTestUtils.setField(membership, "remainingTrips", remainingTrips);
        return membership;
    }

    private AppUser user(Long id, UserRole role) {
        AppUser user = new AppUser("1380000000" + id, "password-hash", "测试账号" + id, role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
