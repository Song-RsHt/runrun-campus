package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.entity.OrderProof;
import com.zhangxuan.runruncampus.enums.OrderProofType;
import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.integration.storage.OrderProofStorage;
import com.zhangxuan.runruncampus.repository.OrderProofRepository;
import com.zhangxuan.runruncampus.repository.OrderRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

public record OrderProofVO(Long id, OrderProofType type, String originalFileName, String contentType, long sizeBytes, Instant createdAt) {
}
