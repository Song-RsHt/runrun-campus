package com.zhangxuan.runruncampus.privacy;

import com.zhangxuan.runruncampus.address.AddressRepository;
import com.zhangxuan.runruncampus.auth.SessionRepository;
import com.zhangxuan.runruncampus.courier.CourierCertificationService;
import com.zhangxuan.runruncampus.message.OrderMessageRepository;
import com.zhangxuan.runruncampus.message.PushDeliveryRepository;
import com.zhangxuan.runruncampus.order.OrderRepository;
import com.zhangxuan.runruncampus.order.OrderStatus;
import com.zhangxuan.runruncampus.payment.WechatRefundRepository;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.zhangxuan.runruncampus.user.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class PrivacyService {
    public static final String CURRENT_VERSION = "2026-10-24";
    public static final List<OrderStatus> ACTIVE_ORDER_STATUSES = List.of(
            OrderStatus.PENDING_ASSIGNMENT, OrderStatus.PENDING_REASSIGNMENT,
            OrderStatus.ACCEPTED, OrderStatus.PICKED_UP);
    private static final String CONTENT = """
            一、我们处理的信息
            为提供校园代取服务，我们会处理手机号、昵称、账号角色、登录凭证及与手机号账号绑定的微信 openid。下单和履约过程中，我们会处理联系人姓名和手机号、商家或驿站、取件码、送达区域、详细位置、服务规格、物品类型、申报价值、备注、订单及售后记录。骑手认证会处理学生证照片；履约过程中会处理规格核验、取件和送达照片，以及骑手提交送达时获取的一次位置。信用与风控功能会处理取消次数、异常工单、履约超时和管理员核实结果，并形成风险提示、信用分及变动记录。

            二、使用目的
            上述信息仅用于账号登录和微信身份绑定、订单履约、微信支付及退款、骑手认证、客服售后、安全风控、消息通知以及法律法规要求的安全保障。风险提示只用于管理员人工核实，不会自动扣分、停用账号或限制下单和接单。

            三、第三方服务
            根据实际启用的功能，小程序会使用微信提供登录、一次位置授权和微信支付，并可能使用阿里云提供服务器和对象存储。相关功能只在必要场景调用；我们不通过本小程序持续获取骑手位置，也不保存完整配送轨迹。

            四、保存与保护
            学生证照片按现有规则在审核后 30 天清理；无争议订单的规格核验、取件、送达照片和送达精确位置在订单完成后 7 天清理；存在争议时保存至争议处理结束后 7 天。小程序普通历史订单列表展示最近 7 天，依法需要继续保存的核心交易记录自交易完成之日起保存不少于 3 年并进行必要的安全保护。注销后会删除可直接删除的资料并匿名化账号。

            五、你的权利
            你可以在应用内查看本规则、信用分和信用变动原因，管理登录设备、修改密码、提交投诉或信用申诉并注销账号。注销前仅校验账号安全及是否存在进行中订单或未完成退款。

            六、联系我们
            运营主体：射阳县合德镇轩轩生活服务部
            投诉电话：15151050678
            """;

    private final UserRepository users;
    private final OrderRepository orders;
    private final WechatRefundRepository refunds;
    private final SessionRepository sessions;
    private final AddressRepository addresses;
    private final CourierCertificationService certifications;
    private final OrderMessageRepository messages;
    private final PushDeliveryRepository deliveries;
    private final PasswordEncoder passwords;

    public PrivacyService(UserRepository users, OrderRepository orders, WechatRefundRepository refunds,
                          SessionRepository sessions, AddressRepository addresses,
                          CourierCertificationService certifications, OrderMessageRepository messages,
                          PushDeliveryRepository deliveries, PasswordEncoder passwords) {
        this.users = users;
        this.orders = orders;
        this.refunds = refunds;
        this.sessions = sessions;
        this.addresses = addresses;
        this.certifications = certifications;
        this.messages = messages;
        this.deliveries = deliveries;
        this.passwords = passwords;
    }

    public Policy policy() {
        return new Policy(CURRENT_VERSION, "跑跑代取隐私政策", LocalDate.of(2026, 10, 24),
                "射阳县合德镇轩轩生活服务部", "15151050678", CONTENT);
    }

    public ConsentStatus status(AppUser user) {
        return new ConsentStatus(CURRENT_VERSION.equals(user.getPrivacyPolicyVersion()),
                CURRENT_VERSION, user.getPrivacyPolicyVersion(), user.getPrivacyConsentAt());
    }

    @Transactional
    public ConsentStatus consent(AppUser user, String version) {
        requireCurrentVersion(version);
        user.recordPrivacyConsent(version);
        users.save(user);
        return status(user);
    }

    @Transactional
    public void withdrawConsent(AppUser current) {
        AppUser user = users.findLockedById(current.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效"));
        user.withdrawPrivacyConsent();
        sessions.deleteByUser(user);
    }

    @Transactional
    public void deleteAccount(AppUser current, String password, boolean confirmed) {
        if (!confirmed) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请确认注销账号");
        AppUser user = users.findLockedById(current.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效"));
        if (user.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理员账号不能在客户端注销");
        }
        if (!passwords.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前密码错误");
        }
        long activeOrders = orders.countByCustomerAndStatusIn(user, ACTIVE_ORDER_STATUSES)
                + orders.countByCourierAndStatusIn(user, ACTIVE_ORDER_STATUSES);
        if (activeOrders > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "存在进行中订单，完成或取消后才能注销");
        }
        if (refunds.existsUnfinishedByUser(user)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "存在未完成退款，退款完成后才能注销");
        }
        certifications.deletePersonalData(user);
        addresses.deleteByUser(user);
        messages.deleteByRecipient(user);
        deliveries.deleteByRecipient(user.getId());
        user.anonymize(passwords.encode(UUID.randomUUID().toString()));
        sessions.deleteByUser(user);
    }

    public static void requireCurrentVersion(String version) {
        if (!CURRENT_VERSION.equals(version)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "隐私政策已更新，请阅读最新版本后重试");
        }
    }

    public record Policy(String version, String title, LocalDate effectiveDate,
                         String operatorName, String contact, String content) { }
    public record ConsentStatus(boolean accepted, String currentVersion, String acceptedVersion,
                                Instant acceptedAt) { }
}
