package com.zhangxuan.runruncampus.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/app-version")
public class AppVersionController {
    private final int versionCode;
    private final String versionName;
    private final String downloadUrl;

    public AppVersionController(@Value("${app.android-update.version-code:0}") int versionCode,
                                @Value("${app.android-update.version-name:}") String versionName,
                                @Value("${app.android-update.download-url:}") String downloadUrl) {
        this.versionCode = versionCode;
        this.versionName = versionName.trim();
        this.downloadUrl = downloadUrl.trim();
    }

    @GetMapping("/android")
    public AndroidVersion android() {
        if (versionCode < 1 || versionName.isBlank() || !isHttps(downloadUrl)) {
            return new AndroidVersion(false, 0, null, null);
        }
        return new AndroidVersion(true, versionCode, versionName, downloadUrl);
    }

    private boolean isHttps(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public record AndroidVersion(boolean available, int latestVersionCode,
                                 String latestVersionName, String downloadUrl) { }
}
