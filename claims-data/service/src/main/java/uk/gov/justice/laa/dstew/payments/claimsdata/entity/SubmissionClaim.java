package uk.gov.justice.laa.dstew.payments.claimsdata.entity;



import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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

/**
 * Entity representing a claim linked to a submission.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "claim")
public class SubmissionClaim {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "submission_id", nullable = false)
  private Submission submission;

  @Column(nullable = false)
  private String status;

  @Column(name = "schedule_reference", nullable = false)
  private String scheduleReference;

  @Column(name = "line_number", nullable = false)
  private Integer lineNumber;

  @Column(name = "case_reference_number", nullable = false)
  private String caseReferenceNumber;

  @Column(name = "unique_file_number", nullable = false)
  private String uniqueFileNumber;

  @Column(name = "case_start_date", nullable = false)
  private LocalDate caseStartDate;

  @Column(name = "case_concluded_date", nullable = false)
  private LocalDate caseConcludedDate;

  @Column(name = "matter_type_code", nullable = false)
  private String matterTypeCode;

  @Column(name = "crime_matter_type_code")
  private String crimeMatterTypeCode;

  @Column(name = "fee_scheme_code")
  private String feeSchemeCode;

  @Column(name = "fee_code")
  private String feeCode;

  @Column(name = "procurement_area_code")
  private String procurementAreaCode;

  @Column(name = "access_point_code")
  private String accessPointCode;

  @Column(name = "delivery_location")
  private String deliveryLocation;

  @Column(name = "representation_order_date")
  private LocalDate representationOrderDate;

  @Column(name = "suspects_defendants_count")
  private Integer suspectsDefendantsCount;

  @Column(name = "police_station_court_attendances_count")
  private Integer policeStationCourtAttendancesCount;

  @Column(name = "police_station_court_prison_id")
  private String policeStationCourtPrisonId;

  @Column(name = "dscc_number")
  private String dsccNumber;

  @Column(name = "maat_id")
  private String maatId;

  @Column(name = "prison_law_prior_approval_number")
  private String prisonLawPriorApprovalNumber;

  @Column(name = "is_duty_solicitor")
  private Boolean dutySolicitor;

  @Column(name = "is_youth_court")
  private Boolean youthCourt;

  @Column(name = "scheme_id")
  private String schemeId;

  @Column(name = "mediation_sessions_count")
  private Integer mediationSessionsCount;

  @Column(name = "mediation_time_minutes")
  private Integer mediationTimeMinutes;

  @Column(name = "outreach_location")
  private String outreachLocation;

  @Column(name = "referral_source")
  private String referralSource;

  @Column(name = "matched_claim_id")
  private UUID matchedClaimId;

  @Column(name = "total_value")
  private java.math.BigDecimal totalValue;

  @Column(name = "created_by_user_id", nullable = false)
  private String createdByUserId;

  @CreationTimestamp
  @Column(name = "created_on", nullable = false)
  private Instant createdOn;

  @Column(name = "updated_by_user_id")
  private String updatedByUserId;

  @UpdateTimestamp
  @Column(name = "updated_on")
  private Instant updatedOn;
}

