package com.zhangxuan.runruncampus.controller.admin;

import com.zhangxuan.runruncampus.controller.AdminPushDeliveryController;
import com.zhangxuan.runruncampus.enums.AdminPermission;
import com.zhangxuan.runruncampus.enums.PushDeliveryStatus;
import com.zhangxuan.runruncampus.model.PushDeliveryPage;
import com.zhangxuan.runruncampus.repository.PushDeliveryRepository;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.service.impl.AdminPushDeliveryServiceImpl;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;












































class AdminPushDeliveryControllerTest {

  @Test
  void overviewRequiresDedicatedPermissionAndReturnsFailureCounts() {
    AuthService auth = mock(AuthService.class);
    PushDeliveryRepository deliveries = mock(PushDeliveryRepository.class);
    when(deliveries.count(PushDeliveryStatus.DEAD)).thenReturn(3L);
    when(deliveries.count(PushDeliveryStatus.PENDING)).thenReturn(2L);
    when(deliveries.count(PushDeliveryStatus.PROCESSING)).thenReturn(1L);
    when(deliveries.page(PushDeliveryStatus.DEAD, 0, 20))
        .thenReturn(new PushDeliveryPage(List.of(), 0, 20, 3, 1));
    var result =
        new AdminPushDeliveryController(auth, new AdminPushDeliveryServiceImpl(deliveries))
            .overview("Bearer token", PushDeliveryStatus.DEAD, 0, 20);
    verify(auth).requirePermission("Bearer token", AdminPermission.PUSH_DELIVERY_VIEW);
    assertEquals(3, result.dead());
    assertEquals(2, result.pending());
    assertEquals(1, result.processing());
  }
}
