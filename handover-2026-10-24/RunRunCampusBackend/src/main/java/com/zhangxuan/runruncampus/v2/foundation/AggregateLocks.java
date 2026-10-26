package com.zhangxuan.runruncampus.v2.foundation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;

@Component
public class AggregateLocks {
    // Capacity/identity locks precede order locks; all callers supply the full plan up front.
    public enum Kind {
        CONFIG("platform_settings"), ACCOUNT("app_users"), CERTIFICATION("courier_certifications"),
        ORDER("v2_orders"), PAYMENT("payment_orders"), SETTLEMENT("v2_orders");
        private final String table;
        Kind(String table) { this.table = table; }
    }
    public record Subject(Kind kind, long id) implements Comparable<Subject> {
        public Subject { Objects.requireNonNull(kind); if (id <= 0) throw new IllegalArgumentException("Positive id required"); }
        @Override public int compareTo(Subject other) {
            int type = kind.compareTo(other.kind);
            return type != 0 ? type : Long.compare(id, other.id);
        }
    }
    private final JdbcTemplate jdbc;
    private final Object contextKey = new Object();
    public AggregateLocks(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void acquire(Collection<Subject> subjects) {
        TransactionRequired.check();
        @SuppressWarnings("unchecked")
        TreeSet<Subject> held = (TreeSet<Subject>) TransactionSynchronizationManager.getResource(contextKey);
        if (held == null) {
            held = new TreeSet<>();
            TransactionSynchronizationManager.bindResource(contextKey, held);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(contextKey);
                }
            });
        }
        for (Subject subject : new TreeSet<>(subjects)) {
            if (held.contains(subject)) continue;
            if (!held.isEmpty() && subject.compareTo(held.last()) < 0)
                throw new IllegalStateException("Lock plan must follow CONFIG/ACCOUNT/CERTIFICATION/ORDER/PAYMENT/SETTLEMENT order");
            // Typed namespace prevents a certification id from aliasing the same numeric order id.
            advisory("v2:" + subject.kind() + ":" + subject.id());
            List<Long> found = jdbc.queryForList("select id from " + subject.kind().table + " where id=? for update", Long.class, subject.id());
            ResourceChecks.visible(!found.isEmpty());
            held.add(subject);
        }
    }

    void advisory(String key) {
        TransactionRequired.check();
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?,0))", rs -> { return null; }, key);
    }
}
