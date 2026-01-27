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
import org.javers.core.metamodel.annotation.DiffIgnore;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;

/** Entity representing a validation message linked to a submission and optionally a claim. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "validation_message_log")
// @DiffIgnore // It’s already a durable log.
// Using Javers for a log table compounds storage for minimal insight.
// We could also add @DiffIgnore to every property except maybe the id?
public class ValidationMessageLog {

  @DiffIgnore @Id private UUID id;

  @DiffIgnore
  @NotNull
  @Column(name = "submission_id", nullable = false)
  private UUID submissionId;

  @DiffIgnore
  @Column(name = "claim_id")
  private UUID claimId;

  @DiffIgnore
  @NotNull
  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private ValidationMessageType type;

  @DiffIgnore
  @NotNull
  @Column(nullable = false)
  private String source;

  @DiffIgnore
  @NotNull
  @Column(name = "display_message", nullable = false)
  private String displayMessage;

  @DiffIgnore
  @Column(name = "technical_message")
  private String technicalMessage;

  @DiffIgnore
  @CreationTimestamp
  @Column(name = "created_on", nullable = false, updatable = false)
  private Instant createdOn;
}
