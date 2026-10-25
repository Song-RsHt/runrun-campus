package com.zhangxuan.runruncampus.feedback;

import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {
    @Mock
    private FeedbackRepository feedbackRepository;

    private FeedbackService feedbackService;
    private AppUser user;

    @BeforeEach
    void setUp() {
        feedbackService = new FeedbackService(feedbackRepository);
        user = user(1L, UserRole.CUSTOMER);
    }

    @Test
    void submitsTrimmedFeedback() {
        when(feedbackRepository.save(any(UserFeedback.class))).thenAnswer(invocation -> {
            UserFeedback feedback = invocation.getArgument(0);
            ReflectionTestUtils.setField(feedback, "id", 11L);
            return feedback;
        });

        FeedbackService.FeedbackResponse response = feedbackService.submit(
                user, FeedbackType.FUNCTION, "  希望增加深色模式  ");

        assertEquals(11L, response.id());
        assertEquals(FeedbackType.FUNCTION, response.type());
        assertEquals("希望增加深色模式", response.content());
        assertNotNull(response.createdAt());
    }

    @Test
    void listsOnlyCurrentUsersFeedback() {
        UserFeedback feedback = new UserFeedback(user, FeedbackType.ACCOUNT, "账号问题");
        ReflectionTestUtils.setField(feedback, "id", 12L);
        when(feedbackRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(feedback));

        List<FeedbackService.FeedbackResponse> responses = feedbackService.list(user);

        assertEquals(1, responses.size());
        assertEquals(12L, responses.get(0).id());
        verify(feedbackRepository).findByUserOrderByCreatedAtDesc(user);
    }

    @Test
    void userCanSeeResolutionAndHandledTime() {
        UserFeedback feedback = new UserFeedback(user, FeedbackType.FUNCTION, "支付失败");
        AppUser admin = user(2L, UserRole.ADMIN);
        feedback.claim(admin);
        feedback.resolve("已核对支付记录，款项未扣除");
        when(feedbackRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(feedback));

        FeedbackService.FeedbackResponse response = feedbackService.list(user).get(0);

        assertEquals(FeedbackHandlingStatus.RESOLVED, response.handlingStatus());
        assertEquals("已核对支付记录，款项未扣除", response.resolution());
        assertNotNull(response.handledAt());
        assertNotNull(response.updatedAt());
    }

    @Test
    void rejectsBlankFeedback() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> feedbackService.submit(user, FeedbackType.OTHER, "   "));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(feedbackRepository);
    }

    private AppUser user(Long id, UserRole role) {
        AppUser appUser = new AppUser("1380000000" + id, "password-hash", "测试账号", role);
        ReflectionTestUtils.setField(appUser, "id", id);
        return appUser;
    }
}
