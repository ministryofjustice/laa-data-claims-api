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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;

/** Entity representing a submission associated with a bulk submission. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "submission")
public class Submission {

  @Id private UUID id;

  @NotNull
  @Column(nullable = false)
  private UUID bulkSubmissionId;

  @NotNull
  @Column(nullable = false)
  private String officeAccountNumber;

  @NotNull
  @Column(nullable = false)
  private String submissionPeriod;

  @NotNull
  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private AreaOfLaw areaOfLaw;

  @Enumerated(EnumType.STRING)
  private SubmissionStatus status;

  private String crimeLowerScheduleNumber;

  private String legalHelpSubmissionReference;

  private String mediationSubmissionReference;

  private UUID previousSubmissionId;

  private Boolean isNilSubmission;

  private Integer numberOfClaims;

  private String errorMessages;

  @Column(nullable = false)
  private String createdByUserId;

  @Column(nullable = false)
  private String providerUserId;

  @Column(nullable = false)
  private Instant createdOn;

  private String updatedByUserId;

  @UpdateTimestamp
  @Column(nullable = false)
  private Instant updatedOn;
}
