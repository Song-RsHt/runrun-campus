package com.zhangxuan.runruncampus.courier;
import com.zhangxuan.runruncampus.user.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="evidence_holds")
public class EvidenceHold {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY) private CourierCertification certification;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) private AppUser owner;
 @Column(nullable=false) private String reason;
 @Column(nullable=false) private Instant reviewDueAt;
 private Instant releasedAt;
 protected EvidenceHold() {}
 public EvidenceHold(CourierCertification c, AppUser owner, String reason, Instant due){this.certification=c;this.owner=owner;this.reason=reason;this.reviewDueAt=due;}
 public boolean active(){return releasedAt==null;}
}
