package com.zhangxuan.runrunv2;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Host-authorized synthetic fixtures only; never available as an HTTP bootstrap. */
@Component
@Profile("local-v2-test")
public class LocalTestBootstrap implements ApplicationRunner {
    private final JdbcTemplate db;
    private final Environment env;
    private final IdentitySecrets secrets;
    private final DeploymentIdentityService deployment;
    private final TransactionTemplate tx;
    public LocalTestBootstrap(JdbcTemplate db, Environment env, IdentitySecrets secrets,
                              DeploymentIdentityService deployment, org.springframework.transaction.PlatformTransactionManager manager,
                              IdentityStartupGuard guard) {
        this.db=db; this.env=env; this.secrets=secrets; this.deployment=deployment; this.tx=new TransactionTemplate(manager);
    }
    @Override public void run(ApplicationArguments args) {
        if (!"test".equals(env.getProperty("RRC_V2_ENVIRONMENT"))
                || !"jdbc:postgresql://postgres:5432/rrc_v2_test".equals(env.getProperty("RRC_V2_JDBC_URL"))
                || !"true".equals(env.getProperty("rrc.payment.mock-enabled"))
                || !"false".equals(env.getProperty("rrc.identity.wechat-enabled"))
                || !"synthetic-local-app".equals(env.getProperty("rrc.identity.app-id"))
                || !"test-admin".equals(env.getProperty("RRC_V2_ADMIN_PHONE"))
                || !"CREATE_ISOLATED_TEST_ADMIN".equals(env.getProperty("RRC_V2_BOOTSTRAP_CONFIRM"))) {
            throw new IllegalStateException("Local v2 bootstrap requires isolated Compose DB, test environment, mock payment, disabled WeChat and explicit fixture confirmation");
        }
        String password=env.getRequiredProperty("RRC_V2_ADMIN_PASSWORD");
        if (password.length()<16 || password.getBytes(StandardCharsets.UTF_8).length>72 || password.contains("REPLACE"))
            throw new IllegalStateException("Generate a local fixture password (16-72 bytes)");
        var encoder=new BCryptPasswordEncoder(12);
        tx.executeWithoutResult(status -> {
            db.execute("SELECT pg_advisory_xact_lock(7082001)");
            var existing=db.queryForList("SELECT password_hash,role,v2_admin_enabled FROM runrun_v2.app_users WHERE phone='test-admin'");
            if (!existing.isEmpty()) {
                var account=existing.get(0);
                if (!"ADMIN".equals(account.get("role")) || !Boolean.TRUE.equals(account.get("v2_admin_enabled"))
                        || !encoder.matches(password, (String)account.get("password_hash"))
                        || !Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM runrun_v2.admin_audit_logs WHERE request_id='local-test-enable' AND reason='Isolated synthetic local test fixture')",Boolean.class)))
                    throw new IllegalStateException("Local fixture differs; no automatic reset or privilege restoration. Restore local env file or recreate test volumes.");
                return;
            }
            if (db.queryForObject("SELECT count(*) FROM runrun_v2.app_users",Long.class)!=0)
                throw new IllegalStateException("Local fixture initialization requires an empty v2 account database");
            long actor=Long.parseLong(deployment.bootstrap("Isolated synthetic local test fixture", "CREATE DEPLOYMENT IDENTITY", "local-test-deployer"));
            long id=db.queryForObject("INSERT INTO runrun_v2.app_users(phone,role,password_hash) VALUES('test-admin','USER',?) RETURNING id",Long.class,encoder.encode(password));
            String app="synthetic-local-app", openid="local-test-admin";
            db.update("INSERT INTO runrun_v2.wechat_identities(user_id,app_id,openid_hash,openid_ciphertext) VALUES(?,?,?,?)",
                    id,app,secrets.hash("openid:"+app,openid),secrets.encrypt(app,openid));
            // Reuse controlled provisioning so access versions, revocation and audit follow C01.
            deployment.provisionAdmin(actor,id,0,"Isolated synthetic local test fixture","CREATE ADMIN "+id,"local-test-create");
            deployment.changeAccess(actor,id,true,1,"Isolated synthetic local test fixture","ENABLE "+id,"local-test-enable");
        });
    }
}
