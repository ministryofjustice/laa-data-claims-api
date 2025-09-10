package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Entity representing a validation error linked to a submission and optionally a claim. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "validation_error_log")
public class ValidationErrorLog {

  @Id private UUID id;

  @NotNull
  @Column(name = "submission_id", nullable = false)
  private UUID submissionId;

  @Column(name = "claim_id")
  private UUID claimId;

  @NotNull
  @Column(nullable = false)
  private String errorCode;

  @NotNull
  @Column(nullable = false)
  private String errorDescription;

  @NotNull
  @Column(nullable = false)
  private String createdByUserId;

  @CreationTimestamp
  @Column(nullable = false)
  private Instant createdOn;

  private String updatedByUserId;

  @UpdateTimestamp
  @Column(nullable = false)
  private Instant updatedOn;
}
