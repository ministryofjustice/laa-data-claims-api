package uk.gov.justice.laa.dstew.payments.claimsdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.javers.core.metamodel.annotation.DiffIgnore;

/** Entity representing the summary of the claim fee. */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "claim_summary_fee", schema = "claims")
public class ClaimSummaryFee {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "claim_id", nullable = false)
  private Claim claim;

  private Integer adviceTime;

  private Integer travelTime;

  private Integer waitingTime;

  private BigDecimal netProfitCostsAmount;

  private BigDecimal netDisbursementAmount;

  private BigDecimal netCounselCostsAmount;

  private BigDecimal disbursementsVatAmount;

  private BigDecimal travelWaitingCostsAmount;

  private BigDecimal netWaitingCostsAmount;

  private Boolean isVatApplicable;

  private Boolean isToleranceApplicable;

  private String priorAuthorityReference;

  private Boolean isLondonRate;

  private Integer adjournedHearingFeeAmount;

  private Boolean isAdditionalTravelPayment;

  private BigDecimal costsDamagesRecoveredAmount;

  private String meetingsAttendedCode;

  private BigDecimal detentionTravelWaitingCostsAmount;

  private BigDecimal jrFormFillingAmount;

  private Boolean isEligibleClient;

  private String courtLocationCode;

  private String adviceTypeCode;

  private Integer medicalReportsCount;

  private Boolean isIrcSurgery;

  private LocalDate surgeryDate;

  private Integer surgeryClientsCount;

  private Integer surgeryMattersCount;

  private Integer cmrhOralCount;

  private Integer cmrhTelephoneCount;

  private String aitHearingCentreCode;

  private Boolean isSubstantiveHearing;

  private Integer hoInterview;

  private String localAuthorityNumber;

  @Column(nullable = false)
  private String createdByUserId;

  @DiffIgnore
  @CreationTimestamp
  @Column(nullable = false)
  private OffsetDateTime createdOn;

  @DiffIgnore private String updatedByUserId;

  @DiffIgnore @UpdateTimestamp private OffsetDateTime updatedOn;
}
