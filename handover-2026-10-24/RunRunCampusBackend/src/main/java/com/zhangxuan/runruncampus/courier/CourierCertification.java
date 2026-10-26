package com.zhangxuan.runruncampus.courier;

import com.zhangxuan.runruncampus.user.AppUser;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "courier_certifications")
public class CourierCertification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // C M44-15: legacy edits must invalidate a concurrently previewed v2 decision.
    @Version
    private long version;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(unique = true)
    private AppUser courier;

    @Column(nullable = false, length = 48)
    private String realName;

    @Column(nullable = false, length = 40)
    private String studentId;

    @Column(nullable = false, length = 80)
    private String campus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CourierCertificationStatus status;

    @Column(length = 300)
    private String rejectionReason;

    @Column(nullable = false)
    private Instant submittedAt;

    private Instant reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser reviewer;

    private LocalDate requestedValidUntil;
    private LocalDate approvedValidUntil;

    @Column(length = 160)
    private String submittedPhotoKey;
    @Column(length = 32)
    private String submittedPhotoContentType;
    private Instant submittedPhotoAt;

    @Column(length = 160)
    private String approvedPhotoKey;
    @Column(length = 32)
    private String approvedPhotoContentType;

    @Column(nullable = false)
    private boolean approvalRetained;

    protected CourierCertification() {
    }

    public CourierCertification(AppUser courier, String realName, String studentId, String campus) {
        this.courier = courier;
        resubmit(realName, studentId, campus);
    }

    public CourierCertification(AppUser courier, String realName, String studentId, String campus,
                                LocalDate validUntil, boolean retainApproval) {
        this.courier = courier;
        resubmit(realName, studentId, campus, validUntil, retainApproval);
    }

    public void resubmit(String realName, String studentId, String campus) {
        resubmit(realName, studentId, campus, null, false);
    }

    public void resubmit(String realName, String studentId, String campus,
                         LocalDate validUntil, boolean retainApproval) {
        this.realName = realName;
        this.studentId = studentId;
        this.campus = campus;
        this.status = CourierCertificationStatus.PENDING;
        this.rejectionReason = null;
        this.submittedAt = Instant.now();
        this.reviewedAt = null;
        this.reviewer = null;
        this.requestedValidUntil = validUntil;
        this.approvalRetained = retainApproval;
        if (!retainApproval) courier.revokeCourierApproval();
    }

    public void declareValidity(LocalDate validUntil) { this.requestedValidUntil = validUntil; }

    public String attachSubmittedPhoto(String key, String contentType) {
        String previous = this.submittedPhotoKey;
        this.submittedPhotoKey = key;
        this.submittedPhotoContentType = contentType;
        this.submittedPhotoAt = Instant.now();
        return previous;
    }

    public boolean hasCurrentPhoto() {
        return status == CourierCertificationStatus.APPROVED
                ? approvedPhotoKey != null
                : submittedPhotoKey != null && submittedPhotoAt != null && !submittedPhotoAt.isBefore(submittedAt);
    }

    public void approve(AppUser reviewer) {
        this.status = CourierCertificationStatus.APPROVED;
        this.rejectionReason = null;
        this.reviewedAt = Instant.now();
        this.reviewer = reviewer;
        this.approvedValidUntil = null;
        this.approvedPhotoKey = submittedPhotoKey;
        this.approvedPhotoContentType = submittedPhotoContentType;
        this.submittedPhotoKey = null;
        this.submittedPhotoContentType = null;
        this.submittedPhotoAt = null;
        this.approvalRetained = false;
        courier.approveCourier();
    }

    public void reject(AppUser reviewer, String reason) {
        this.status = CourierCertificationStatus.REJECTED;
        this.rejectionReason = reason;
        this.reviewedAt = Instant.now();
        this.reviewer = reviewer;
        if (!approvalRetained) courier.revokeCourierApproval();
    }

    public Long getId() { return id; }
    public long getVersion() { return version; }
    public AppUser getCourier() { return courier; }
    public String getRealName() { return realName; }
    public String getStudentId() { return studentId; }
    public String getCampus() { return campus; }
    public CourierCertificationStatus getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public AppUser getReviewer() { return reviewer; }
    public LocalDate getRequestedValidUntil() { return requestedValidUntil; }
    public LocalDate getApprovedValidUntil() { return approvedValidUntil; }
    public String getSubmittedPhotoKey() { return submittedPhotoKey; }
    public String getSubmittedPhotoContentType() { return submittedPhotoContentType; }
    public String getApprovedPhotoKey() { return approvedPhotoKey; }
    public String getApprovedPhotoContentType() { return approvedPhotoContentType; }

    void clearSubmittedPhoto() {
        submittedPhotoKey = null;
        submittedPhotoContentType = null;
        submittedPhotoAt = null;
    }

    void clearApprovedPhoto() {
        approvedPhotoKey = null;
        approvedPhotoContentType = null;
    }
}
