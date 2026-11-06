package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CourierCertification;
import com.zhangxuan.runruncampus.enums.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.AdminAuditTargetType;
import com.zhangxuan.runruncampus.enums.CourierCertificationStatus;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.integration.storage.OrderProofStorage;
import com.zhangxuan.runruncampus.model.CertificationPhoto;
import com.zhangxuan.runruncampus.repository.CourierCertificationRepository;
import com.zhangxuan.runruncampus.vo.CertificationResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.zhangxuan.runruncampus.model.UploadSource;
import org.springframework.web.server.ResponseStatusException;

/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface CourierCertificationService {

    CertificationResponse overview(AppUser courier);

    CertificationResponse submit(AppUser courier);

    CertificationResponse submit(AppUser courier, Long expectedVersion);

    boolean isEligible(AppUser courier);

    List<CertificationResponse> pending(AppUser admin);

    CertificationResponse approve(AppUser admin, Long id);

    CertificationResponse approve(AppUser admin, Long id, long expectedVersion, String photoResourceId);

    CertificationResponse reject(AppUser admin, Long id, String reason);

    CertificationResponse uploadStudentCard(AppUser courier, Long id, UploadSource file);

    CertificationResponse uploadStudentCard(AppUser courier, Long id, UploadSource file, Long expectedVersion);

    CertificationPhoto studentCardPhoto(AppUser admin, Long id);

    void deletePersonalData(AppUser courier);
}
