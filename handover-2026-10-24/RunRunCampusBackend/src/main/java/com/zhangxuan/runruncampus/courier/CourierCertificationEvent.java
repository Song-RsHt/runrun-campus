package com.zhangxuan.runruncampus.courier;
import com.zhangxuan.runruncampus.user.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="courier_certification_events")
public class CourierCertificationEvent {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) private CourierCertification certification;
 @ManyToOne(fetch=FetchType.LAZY) private AppUser actor;
 @Column(nullable=false,length=16) private String actorKind;
 @Column(nullable=false,length=48) private String eventType;
 @Column(nullable=false,length=80) private String requestId;
 @Column(nullable=false) private long certificationVersion;
 @Column(nullable=false) private Instant createdAt=Instant.now();
 protected CourierCertificationEvent(){}
 public CourierCertificationEvent(CourierCertification c,AppUser actor,String kind,String type,String requestId){this.certification=c;this.actor=actor;this.actorKind=kind;this.eventType=type;this.requestId=requestId;this.certificationVersion=c.getVersion();}
}
