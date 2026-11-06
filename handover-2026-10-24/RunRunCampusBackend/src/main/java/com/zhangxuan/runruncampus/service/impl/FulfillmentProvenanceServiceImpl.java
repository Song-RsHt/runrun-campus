package com.zhangxuan.runruncampus.service.impl;

import com.zhangxuan.runruncampus.dto.CreateRequest;
import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.Channel;
import com.zhangxuan.runruncampus.enums.OrderPaymentMethod;
import com.zhangxuan.runruncampus.enums.OrderPaymentStatus;
import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.mapper.FulfillmentProvenanceMapper;
import com.zhangxuan.runruncampus.repository.OrderRepository;
import com.zhangxuan.runruncampus.vo.Entry;
import com.zhangxuan.runruncampus.vo.Summary;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.zhangxuan.runruncampus.service.FulfillmentProvenanceService;

/**
 * 维护履约溯源及独立核验约束。金额、证据摘要及录入人与核验人关系在业务层校验，再按原事务持久化。
 */
@Service
public class FulfillmentProvenanceServiceImpl implements FulfillmentProvenanceService {

    private final FulfillmentProvenanceMapper mapper;

    private final OrderRepository orders;

    public FulfillmentProvenanceServiceImpl(FulfillmentProvenanceMapper mapper, OrderRepository orders) {
        this.mapper = mapper;
        this.orders = orders;
    }

    @Transactional
    @Override
    public Entry record(AppUser administrator, CreateRequest request) {
        if (request == null || request.channel() == null || request.completedAt() == null || request.completedAt().isAfter(Instant.now()) || request.sourceRef() == null || !request.sourceRef().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}") || !isHash(request.completionEvidenceSha256()) || !optionalHash(request.financialEvidenceSha256()) || negative(request.customerPaidCents()) || negative(request.platformRevenueCents()) || negative(request.courierSettlementCents())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "溯源字段格式无效");
        }
        boolean hasAmounts = request.customerPaidCents() != null || request.platformRevenueCents() != null || request.courierSettlementCents() != null;
        if (hasAmounts && request.financialEvidenceSha256() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "填写金额时必须提供独立的财务凭证摘要");
        }
        if (request.channel() == Channel.APP_END_TO_END && request.appOrderId() == null || request.channel() == Channel.MANUAL_OFFLINE && request.appOrderId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "履约渠道与软件订单关联不一致");
        }
        if (request.appOrderId() != null) {
            var order = orders.findById(request.appOrderId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "关联的软件订单不存在"));
            if (order.getStatus() != OrderStatus.COMPLETED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "关联的软件订单尚未完成");
            }
            if (request.channel() == Channel.APP_END_TO_END && (order.getPaymentMethod() != OrderPaymentMethod.WECHAT_APP || order.getPaymentStatus() != OrderPaymentStatus.PAID)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "全软件闭环须有已支付的软件支付订单");
            }
        }
        try {
            Long id = mapper.insertProvenance(request.sourceRef(), request.channel().name(), request.appOrderId(), Timestamp.from(request.completedAt()), request.customerPaidCents(), request.platformRevenueCents(), request.courierSettlementCents(), request.completionEvidenceSha256().toLowerCase(Locale.ROOT), request.financialEvidenceSha256() == null ? null : request.financialEvidenceSha256().toLowerCase(Locale.ROOT), administrator.getId());
            return find(id);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该来源编号或软件订单已被记录");
        }
    }

    @Transactional
    @Override
    public Entry verify(AppUser administrator, long id) {
        int updated = mapper.verifyByIndependentAdministrator(administrator.getId(), id, administrator.getId());
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "记录不存在、已核验或不能由录入人自行核验");
        }
        return find(id);
    }

    @Transactional(readOnly = true)
    @Override
    public List<Entry> list(int page) {
        if (page < 0 || page > 10000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "页码超出范围");
        }
        return mapper.pageProvenance(page * 100);
    }

    @Transactional(readOnly = true)
    @Override
    public Summary summary() {
        return mapper.summarizeVerifiedProvenance();
    }

    private Entry find(long id) {
        return mapper.findProvenance(id);
    }

    private static boolean negative(Long amount) {
        return amount != null && amount < 0;
    }

    private static boolean isHash(String value) {
        return value != null && value.matches("[A-Fa-f0-9]{64}");
    }

    private static boolean optionalHash(String value) {
        return value == null || isHash(value);
    }
}
