package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

/** Entity representing a claim linked to a submission. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "claim")
public class Claim {

  @Id private UUID id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "submission_id", nullable = false)
  private Submission submission;

  @NotNull
  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private ClaimStatus status;

  private String scheduleReference;

  @NotNull
  @Column(nullable = false)
  private Integer lineNumber;

  private String caseReferenceNumber;

  private String uniqueFileNumber;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
  private LocalDate caseStartDate;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
  private LocalDate caseConcludedDate;

  @NotNull
  @Column(nullable = false)
  private String matterTypeCode;

  private String crimeMatterTypeCode;

  private String feeSchemeCode;

  private String feeCode;

  private String procurementAreaCode;

  private String accessPointCode;

  private String deliveryLocation;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
  private LocalDate representationOrderDate;

  private Integer suspectsDefendantsCount;

  private Integer policeStationCourtAttendancesCount;

  private String policeStationCourtPrisonId;

  private String dsccNumber;

  private String maatId;

  private String prisonLawPriorApprovalNumber;

  @Column(name = "is_duty_solicitor")
  private Boolean dutySolicitor;

  @Column(name = "is_youth_court")
  private Boolean youthCourt;

  private String schemeId;

  private Integer mediationSessionsCount;

  private Integer mediationTimeMinutes;

  private String outreachLocation;

  private String referralSource;

  private UUID matchedClaimId;

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
