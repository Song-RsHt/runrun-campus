package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.entity.AdminMfaCredential;
import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.Session;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.enums.admin.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.admin.AdminAuditTargetType;
import com.zhangxuan.runruncampus.enums.admin.AdminRole;
import com.zhangxuan.runruncampus.model.AdminMfaVerification;
import com.zhangxuan.runruncampus.model.SessionMetadata;
import com.zhangxuan.runruncampus.repository.AdminMfaRepository;
import com.zhangxuan.runruncampus.repository.SessionRepository;
import com.zhangxuan.runruncampus.repository.UserRepository;
import com.zhangxuan.runruncampus.security.AdminMfaCrypto;
import com.zhangxuan.runruncampus.service.admin.AdminAuditService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

public record AdminMfaChallengeVO(String step, String challengeToken, Instant expiresAt, String secret, String otpauthUri) {
}
