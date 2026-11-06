package com.zhangxuan.runruncampus.mapper;

import com.zhangxuan.runruncampus.model.DispatchCandidateRow;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

/** 管理员派单数据访问；使用调用方事务绑定的 JDBC 连接，不开启或提交事务。 */
@Repository
public class AdminDispatchMapper {

    private static final String CANDIDATE_SQL =
            """
select u.id, u.display_name, u.courier_availability_updated_at,
    coalesce(metrics.active_orders, 0) as active_orders,
    coalesce(metrics.completed_orders, 0) as completed_orders,
    coalesce(metrics.matched_completed_orders, 0) as matched_completed_orders,
    metrics.average_rating, coalesce(metrics.rating_count, 0) as rating_count
from app_users u
left join courier_certifications cert on cert.courier_id = u.id
left join lateral (
    select count(*) filter (where history.status in ('ACCEPTED', 'PICKED_UP')) as active_orders,
        count(*) filter (where history.status = 'COMPLETED') as completed_orders,
        count(*) filter (where history.status = 'COMPLETED' and history.service_type = ?) as matched_completed_orders,
        avg(history.rating_score) filter (where history.rating_score is not null) as average_rating,
        count(history.rating_score) as rating_count
    from campus_orders history where history.courier_id = u.id
) metrics on true
where u.role = 'COURIER' and u.enabled = true
  and u.courier_approved is distinct from false
  and u.courier_accepting_orders = true
  and coalesce(metrics.active_orders, 0) < ?
  and (cert.approved_valid_until is null
       or cert.approved_valid_until >= (now() at time zone 'Asia/Shanghai')::date)
""";

    private final JdbcTemplate jdbc;

    public AdminDispatchMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DispatchCandidateRow> findCandidates(String serviceType, int maxActiveOrders) {
        return jdbc.query(
                CANDIDATE_SQL,
                (rs, row) -> {
                    double rating = rs.getDouble("average_rating");
                    Double averageRating = rs.wasNull() ? null : rating;
                    Timestamp availability = rs.getTimestamp("courier_availability_updated_at");
                    return new DispatchCandidateRow(
                            rs.getLong("id"),
                            rs.getString("display_name"),
                            rs.getLong("active_orders"),
                            availability == null ? null : availability.toInstant(),
                            rs.getLong("completed_orders"),
                            rs.getLong("matched_completed_orders"),
                            averageRating,
                            rs.getLong("rating_count"));
                },
                serviceType,
                maxActiveOrders);
    }
}
