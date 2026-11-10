package com.zhangxuan.runruncampus.repository.admin;

import com.zhangxuan.runruncampus.entity.admin.AdminAuditLog;
import com.zhangxuan.runruncampus.enums.admin.AdminAuditAction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 管理员操作审计日志 JPA 仓储；只定义持久化查询和写入，不承担权限及业务决策。 */
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    @Query(
            value =
                    """
                    select log from AdminAuditLog log
                    join fetch log.administrator administrator
                    where (:action is null or log.action = :action)
                      and (:query is null
                        or lower(administrator.displayName) like lower(concat('%', :query, '%'))
                        or administrator.phone like concat('%', :query, '%')
                        or lower(log.summary) like lower(concat('%', :query, '%')))
                    """,
            countQuery =
                    """
                    select count(log) from AdminAuditLog log
                    join log.administrator administrator
                    where (:action is null or log.action = :action)
                      and (:query is null
                        or lower(administrator.displayName) like lower(concat('%', :query, '%'))
                        or administrator.phone like concat('%', :query, '%')
                        or lower(log.summary) like lower(concat('%', :query, '%')))
                    """)
    Page<AdminAuditLog> search(
            @Param("query") String query,
            @Param("action") AdminAuditAction action,
            Pageable pageable);

    @Query(
            value =
                    """
                    select log from AdminAuditLog log
                    join fetch log.administrator
                    where (:action is null or log.action = :action)
                    """,
            countQuery =
                    "select count(log) from AdminAuditLog log where (:action is null or log.action"
                            + " = :action)")
    Page<AdminAuditLog> filter(@Param("action") AdminAuditAction action, Pageable pageable);
}
