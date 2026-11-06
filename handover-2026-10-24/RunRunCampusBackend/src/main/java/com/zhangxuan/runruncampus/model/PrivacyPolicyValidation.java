package com.zhangxuan.runruncampus.model;

import com.zhangxuan.runruncampus.constant.PrivacyConstants;
import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.enums.order.OrderStatus;
import com.zhangxuan.runruncampus.repository.SessionRepository;
import com.zhangxuan.runruncampus.repository.UserRepository;
import com.zhangxuan.runruncampus.repository.address.AddressRepository;
import com.zhangxuan.runruncampus.repository.message.OrderMessageRepository;
import com.zhangxuan.runruncampus.repository.message.PushDeliveryRepository;
import com.zhangxuan.runruncampus.repository.order.OrderRepository;
import com.zhangxuan.runruncampus.repository.payment.WechatRefundRepository;
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

public final class PrivacyPolicyValidation {

    private PrivacyPolicyValidation() {
    }

    public static void requireCurrentVersion(String version) {
        if (!PrivacyConstants.CURRENT_VERSION.equals(version)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "隐私政策已更新，请阅读最新版本后重试");
        }
    }
}
