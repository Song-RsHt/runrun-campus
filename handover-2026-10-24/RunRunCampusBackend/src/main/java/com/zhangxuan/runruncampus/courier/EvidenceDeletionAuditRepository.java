package com.zhangxuan.runruncampus.courier;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
public interface EvidenceDeletionAuditRepository extends JpaRepository<EvidenceDeletionAudit,Long> {
 @Query("select a from EvidenceDeletionAudit a where a.status in ('REQUESTED','FAILED') and a.deleteAfter <= :now")
 List<EvidenceDeletionAudit> findDue(@Param("now") Instant now);
}
