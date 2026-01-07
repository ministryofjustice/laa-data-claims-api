package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.envers.Audited;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;

/** Entity representing a validation message linked to a submission and optionally a claim. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Audited
@Table(name = "validation_message_log")
public class ValidationMessageLog {

  @Id private UUID id;

  @NotNull
  @Column(name = "submission_id", nullable = false)
  private UUID submissionId;

  @Column(name = "claim_id")
  private UUID claimId;

  @NotNull
  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private ValidationMessageType type;

  @NotNull
  @Column(nullable = false)
  private String source;

  @NotNull
  @Column(name = "display_message", nullable = false)
  private String displayMessage;

  @Column(name = "technical_message")
  private String technicalMessage;

  @CreationTimestamp
  @Column(name = "created_on", nullable = false, updatable = false)
  private Instant createdOn;
}
