package com.zhangxuan.runrunv2.service.impl.identity;

import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.integration.wechat.WechatGateway;
import com.zhangxuan.runrunv2.mapper.courier.CourierCertificationV2Mapper;
import com.zhangxuan.runrunv2.mapper.identity.IdentityMapper;
import com.zhangxuan.runrunv2.model.identity.IdentityUser;
import com.zhangxuan.runrunv2.model.identity.InputValidation;
import com.zhangxuan.runrunv2.security.IdentitySecrets;
import com.zhangxuan.runrunv2.service.identity.IdentityService;
import com.zhangxuan.runrunv2.vo.identity.IdentityAccountVO;
import com.zhangxuan.runrunv2.vo.identity.IdentityLoginVO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 新系统身份与会话业务实现；通过注入的业务契约及数据访问组件执行，事务边界保留在本类。
 *
 * <p>编排 v2 微信身份、独立会话和账号访问规则；认证材料委托安全组件处理，数据访问委托 Mapper。 保留票据消费、会话撤销与审计的原子性，不接入 legacy
 * 账号恢复。参见《账号与历史兼容规则》。
 */
@Service
public class IdentityServiceImpl implements IdentityService {

    private final IdentityMapper mapper;
    private final CourierCertificationV2Mapper certifications;
    private final IdentitySecrets secrets;
    private final WechatGateway wechat;
    private final String appId;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
    private final String dummyPassword = passwords.encode("synthetic-unmatchable-password");

    public IdentityServiceImpl(
            IdentityMapper mapper,
            CourierCertificationV2Mapper certifications,
            IdentitySecrets secrets,
            WechatGateway wechat,
            @Value("${rrc.identity.app-id}") String appId) {
        this.mapper = mapper;
        this.certifications = certifications;
        this.secrets = secrets;
        this.wechat = wechat;
        this.appId = appId;
    }

    @Transactional
    @Override
    public IdentityLoginVO login(String code) {
        InputValidation.text(code, 512);
        String openid = wechat.exchangeLoginCode(code);
        String digest = secrets.hash("openid:" + appId, openid);
        lock("wechat:" + appId + ":" + digest);
        List<IdentityUser> users = mapper.findWechatUsersForUpdate(appId, digest);
        if (!users.isEmpty()) {
            return issue(users.get(0));
        }
        String ticket = secrets.token();
        mapper.insertWechatTicket(
                secrets.hash("ticket", ticket), appId, digest, secrets.encrypt(appId, openid));
        return new IdentityLoginVO(ticket, null, null);
    }

    @Transactional
    @Override
    public IdentityLoginVO bindPhone(String loginTicket, String phoneCode, String requestedRole) {
        InputValidation.text(loginTicket, 128);
        InputValidation.text(phoneCode, 512);
        if (!"USER".equals(requestedRole) && !"COURIER".equals(requestedRole)) {
            throw IdentityFailure.invalid();
        }
        Map<String, Object> ticket = ticket(loginTicket, "WECHAT_LOGIN");
        if (!appId.equals(ticket.get("app_id"))) {
            throw IdentityFailure.auth();
        }
        String phone = wechat.exchangePhoneCode(phoneCode);
        // 手机号必须来自微信服务端响应，不能信任客户端字段。
        if (phone == null || !phone.matches("1[3-9][0-9]{9}")) {
            throw new IdentityFailure(401, "WECHAT_AUTH_FAILED");
        }
        lock("phone:" + phone);
        lock("wechat:" + appId + ":" + (String) ticket.get("openid_hash"));
        if (mapper.countWechatIdentity(appId, (String) ticket.get("openid_hash")) > 0) {
            throw IdentityFailure.conflict();
        }
        List<IdentityUser> existing = mapper.findPhoneUsersForUpdate(phone);
        if (!existing.isEmpty()) {
            throw IdentityFailure.conflict();
        }
        Long id = mapper.insertAccount(phone, requestedRole);
        IdentityUser user = user(id, true);
        if ("COURIER".equals(requestedRole)) {
            certifications.insertPendingCertification(id);
        }
        active(user);
        if (mapper.countUserWechatBindings(appId, user.id()) > 0) {
            throw IdentityFailure.conflict();
        }
        mapper.insertWechatBinding(
                user.id(),
                appId,
                (String) ticket.get("openid_hash"),
                (String) ticket.get("openid_ciphertext"));
        consume(loginTicket);
        mapper.insertWechatBoundEvent(user.id(), user.id());
        return issue(user);
    }

    @Transactional
    @Override
    public IdentityLoginVO passwordLogin(String phone, String password) {
        InputValidation.text(phone, 32);
        InputValidation.text(password, 72);
        List<IdentityUser> users = mapper.findPasswordUsersForUpdate(phone);
        String hash =
                users.isEmpty() || users.get(0).passwordHash() == null
                        ? dummyPassword
                        : users.get(0).passwordHash();
        if (!passwords.matches(password, hash) || users.isEmpty()) {
            throw IdentityFailure.auth();
        }
        IdentityUser user = users.get(0);
        if (user.passwordHash() == null
                || mapper.countPasswordUserBindings(user.id(), appId) == 0) {
            throw IdentityFailure.auth();
        }
        return issue(user);
    }

