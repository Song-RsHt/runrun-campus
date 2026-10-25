package com.zhangxuan.runruncampus.credit;

import com.zhangxuan.runruncampus.api.PlatformSettingsService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RiskSignalMaintenance {
    private final JdbcTemplate jdbc;
    private final PlatformSettingsService settings;

    public RiskSignalMaintenance(JdbcTemplate jdbc, PlatformSettingsService settings) {
        this.jdbc = jdbc;
        this.settings = settings;
    }

    @Scheduled(cron = "${app.risk.scan-cron:0 */10 * * * *}")
    @Transactional
    public void scan() {
        frequentCancellations();
        repeatedIssues();
        overdueCourierOrders(settings.get().deliveryMinutes());
    }

    private void frequentCancellations() {
        jdbc.update("""
                insert into risk_signals
                    (subject_user_id, signal_type, status, source_key, details, created_at)
                select candidate.user_id, 'FREQUENT_CANCELLATION', 'PENDING',
                       concat('CANCEL:', candidate.user_id, ':', candidate.last_event_id),
                       concat('该用户24小时内取消', candidate.event_count, '单，请人工核实是否异常'), now()
                from (
                    select event.actor_id as user_id, max(event.id) as last_event_id,
                           count(*) as event_count
                    from order_events event
                    join app_users user_account on user_account.id = event.actor_id
                    where event.status = 'CANCELLED'
                      and event.created_at >= now() - interval '24 hours'
                      and user_account.role = 'CUSTOMER'
                      and user_account.deleted_at is null
                    group by event.actor_id
                    having count(*) >= 3
                ) candidate
                where not exists (
                    select 1 from risk_signals pending
                    where pending.subject_user_id = candidate.user_id
                      and pending.signal_type = 'FREQUENT_CANCELLATION'
                      and pending.status = 'PENDING'
                )
                on conflict (source_key) do nothing
                """);
    }

    private void repeatedIssues() {
        jdbc.update("""
                insert into risk_signals
                    (subject_user_id, signal_type, status, source_key, details, created_at)
                select candidate.user_id, 'REPEATED_ISSUES', 'PENDING',
                       concat('ISSUE:', candidate.user_id, ':', candidate.last_issue_id),
                       concat('该用户30天内关联', candidate.issue_count, '个异常工单，请人工核实责任'), now()
                from (
                    select issue.reporter_id as user_id, max(issue.id) as last_issue_id,
                           count(*) as issue_count
                    from order_issues issue
                    join app_users user_account on user_account.id = issue.reporter_id
                    where issue.created_at >= now() - interval '30 days'
                      and user_account.role <> 'ADMIN'
                      and user_account.deleted_at is null
                    group by issue.reporter_id
                    having count(*) >= 3
                ) candidate
                where not exists (
                    select 1 from risk_signals pending
                    where pending.subject_user_id = candidate.user_id
                      and pending.signal_type = 'REPEATED_ISSUES'
                      and pending.status = 'PENDING'
                )
                on conflict (source_key) do nothing
                """);
    }

    private void overdueCourierOrders(int deliveryMinutes) {
        jdbc.update("""
                insert into risk_signals
                    (subject_user_id, signal_type, status, source_key, order_id, details, created_at)
                select orders.courier_id, 'ORDER_TIMEOUT', 'PENDING', concat('ORDER_TIMEOUT:', orders.id),
                       orders.id, concat('订单', orders.order_no, '超过配送提醒阈值，请人工核实'), now()
                from campus_orders orders
                join app_users courier on courier.id = orders.courier_id
                where orders.status in ('ACCEPTED', 'PICKED_UP')
                  and orders.updated_at < now() - (? * interval '1 minute')
                  and courier.deleted_at is null
                on conflict (source_key) do nothing
                """, deliveryMinutes);
    }
}
