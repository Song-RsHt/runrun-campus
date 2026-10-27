package com.zhangxuan.runruncampus.courier;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "evidence_deletion_audits")
public class EvidenceDeletionAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) private CourierCertification certification;
    @Column(nullable=false,length=32) private String resourceType;
    @Column(nullable=false,length=128) private String resourceId;
    @Column(length=256) private String resourceStorageKey;
    @Column(nullable=false) private Instant deleteAfter;
    @Column(nullable=false,length=16) private String status;
    @Column(nullable=false) private int attemptNo;
    private Instant finishedAt;
    @Column(length=80) private String errorCode;
    protected EvidenceDeletionAudit() {}
    public EvidenceDeletionAudit(CourierCertification certification, String type, String id, String key, Instant after) {
        this.certification=certification; this.resourceType=type; this.resourceId=id; this.resourceStorageKey=key;
        this.deleteAfter=after; this.status="REQUESTED";
    }
    public String getResourceStorageKey(){return resourceStorageKey;}
    public String getStatus(){return status;}
    public int getAttemptNo(){return attemptNo;}
    public void deleting(){status="DELETING"; attemptNo++;}
    public void succeeded(){status="SUCCEEDED"; finishedAt=Instant.now(); resourceStorageKey=null;}
    public void failed(String code){status="FAILED"; errorCode=code;}
}
