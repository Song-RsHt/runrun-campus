package com.zhangxuan.runruncampus.model.order;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.order.OrderProofType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;







@Entity
@Table(
    name = "order_proofs",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_order_proof_order_type",
            columnNames = {"order_id", "type"}))
public class OrderProof {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", nullable = false)
  private CampusOrder order;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private AppUser uploader;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private OrderProofType type;

  @Column(nullable = false, unique = true, length = 160)
  private String storedFileName;

  @Column(nullable = false, length = 255)
  private String originalFileName;

  @Column(nullable = false, length = 64)
  private String contentType;

  @Column(nullable = false)
  private long sizeBytes;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  protected OrderProof() {}

  public OrderProof(
      CampusOrder order,
      AppUser uploader,
      OrderProofType type,
      String storedFileName,
      String originalFileName,
      String contentType,
      long sizeBytes) {
    this.order = order;
    this.uploader = uploader;
    this.type = type;
    this.storedFileName = storedFileName;
    this.originalFileName = originalFileName;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public CampusOrder getOrder() {
    return order;
  }

  public AppUser getUploader() {
    return uploader;
  }

  public OrderProofType getType() {
    return type;
  }

  public String getStoredFileName() {
    return storedFileName;
  }

  public String getOriginalFileName() {
    return originalFileName;
  }

  public String getContentType() {
    return contentType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
