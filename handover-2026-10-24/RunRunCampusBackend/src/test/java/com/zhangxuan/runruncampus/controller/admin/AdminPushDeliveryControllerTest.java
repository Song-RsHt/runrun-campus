package com.zhangxuan.runruncampus.controller.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhangxuan.runruncampus.enums.admin.AdminPermission;
import com.zhangxuan.runruncampus.enums.message.PushDeliveryStatus;
import com.zhangxuan.runruncampus.repository.message.PushDeliveryRepository;
import com.zhangxuan.runruncampus.service.auth.AuthService;
import com.zhangxuan.runruncampus.service.impl.admin.AdminPushDeliveryServiceImpl;
import com.zhangxuan.runruncampus.vo.message.PushDeliveryPageVO;

import org.junit.jupiter.api.Test;

import java.util.List;

class AdminPushDeliveryControllerTest {

    @Test
    void overviewRequiresDedicatedPermissionAndReturnsFailureCounts() {
        AuthService auth = mock(AuthService.class);
        PushDeliveryRepository deliveries = mock(PushDeliveryRepository.class);
        when(deliveries.count(PushDeliveryStatus.DEAD)).thenReturn(3L);
        when(deliveries.count(PushDeliveryStatus.PENDING)).thenReturn(2L);
        when(deliveries.count(PushDeliveryStatus.PROCESSING)).thenReturn(1L);
        when(deliveries.page(PushDeliveryStatus.DEAD, 0, 20))
                .thenReturn(new PushDeliveryPageVO(List.of(), 0, 20, 3, 1));
        var result =
                new AdminPushDeliveryController(auth, new AdminPushDeliveryServiceImpl(deliveries))
                        .overview("Bearer token", PushDeliveryStatus.DEAD, 0, 20);
        verify(auth).requirePermission("Bearer token", AdminPermission.PUSH_DELIVERY_VIEW);
        assertEquals(3, result.dead());
        assertEquals(2, result.pending());
        assertEquals(1, result.processing());
    }
}
