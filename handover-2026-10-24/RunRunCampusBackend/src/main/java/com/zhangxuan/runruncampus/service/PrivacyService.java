package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.constant.PrivacyConstants;
import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.model.PrivacyPolicyValidation;
import com.zhangxuan.runruncampus.repository.SessionRepository;
import com.zhangxuan.runruncampus.repository.UserRepository;
import com.zhangxuan.runruncampus.repository.AddressRepository;
import com.zhangxuan.runruncampus.repository.message.OrderMessageRepository;
import com.zhangxuan.runruncampus.repository.message.PushDeliveryRepository;
import com.zhangxuan.runruncampus.repository.OrderRepository;
import com.zhangxuan.runruncampus.repository.WechatRefundRepository;
import com.zhangxuan.runruncampus.service.courier.CourierCertificationService;
import com.zhangxuan.runruncampus.vo.PrivacyConsentVO;
import com.zhangxuan.runruncampus.vo.PrivacyPolicyVO;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface PrivacyService {

    PrivacyPolicyVO policy();

    PrivacyConsentVO status(AppUser user);

    PrivacyConsentVO consent(AppUser user, String version);

    void withdrawConsent(AppUser current);

    void deleteAccount(AppUser current, String password, boolean confirmed);
}
