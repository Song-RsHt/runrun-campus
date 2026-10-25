package com.zhangxuan.runruncampus.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiRateLimitFilterTest {
    @Test
    void proofUploadLimitIsAppliedPerCredential() throws Exception {
        ApiRateLimitFilter filter = new ApiRateLimitFilter(new ApiRateLimitService(Clock.systemUTC()));

        for (int request = 0; request < 12; request++) {
            assertEquals(200, execute(filter, "POST", "/api/v1/orders/11/proofs/PICKUP",
                    "Bearer token-a", "10.0.0.1").getStatus());
        }

        MockHttpServletResponse blocked = execute(filter, "POST", "/api/v1/orders/11/proofs/PICKUP",
                "Bearer token-a", "10.0.0.1");
        assertEquals(429, blocked.getStatus());
        assertEquals("60", blocked.getHeader("Retry-After"));
        assertTrue(blocked.getContentAsString().contains("请求过于频繁"));

        assertEquals(200, execute(filter, "POST", "/api/v1/orders/11/proofs/PICKUP",
                "Bearer token-b", "10.0.0.1").getStatus());
    }

    @Test
    void authenticationLimitIsAppliedPerClientAddress() throws Exception {
        ApiRateLimitFilter filter = new ApiRateLimitFilter(new ApiRateLimitService(Clock.systemUTC()));

        for (int request = 0; request < 30; request++) {
            assertEquals(200, execute(filter, "POST", "/api/v1/auth/login", null, "10.0.0.2").getStatus());
        }

        assertEquals(429, execute(filter, "POST", "/api/v1/auth/login", null, "10.0.0.2").getStatus());
        assertEquals(200, execute(filter, "POST", "/api/v1/auth/login", null, "10.0.0.3").getStatus());
    }

    @Test
    void normalPollingFrequencyRemainsBelowReadLimit() throws Exception {
        ApiRateLimitFilter filter = new ApiRateLimitFilter(new ApiRateLimitService(Clock.systemUTC()));

        for (int request = 0; request < 60; request++) {
            assertEquals(200, execute(filter, "GET", "/api/v1/orders/mine",
                    "Bearer polling-token", "10.0.0.4").getStatus());
        }
    }

    private MockHttpServletResponse execute(ApiRateLimitFilter filter, String method, String uri,
                                            String authorization, String remoteAddress) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(remoteAddress);
        if (authorization != null) request.addHeader("Authorization", authorization);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });
        return response;
    }
}
