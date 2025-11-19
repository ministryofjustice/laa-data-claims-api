package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentOutcome;

/** Entity representing an assessment linked to a claim. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "assessment")
public class Assessment {

  @NotNull
  @Column(nullable = false)
  @Id
  private UUID id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "claim_id", nullable = false)
  private Claim claim;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "claim_summary_fee_id", nullable = false)
  private ClaimSummaryFee claimSummaryFee;

  @NotNull
  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private AssessmentOutcome assessmentOutcome;

  private BigDecimal fixedFeeAmount;

  private BigDecimal netTravelCostsAmount;

  private BigDecimal netWaitingCostsAmount;

  private BigDecimal netProfitCostsAmount;

  private BigDecimal disbursementAmount;

  private BigDecimal disbursementVatAmount;

  private BigDecimal netCostOfCounselAmount;

  private BigDecimal travelWaitingCostsAmount;

  private BigDecimal travelAndWaitingCostsAmount;

  private Boolean isVatApplicable;

  private Integer adjournedHearingFeeAmount;

  private BigDecimal jrFormFillingAmount;

  private Integer cmrhOralCount;

  private Integer cmrhTelephoneCount;

  private Boolean isSubstantiveHearing;

  private Integer hoInterview;

  @NotNull
  @Column(nullable = false)
  private String createdByUserId;

  @CreationTimestamp
  @NotNull
  @Column(nullable = false)
  private Instant createdOn;
}
