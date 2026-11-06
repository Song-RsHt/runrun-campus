package com.zhangxuan.runruncampus.service.admin;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.UserFeedback;
import com.zhangxuan.runruncampus.enums.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.AdminAuditTargetType;
import com.zhangxuan.runruncampus.enums.FeedbackHandlingStatus;
import com.zhangxuan.runruncampus.enums.FeedbackType;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.repository.FeedbackRepository;
import com.zhangxuan.runruncampus.service.AdminAuditService;
import com.zhangxuan.runruncampus.service.AdminFeedbackService;
import com.zhangxuan.runruncampus.service.impl.AdminFeedbackServiceImpl;
import com.zhangxuan.runruncampus.vo.FeedbackItem;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;




































































@ExtendWith(MockitoExtension.class)
class AdminFeedbackServiceTest {

  @Mock private FeedbackRepository feedbackRepository;

  @Mock private AdminAuditService auditService;

  private AdminFeedbackService service;

  private AppUser administrator;

  private UserFeedback feedback;

  @BeforeEach
  void setUp() {
    service = new AdminFeedbackServiceImpl(feedbackRepository, auditService);
    administrator = new AppUser("13800000000", "hash", "运营员", UserRole.ADMIN);
    ReflectionTestUtils.setField(administrator, "id", 1L);
    AppUser user = new AppUser("13800000001", "hash", "反馈用户", UserRole.CUSTOMER);
    ReflectionTestUtils.setField(user, "id", 2L);
    feedback = new UserFeedback(user, FeedbackType.FUNCTION, "希望增加状态提醒");
    ReflectionTestUtils.setField(feedback, "id", 9L);
  }

  @Test
  void claimsAndResolvesFeedbackForAssignedAdministrator() {
    when(feedbackRepository.findLockedById(9L)).thenReturn(Optional.of(feedback));
    FeedbackItem claimed = service.claim(administrator, 9L);
    FeedbackItem resolved = service.resolve(administrator, 9L, "  已记录并排期  ");
    assertEquals(FeedbackHandlingStatus.PROCESSING, claimed.handlingStatus());
    assertEquals(FeedbackHandlingStatus.RESOLVED, resolved.handlingStatus());
    assertEquals("已记录并排期", resolved.resolution());
    verify(auditService)
        .record(
            administrator,
            AdminAuditAction.FEEDBACK_CLAIMED,
            AdminAuditTargetType.FEEDBACK,
            9L,
            "领取用户反馈：#9");
    verify(auditService)
        .record(
            administrator,
            AdminAuditAction.FEEDBACK_RESOLVED,
            AdminAuditTargetType.FEEDBACK,
            9L,
            "解决用户反馈：#9");
  }
}
