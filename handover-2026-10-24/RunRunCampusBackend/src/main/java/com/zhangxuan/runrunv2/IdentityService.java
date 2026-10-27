package com.zhangxuan.runrunv2;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {
    private final JdbcTemplate db;
    private final IdentitySecrets secrets;
    private final WechatGateway wechat;
    private final String appId;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
    private final String dummyPassword = passwords.encode("synthetic-unmatchable-password");

    public record Account(String id, String role, String status, boolean adminEnabled, long securityVersion, boolean courierEligible) {}
    public record LoginResult(String loginTicket, String token, Account account) {}
    record User(long id, String role, String status, boolean enabled, long securityVersion, boolean controlled, String passwordHash) {}
    private static final RowMapper<User> USER = (r, n) -> new User(r.getLong("id"), r.getString("role"), r.getString("status"),
            r.getBoolean("v2_admin_enabled"), r.getLong("v2_admin_security_version"), r.getBoolean("controlled_identity"), r.getString("password_hash"));

    public IdentityService(JdbcTemplate db, IdentitySecrets secrets, WechatGateway wechat,
                           @Value("${rrc.identity.app-id}") String appId) {
        this.db = db; this.secrets = secrets; this.wechat = wechat; this.appId = appId;
    }
    @Transactional
    public LoginResult login(String code) {
        text(code, 512);
        String openid = wechat.exchangeLoginCode(code);
        String digest = secrets.hash("openid:" + appId, openid);
        lock("wechat:" + appId + ":" + digest);
        List<User> users = db.query("SELECT u.* FROM runrun_v2.app_users u JOIN runrun_v2.wechat_identities w ON w.user_id=u.id WHERE w.app_id=? AND w.openid_hash=? FOR UPDATE OF u", USER, appId, digest);
        if (!users.isEmpty()) return issue(users.get(0));
        String ticket = secrets.token();
        db.update("INSERT INTO runrun_v2.identity_tickets(token_hash,kind,app_id,openid_hash,openid_ciphertext,expires_at) VALUES(?,'WECHAT_LOGIN',?,?,?,now()+interval '5 minutes')",
                secrets.hash("ticket", ticket), appId, digest, secrets.encrypt(appId, openid));
        return new LoginResult(ticket, null, null);
    }
    @Transactional
    public LoginResult bindPhone(String loginTicket, String phoneCode, String requestedRole) {
        text(loginTicket, 128); text(phoneCode, 512);
        if (!"USER".equals(requestedRole) && !"COURIER".equals(requestedRole)) throw IdentityFailure.invalid();
        Map<String,Object> ticket = ticket(loginTicket, "WECHAT_LOGIN");
        if (!appId.equals(ticket.get("app_id"))) throw IdentityFailure.auth();
        String phone = wechat.exchangePhoneCode(phoneCode);
        // The phone belongs to Wechat's server response, never to a client field.
        if (phone == null || !phone.matches("1[3-9][0-9]{9}")) throw new IdentityFailure(401, "WECHAT_AUTH_FAILED");
        lock("phone:" + phone);
        lock("wechat:" + appId + ":" + ticket.get("openid_hash"));
        if (count("SELECT count(*) FROM runrun_v2.wechat_identities WHERE app_id=? AND openid_hash=?", appId, ticket.get("openid_hash")) > 0) throw IdentityFailure.conflict();
        List<User> existing = db.query("SELECT * FROM runrun_v2.app_users WHERE phone=? FOR UPDATE", USER, phone);
        if (!existing.isEmpty()) throw IdentityFailure.conflict();
        Long id = db.queryForObject("INSERT INTO runrun_v2.app_users(phone,role) VALUES(?,?) RETURNING id", Long.class, phone, requestedRole);
        User user = user(id, true);
        if ("COURIER".equals(requestedRole)) db.update("INSERT INTO runrun_v2.courier_certifications(courier_id) VALUES(?)", id);
        active(user);
        if (count("SELECT count(*) FROM runrun_v2.wechat_identities WHERE app_id=? AND user_id=?", appId, user.id) > 0) throw IdentityFailure.conflict();
        db.update("INSERT INTO runrun_v2.wechat_identities(user_id,app_id,openid_hash,openid_ciphertext) VALUES(?,?,?,?)",
                user.id, appId, ticket.get("openid_hash"), ticket.get("openid_ciphertext"));
        consume(loginTicket);
        db.update("INSERT INTO runrun_v2.identity_audit_events(actor_id,event_type,subject_id) VALUES(?,'WECHAT_BOUND',?)", user.id, user.id);
        return issue(user);
    }
    @Transactional
    public LoginResult passwordLogin(String phone, String password) {
        text(phone, 32); text(password, 72);
        List<User> users = db.query("SELECT * FROM runrun_v2.app_users WHERE phone=? FOR UPDATE", USER, phone);
        String hash = users.isEmpty() || users.get(0).passwordHash == null ? dummyPassword : users.get(0).passwordHash;
        if (!passwords.matches(password, hash) || users.isEmpty()) throw IdentityFailure.auth();
        User user = users.get(0);
        if (user.passwordHash == null || count("SELECT count(*) FROM runrun_v2.wechat_identities WHERE user_id=? AND app_id=?", user.id, appId) == 0) throw IdentityFailure.auth();
        return issue(user);
    }
    public Account authenticate(String token) {
        textToken(token);
        List<User> users = db.query("SELECT u.* FROM runrun_v2.user_sessions s JOIN runrun_v2.app_users u ON u.id=s.user_id WHERE s.token_hash=? AND s.invalidated_at IS NULL AND s.expires_at>now() AND s.security_version=u.v2_admin_security_version", USER, secrets.hash("session", token));
        if (users.size() != 1) throw IdentityFailure.auth();
        active(users.get(0));
        return account(users.get(0));
    }
    public Account requireRole(String token, String role, boolean needsCourierEligibility) {
        Account account = authenticate(token);
        if (!role.equals(account.role)) throw new IdentityFailure(403, "FORBIDDEN");
        if ("ADMIN".equals(role) && !account.adminEnabled) throw new IdentityFailure(403, "V2_ADMIN_NOT_ENABLED");
        if ("COURIER".equals(role) && needsCourierEligibility && !account.courierEligible) throw new IdentityFailure(403, "COURIER_NOT_APPROVED");
        return account;
    }
    @Transactional
    public void revoke(String token, boolean all, String key) {
        Account account = authenticate(token);
        user(Long.parseLong(account.id), true);
        authenticate(token);
        // Bind single-session logout idempotency to the exact session being revoked.
        // Reusing a key from another active session must conflict instead of silently
        // reporting the first request as a replay.
        String logoutContent = all
                ? "all:true"
                : "all:false:session:" + secrets.hash("session", token);
        if (!beginWrite(account.id, "identity:logout", key, logoutContent)) return;
        if (all) db.update("UPDATE runrun_v2.user_sessions SET invalidated_at=now() WHERE user_id=? AND invalidated_at IS NULL", Long.valueOf(account.id));
        else db.update("UPDATE runrun_v2.user_sessions SET invalidated_at=now() WHERE token_hash=? AND invalidated_at IS NULL", secrets.hash("session", token));
        db.update("INSERT INTO runrun_v2.identity_audit_events(actor_id,event_type,subject_id) VALUES(?,'SESSION_REVOKED',?)", Long.valueOf(account.id), Long.valueOf(account.id));
        finishWrite(account.id, "identity:logout", key);
    }
    @Transactional
    public void setPassword(String token, String password, String key) {
        Account account = authenticate(token);
        if (password == null || password.length() < 12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) throw IdentityFailure.invalid();
        User user = user(Long.parseLong(account.id), true);
        active(user);
        authenticate(token);
        if (!beginWrite(account.id, "identity:password", key, password)) return;
        db.update("UPDATE runrun_v2.app_users SET password_hash=?,version=version+1,updated_at=now() WHERE id=?", passwords.encode(password), user.id);
        // Password changes revoke every session, including the caller; no hidden surviving session.
        db.update("UPDATE runrun_v2.user_sessions SET invalidated_at=now() WHERE user_id=? AND invalidated_at IS NULL", user.id);
        db.update("INSERT INTO runrun_v2.identity_audit_events(actor_id,event_type,subject_id) VALUES(?,'SESSION_REVOKED',?)", user.id, user.id);
        finishWrite(account.id, "identity:password", key);
    }
    /** Business writes must invoke this inside their own transaction and hold the account lock to commit. */
    public Account authorizeForWrite(String token, String role, boolean courierEligibility) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Business authorization requires a transaction");
        Account account = authenticate(token);
        user(Long.parseLong(account.id), true);
        return requireRole(token, role, courierEligibility);
    }
    private boolean beginWrite(String id, String scope, String key, String content) {
        text(key, 128);
        String digest = secrets.hash("write:" + scope, content);
        List<String> existing = db.query("SELECT request_hash FROM runrun_v2.idempotency_requests WHERE actor_id=? AND scope=? AND key=?", (r,n) -> r.getString(1), Long.valueOf(id), scope, key);
        if (!existing.isEmpty()) {
            if (!digest.equals(existing.get(0))) throw new IdentityFailure(409, "IDEMPOTENCY_KEY_CONFLICT");
            return false;
        }
        db.update("INSERT INTO runrun_v2.idempotency_requests(actor_id,scope,key,request_hash,status) VALUES(?,?,?,?,'PROCESSING')", Long.valueOf(id), scope, key, digest);
        return true;
    }
    private void finishWrite(String id, String scope, String key) {
        db.update("UPDATE runrun_v2.idempotency_requests SET status='SUCCEEDED',updated_at=now() WHERE actor_id=? AND scope=? AND key=?", Long.valueOf(id), scope, key);
    }
    User user(long id, boolean locked) {
        List<User> users = db.query("SELECT * FROM runrun_v2.app_users WHERE id=?" + (locked ? " FOR UPDATE" : ""), USER, id);
        if (users.isEmpty()) throw new IdentityFailure(404, "RESOURCE_NOT_FOUND");
        return users.get(0);
    }
    private LoginResult issue(User user) {
        active(user);
        String token = secrets.token();
        db.update("INSERT INTO runrun_v2.user_sessions(user_id,token_hash,expires_at,security_version) VALUES(?,?,now()+interval '24 hours',?)",
                user.id, secrets.hash("session", token), user.securityVersion);
        return new LoginResult(null, token, account(user));
    }
    private Account account(User user) {
        boolean eligible = "COURIER".equals(user.role) && Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM runrun_v2.courier_certifications WHERE courier_id=? AND eligible)", Boolean.class, user.id));
        return new Account(Long.toString(user.id), user.role, user.status, user.enabled, user.securityVersion, eligible);
    }
    private Map<String,Object> ticket(String token, String kind) {
        textToken(token);
        List<Map<String,Object>> rows = db.queryForList("SELECT * FROM runrun_v2.identity_tickets WHERE token_hash=? AND kind=? AND consumed_at IS NULL AND expires_at>now() FOR UPDATE", secrets.hash("ticket", token), kind);
        if (rows.isEmpty()) throw IdentityFailure.auth();
        return rows.get(0);
    }
    private void consume(String token) { db.update("UPDATE runrun_v2.identity_tickets SET consumed_at=now() WHERE token_hash=?", secrets.hash("ticket", token)); }
    void lock(String key) { db.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", (RowMapper<Object>) (r,n) -> null, key); }
    private long count(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
    private static void active(User user) { if (!"ACTIVE".equals(user.status) || user.controlled) throw IdentityFailure.auth(); }
    static void text(String value, int max) { if (value == null || value.isBlank() || value.length() > max) throw IdentityFailure.invalid(); }
    private static void textToken(String token) { if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw IdentityFailure.auth(); }
}