    @Override
    public IdentityAccountVO authenticate(String token) {
        textToken(token);
        List<IdentityUser> users = mapper.findActiveSessionUser(secrets.hash("session", token));
        if (users.size() != 1) {
            throw IdentityFailure.auth();
        }
        active(users.get(0));
        return account(users.get(0));
    }

    @Override
    public IdentityAccountVO requireRole(
            String token, String role, boolean needsCourierEligibility) {
        IdentityAccountVO account = authenticate(token);
        if (!role.equals(account.role())) {
            throw new IdentityFailure(403, "FORBIDDEN");
        }
        if ("ADMIN".equals(role) && !account.adminEnabled()) {
            throw new IdentityFailure(403, "V2_ADMIN_NOT_ENABLED");
        }
        if ("COURIER".equals(role) && needsCourierEligibility && !account.courierEligible()) {
            throw new IdentityFailure(403, "COURIER_NOT_APPROVED");
        }
        return account;
    }

    @Transactional
    @Override
    public void revoke(String token, boolean all, String key) {
        IdentityAccountVO account = authenticate(token);
        user(Long.parseLong(account.id()), true);
        authenticate(token);
        // 单会话登出的幂等作用域绑定到本次被撤销的具体会话。
        // 其他活动会话复用同一键必须冲突，不能被误判为首次请求的重放。
        String logoutContent =
                all ? "all:true" : "all:false:session:" + secrets.hash("session", token);
        if (!beginWrite(account.id(), "identity:logout", key, logoutContent)) {
            return;
        }
        if (all) {
            mapper.invalidateUserSessions(Long.valueOf(account.id()));
        } else {
            mapper.invalidateSession(secrets.hash("session", token));
        }
        mapper.insertRevocationEvent(Long.valueOf(account.id()), Long.valueOf(account.id()));
        finishWrite(account.id(), "identity:logout", key);
    }

    @Transactional
    @Override
    public void setPassword(String token, String password, String key) {
        IdentityAccountVO account = authenticate(token);
        if (password == null
                || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw IdentityFailure.invalid();
        }
        IdentityUser user = user(Long.parseLong(account.id()), true);
        active(user);
        authenticate(token);
        if (!beginWrite(account.id(), "identity:password", key, password)) {
            return;
        }
        mapper.updatePassword(passwords.encode(password), user.id());
        // 改密后撤销所有会话，包括当前会话，不能保留隐含例外。
        mapper.invalidatePasswordUserSessions(user.id());
        mapper.insertPasswordRevocationEvent(user.id(), user.id());
        finishWrite(account.id(), "identity:password", key);
    }

    /** 业务写入必须在自身事务中调用，并将账号行锁持有至提交。 */
    @Override
    public IdentityAccountVO authorizeForWrite(
            String token, String role, boolean courierEligibility) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Business authorization requires a transaction");
        }
        IdentityAccountVO account = authenticate(token);
        user(Long.parseLong(account.id()), true);
        return requireRole(token, role, courierEligibility);
    }

    private boolean beginWrite(String id, String scope, String key, String content) {
        InputValidation.text(key, 128);
        String digest = secrets.hash("write:" + scope, content);
        List<String> existing = mapper.findIdentityRequestHashes(Long.valueOf(id), scope, key);
        if (!existing.isEmpty()) {
            if (!digest.equals(existing.get(0))) {
                throw new IdentityFailure(409, "IDEMPOTENCY_KEY_CONFLICT");
            }
            return false;
        }
        mapper.insertIdentityRequest(Long.valueOf(id), scope, key, digest);
        return true;
    }

    private void finishWrite(String id, String scope, String key) {
        mapper.completeIdentityRequest(Long.valueOf(id), scope, key);
    }

    @Override
    public IdentityUser user(long id, boolean locked) {
        List<IdentityUser> users = mapper.findUser(locked, id);
        if (users.isEmpty()) {
            throw new IdentityFailure(404, "RESOURCE_NOT_FOUND");
        }
        return users.get(0);
    }

    private IdentityLoginVO issue(IdentityUser user) {
        active(user);
        String token = secrets.token();
        mapper.insertSession(user.id(), secrets.hash("session", token), user.securityVersion());
        return new IdentityLoginVO(null, token, account(user));
    }

    private IdentityAccountVO account(IdentityUser user) {
        boolean eligible =
                "COURIER".equals(user.role())
                        && Boolean.TRUE.equals(mapper.isCourierEligible(user.id()));
        return new IdentityAccountVO(
                Long.toString(user.id()),
                user.role(),
                user.status(),
                user.enabled(),
                user.securityVersion(),
                eligible);
    }

    private Map<String, Object> ticket(String token, String kind) {
        textToken(token);
        List<Map<String, Object>> rows =
                mapper.findTicketForUpdate(secrets.hash("ticket", token), kind);
        if (rows.isEmpty()) {
            throw IdentityFailure.auth();
        }
        return rows.get(0);
    }

    private void consume(String token) {
        mapper.consumeTicket(secrets.hash("ticket", token));
    }

    @Override
    public void lock(String key) {
        mapper.lockIdentityScope(key);
    }

    private static void active(IdentityUser user) {
        if (!"ACTIVE".equals(user.status()) || user.controlled()) {
            throw IdentityFailure.auth();
        }
    }

    private static void textToken(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw IdentityFailure.auth();
        }
    }
}
