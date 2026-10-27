package com.zhangxuan.runruncampus.courier;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface EvidenceHoldRepository extends JpaRepository<EvidenceHold,Long> {
 @Query("select count(h)>0 from EvidenceHold h where h.certification.id=:id and h.releasedAt is null") boolean existsActive(@Param("id") Long id);
}
