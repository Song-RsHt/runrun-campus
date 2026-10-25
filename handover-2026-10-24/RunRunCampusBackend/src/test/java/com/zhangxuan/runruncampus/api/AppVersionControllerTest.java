package com.zhangxuan.runruncampus.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppVersionControllerTest {
    @Test
    void validConfiguredHttpsReleaseIsAdvertised() {
        var result = new AppVersionController(13, "2.3", "https://downloads.example.com/runrun.apk").android();

        assertTrue(result.available());
        assertEquals(13, result.latestVersionCode());
        assertEquals("https://downloads.example.com/runrun.apk", result.downloadUrl());
    }

    @Test
    void missingOrInsecureDownloadLinkKeepsPromptDisabled() {
        assertFalse(new AppVersionController(0, "", "").android().available());
        assertFalse(new AppVersionController(13, "2.3", "http://example.com/app.apk").android().available());
    }
}
