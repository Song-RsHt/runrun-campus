package com.zhangxuan.runruncampus.service.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.service.LoginCaptchaService;
import com.zhangxuan.runruncampus.service.impl.LoginCaptchaServiceImpl;
import com.zhangxuan.runruncampus.vo.LoginCaptchaVO;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.security.SecureRandom;
import java.time.Clock;

class LoginCaptchaServiceTest {

    private final LoginCaptchaService service =
            new LoginCaptchaServiceImpl(new SecureRandom(), Clock.systemUTC());

    @Test
    void challengeContainsRenderablePngAndCanOnlyBeUsedOnce() {
        LoginCaptchaVO challenge = service.issue("1234");
        assertTrue(challenge.imageDataUrl().startsWith("data:image/png;base64,iVBOR"));
        assertDoesNotThrow(() -> service.verify(challenge.challengeToken(), "1234"));
        BusinessFailure reused =
                assertThrows(
                        BusinessFailure.class,
                        () -> service.verify(challenge.challengeToken(), "1234"));
        assertEquals(HttpStatus.PRECONDITION_REQUIRED.value(), reused.status());
    }

    @Test
    void incorrectAnswerConsumesChallenge() {
        LoginCaptchaVO challenge = service.issue("5678");
        assertThrows(
                BusinessFailure.class, () -> service.verify(challenge.challengeToken(), "0000"));
        assertThrows(
                BusinessFailure.class, () -> service.verify(challenge.challengeToken(), "5678"));
    }
}
