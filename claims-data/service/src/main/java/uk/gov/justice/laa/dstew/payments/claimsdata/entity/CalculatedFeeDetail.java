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
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationType;

/** Entity representing the details of the claim fees calculated by the Fee Scheme Platform. */
@Getter
@Setter
@Entity
@Table(name = "calculated_fee_detail", schema = "claims")
public class CalculatedFeeDetail {

  @Id private UUID id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "claim_summary_fee_id", nullable = false)
  private ClaimSummaryFee claimSummaryFee;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "claim_id", nullable = false)
  private Claim claim;

  private String feeCode;

  @Enumerated(EnumType.STRING)
  private FeeCalculationType feeType;

  private String feeCodeDescription;

  private String categoryOfLaw;

  private String totalAmount;

  private Boolean vatIndicator;

  private BigDecimal vatRateApplied;

  private BigDecimal calculatedVatAmount;

  private BigDecimal disbursementAmount;

  private BigDecimal requestedNetDisbursementAmount;

  private BigDecimal disbursementVatAmount;

  private BigDecimal hourlyTotalAmount;

  private BigDecimal fixedFeeAmount;

  private BigDecimal netProfitCostsAmount;

  private BigDecimal requestedNetProfitCostsAmount;

  private BigDecimal netCostOfCounselAmount;

  private BigDecimal netTravelCostsAmount;

  private BigDecimal netWaitingCostsAmount;

  private BigDecimal detentionAndWaitingCostsAmount;

  private BigDecimal jrFormFillingAmount;

  private BigDecimal travelAndWaitingCostsAmount;

  private BigDecimal boltOnTotalFeeAmount;

  private Integer boltOnAdjournedHearingCount;

  private BigDecimal boltOnAdjournedHearingFee;

  private Integer boltOnCmrhTelephoneCount;

  private BigDecimal boltOnCmrhTelephoneFee;

  private Integer boltOnCmrhOralCount;

  private BigDecimal boltOnCmrhOralFee;

  private Integer boltOnHomeOfficeInterviewCount;

  private BigDecimal boltOnHomeOfficeInterviewFee;

  private Boolean escapeCaseFlag;

  private String schemeId;

  @Column(nullable = false)
  private String createdByUserId;

  @CreationTimestamp
  @Column(nullable = false)
  private OffsetDateTime createdOn;

  private String updatedByUserId;

  @UpdateTimestamp private OffsetDateTime updatedOn;
}
