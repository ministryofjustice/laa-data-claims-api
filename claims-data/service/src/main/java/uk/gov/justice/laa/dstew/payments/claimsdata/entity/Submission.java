package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;

/**
 * Entity representing a submission associated with a bulk submission.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "submission")
public class Submission {
  /** Primary key for the submission. */
  @Id private UUID id;

  /** Identifier of the related bulk submission. */
  @Column(name = "bulk_submission_id", nullable = false) private UUID bulkSubmissionId;

  /** Office account number for the submission. */
  @Column(name = "office_account_number", nullable = false) private String officeAccountNumber;

  /** Submission period e.g. "2025-07". */
  @Column(name = "submission_period", nullable = false) private String submissionPeriod;

  /** Area of law e.g. "crime" or "civil". */
  @Column(name = "area_of_law", nullable = false) private String areaOfLaw;

  /** Current status of the submission. */
  @Enumerated(EnumType.STRING) @Column(nullable = false) private SubmissionStatus status;

  /** Optional schedule number. */
  @Column(name = "schedule_number") private String scheduleNumber;

  /** ID of the previous submission this replaces. */
  @Column(name = "previous_submission_id") private UUID previousSubmissionId;

  /** Whether this submission contains no claims. */
  @Column(name = "is_nil_submission") private Boolean isNilSubmission;

  /** Number of claims included in this submission. */
  @Column(name = "number_of_claims") private Integer numberOfClaims;

  /** Error messages associated with the submission. */
  @Column(name = "error_messages") private String errorMessages;

  /** User identifier for the creator of the submission. */
  @Column(name = "created_by_user_id", nullable = false) private String createdByUserId;

  /** Timestamp when the submission was created. */
  @CreationTimestamp
  @Column(name = "created_on", nullable = false)
  private Instant createdOn;

  /** User identifier of the last user who updated the submission. */
  @Column(name = "updated_by_user_id") private String updatedByUserId;

  /** Timestamp of the last update. */
  @UpdateTimestamp
  @Column(name = "updated_on")
  private Instant updatedOn;
}
