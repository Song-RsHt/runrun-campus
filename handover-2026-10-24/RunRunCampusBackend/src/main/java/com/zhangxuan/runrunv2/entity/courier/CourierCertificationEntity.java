package com.zhangxuan.runrunv2.entity.courier;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/** 骑手资质实体；首次创建沿用 NOT_APPLIED 默认值，审核和照片清理仍使用原条件 SQL。 */
@TableName(value = "courier_certifications", schema = "runrun_v2")
public class CourierCertificationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long courierId;

    private String status;

    private Boolean eligible;

    private String submittedPhotoResourceId;

    private String submittedPhotoStorageKey;

    private String approvedPhotoResourceId;

    private String approvedPhotoStorageKey;

    private String rejectionReason;

    private OffsetDateTime submittedAt;

    private OffsetDateTime reviewedAt;

    private OffsetDateTime approvedPhotoAt;

    private Long version;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCourierId() {
        return courierId;
    }

    public void setCourierId(Long courierId) {
        this.courierId = courierId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getEligible() {
        return eligible;
    }

    public void setEligible(Boolean eligible) {
        this.eligible = eligible;
    }

    public String getSubmittedPhotoResourceId() {
        return submittedPhotoResourceId;
    }

    public void setSubmittedPhotoResourceId(String submittedPhotoResourceId) {
        this.submittedPhotoResourceId = submittedPhotoResourceId;
    }

    public String getSubmittedPhotoStorageKey() {
        return submittedPhotoStorageKey;
    }

    public void setSubmittedPhotoStorageKey(String submittedPhotoStorageKey) {
        this.submittedPhotoStorageKey = submittedPhotoStorageKey;
    }

    public String getApprovedPhotoResourceId() {
        return approvedPhotoResourceId;
    }

    public void setApprovedPhotoResourceId(String approvedPhotoResourceId) {
        this.approvedPhotoResourceId = approvedPhotoResourceId;
    }

    public String getApprovedPhotoStorageKey() {
        return approvedPhotoStorageKey;
    }

    public void setApprovedPhotoStorageKey(String approvedPhotoStorageKey) {
        this.approvedPhotoStorageKey = approvedPhotoStorageKey;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(OffsetDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(OffsetDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public OffsetDateTime getApprovedPhotoAt() {
        return approvedPhotoAt;
    }

    public void setApprovedPhotoAt(OffsetDateTime approvedPhotoAt) {
        this.approvedPhotoAt = approvedPhotoAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
