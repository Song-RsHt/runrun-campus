package com.zhangxuan.runruncampus.model.courier;
import com.zhangxuan.runruncampus.enums.courier.CourierCertificationStatus;

import com.zhangxuan.runruncampus.model.user.AppUser;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "courier_certifications")
public class CourierCertification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Transient
    private long version;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(unique = true)
    private AppUser courier;

    @Column(name="real_name", nullable = false, length = 48)
    private String realName;

    @Column(name="student_id", nullable = false, length = 40)
    private String studentId;

    @Column(name="campus", nullable = false, length = 80)
    private String campus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CourierCertificationStatus status;

    @Column(length = 300)
    private String rejectionReason;

    private Instant submittedAt;

    @Column(name="reviewed_at") private Instant reviewedAt;

    @Transient
    private AppUser reviewer;

    @Transient private LocalDate requestedValidUntil;
    @Transient private LocalDate approvedValidUntil;

    @Column(name="submitted_photo_key", length = 160)
    private String submittedPhotoKey;
    @Transient
    private String submittedPhotoContentType;
    @Transient private Instant submittedPhotoAt;

    @Column(name="approved_photo_key", length = 160)
    private String approvedPhotoKey;
    @Transient
    private String approvedPhotoContentType;
    @Transient
    private String submittedPhotoResourceId;
    @Transient
    private String approvedPhotoResourceId;
    @Transient private Instant approvedPhotoAt;

    @Transient
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
        this.submittedPhotoResourceId = "student-card-" + UUID.randomUUID();
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
        this.approvedPhotoResourceId = submittedPhotoResourceId;
        this.approvedPhotoAt = Instant.now();
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
    public long getVersion() { return version; }
    public String getSubmittedPhotoResourceId() { return submittedPhotoResourceId; }
    public String getApprovedPhotoResourceId() { return approvedPhotoResourceId; }
    public Instant getApprovedPhotoAt() { return approvedPhotoAt; }

    public void clearSubmittedPhoto() {
        submittedPhotoKey = null;
        submittedPhotoContentType = null;
        submittedPhotoAt = null;
        submittedPhotoResourceId = null;
    }

    public void clearApprovedPhoto() {
        approvedPhotoKey = null;
        approvedPhotoContentType = null;
        approvedPhotoResourceId = null;
        approvedPhotoAt = null;
    }
}
