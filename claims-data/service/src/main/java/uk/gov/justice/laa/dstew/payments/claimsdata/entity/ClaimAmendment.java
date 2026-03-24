package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Entity representing a claim amendment. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "claim_amendment")
public class ClaimAmendment {
  @Id
  @Column(name = "claim_amendment_id", nullable = false)
  private UUID claimAmendmentId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "claim_id", nullable = false)
  private Claim claim;

  @Column(name = "created_by_user_id", nullable = false)
  private String createdByUserId;

  @CreationTimestamp
  @Column(name = "created_on", nullable = false)
  private OffsetDateTime createdOn;

  @Column(name = "updated_by_user_id")
  private String updatedByUserId;

  @UpdateTimestamp
  @Column(name = "updated_on")
  private OffsetDateTime updatedOn;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "changed_fields", columnDefinition = "jsonb", nullable = false)
  private String changedFields;
}
