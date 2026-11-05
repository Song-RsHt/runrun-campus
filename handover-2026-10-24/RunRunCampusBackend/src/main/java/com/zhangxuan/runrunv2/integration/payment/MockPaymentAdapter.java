package com.zhangxuan.runrunv2.integration.payment;
import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.model.payment.PaymentModels;

import static com.zhangxuan.runrunv2.model.payment.PaymentModels.*;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Database-backed isolated channel. No merchant SDK, network call or client amount. */
@Component
@ConditionalOnProperty(name="rrc.payment.mock-enabled",havingValue="true")
public class MockPaymentAdapter {
    private final JdbcTemplate db;
    public MockPaymentAdapter(JdbcTemplate db, @Value("${rrc.identity.environment}") String environment, org.springframework.core.env.Environment spring) {
        if(!Set.of("local","test","staging").contains(environment) || spring.acceptsProfiles(org.springframework.core.env.Profiles.of("production","prod")))
            throw new IllegalStateException("Mock payments require an isolated environment");
        this.db=db;
    }
    public void submit(String type, String id) {
        // Persist channel acceptance in the caller's transaction; acceptance is never a receipt.
        db.update("INSERT INTO runrun_v2.mock_channel_results(resource_type,resource_id,result) VALUES(?,?,'PROCESSING')",type,Long.valueOf(id));
    }
    public Result query(String type, String id) {
        var rows=db.queryForList("SELECT result FROM runrun_v2.mock_channel_results WHERE resource_type=? AND resource_id=?",type,Long.valueOf(id));
        return rows.isEmpty()?Result.UNKNOWN:Result.valueOf(rows.get(0).get("result").toString());
    }
    public long revision(String type,String id) {
        return db.queryForObject("SELECT version FROM runrun_v2.mock_channel_results WHERE resource_type=? AND resource_id=?",Long.class,type,Long.valueOf(id));
    }
    public void record(String type,String id,Result result) {
        // A previously confirmed receipt cannot turn back into failure/processing.
        db.update("UPDATE runrun_v2.mock_channel_results SET result=?,version=version+1 WHERE resource_type=? AND resource_id=? AND result<>'SUCCESS'",result.name(),type,Long.valueOf(id));
    }
    public void retryRefund(String id) {
        // Retry the original channel identity; never create a second refund transfer.
        if(db.update("UPDATE runrun_v2.mock_channel_results SET result='PROCESSING',version=version+1 WHERE resource_type='REFUND' AND resource_id=? AND result='FAILED'",Long.valueOf(id))!=1)
            throw new IdentityFailure(409,"PAYMENT_RESULT_PENDING");
    }
}
