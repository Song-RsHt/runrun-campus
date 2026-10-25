package com.zhangxuan.runruncampus.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginCaptchaServiceTest {
    private final LoginCaptchaService service = new LoginCaptchaService(new SecureRandom(), Clock.systemUTC());

    @Test
    void challengeContainsRenderablePngAndCanOnlyBeUsedOnce() {
        LoginCaptchaService.Challenge challenge = service.issue("1234");

        assertTrue(challenge.imageDataUrl().startsWith("data:image/png;base64,iVBOR"));
        assertDoesNotThrow(() -> service.verify(challenge.challengeToken(), "1234"));
        ResponseStatusException reused = assertThrows(ResponseStatusException.class,
                () -> service.verify(challenge.challengeToken(), "1234"));
        assertEquals(HttpStatus.PRECONDITION_REQUIRED, reused.getStatusCode());
    }

    @Test
    void incorrectAnswerConsumesChallenge() {
        LoginCaptchaService.Challenge challenge = service.issue("5678");

        assertThrows(ResponseStatusException.class,
                () -> service.verify(challenge.challengeToken(), "0000"));
        assertThrows(ResponseStatusException.class,
                () -> service.verify(challenge.challengeToken(), "5678"));
    }
}
