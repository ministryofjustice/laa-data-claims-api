package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

/**
 * Immutable, fully-resolved snapshot of the current stored claim state at the point an amendment is
 * submitted.
 *
 * <p>Unlike {@link ClaimAmendmentPayload}, every field carries a concrete value (no tri-state) - it
 * captures the "before" picture used by downstream validation, amendment history and diff
 * construction. This is the basis for the {@code beforeState} JSONB column.
 *
 * <p>It holds the before-state of every provider-amendable field plus read-only identity,
 * submission context and FSP/assessment state needed by downstream validation. This is an in-memory
 * contract only; it performs no persistence and makes no external calls.
 */
@Value
@Builder(toBuilder = true)
public class ClaimStateSnapshot {

  // ---------------------------------------------------------------------------
  // Claim identity and lifecycle (read-only)
  // ---------------------------------------------------------------------------
  UUID claimId;
  UUID submissionId;
  Long version;

  /** Claim lifecycle status. Read-only context - not a provider-amendable value. */
  ClaimStatus status;

  /**
   * Assessed-state indicator. The Lombok-generated getter is suppressed so the accessor reads
   * naturally as {@link #hasAssessment()} rather than {@code isHasAssessment()}.
   * {@code @JsonProperty} keeps the serialised property name stable for the {@code beforeState}
   * JSONB.
   */
  @Getter(AccessLevel.NONE)
  @JsonProperty("hasAssessment")
  boolean hasAssessment;

  boolean amended;

  // ---------------------------------------------------------------------------
  // Submission context (read-only)
  // ---------------------------------------------------------------------------
  AreaOfLaw areaOfLaw;
  String officeAccountNumber;
  String submissionPeriod;

  // ---------------------------------------------------------------------------
  // Claim fields (provider-amendable, before values)
  // ---------------------------------------------------------------------------
  String scheduleReference;
  Integer lineNumber;
  String caseReferenceNumber;
  String uniqueFileNumber;
  LocalDate caseStartDate;
  LocalDate caseConcludedDate;
  String matterTypeCode;
  String crimeMatterTypeCode;
  String feeSchemeCode;
  String feeCode;
  String procurementAreaCode;
  String accessPointCode;
  String deliveryLocation;
  LocalDate representationOrderDate;
  Integer suspectsDefendantsCount;
  Integer policeStationCourtAttendancesCount;
  String policeStationCourtPrisonId;
  String dsccNumber;
  String maatId;
  String prisonLawPriorApprovalNumber;
  Boolean dutySolicitor;
  Boolean youthCourt;
  String schemeId;
  Integer mediationSessionsCount;
  Integer mediationTimeMinutes;
  String outreachLocation;
  String referralSource;

  // ---------------------------------------------------------------------------
  // Client fields (provider-amendable, before values)
  // ---------------------------------------------------------------------------
  String clientForename;
  String clientSurname;
  LocalDate clientDateOfBirth;
  String uniqueClientNumber;
  String clientPostcode;
  String genderCode;
  String ethnicityCode;
  String disabilityCode;
  Boolean isLegallyAided;
  String clientTypeCode;
  String homeOfficeClientNumber;
  String claReferenceNumber;
  String claExemptionCode;
  String client2Forename;
  String client2Surname;
  LocalDate client2DateOfBirth;
  String client2Ucn;
  String client2Postcode;
  String client2GenderCode;
  String client2EthnicityCode;
  String client2DisabilityCode;
  Boolean client2IsLegallyAided;

  // ---------------------------------------------------------------------------
  // Claim-case fields (provider-amendable, before values)
  // ---------------------------------------------------------------------------
  String caseId;
  String uniqueCaseId;
  String caseStageCode;
  String stageReachedCode;
  String standardFeeCategoryCode;
  String outcomeCode;
  String designatedAccreditedRepresentativeCode;
  Boolean isPostalApplicationAccepted;
  Boolean isClient2PostalApplicationAccepted;
  String mentalHealthTribunalReference;
  Boolean isNrmAdvice;
  String followOnWork;
  LocalDate transferDate;
  String exemptionCriteriaSatisfied;
  String exceptionalCaseFundingReference;
  Boolean isLegacyCase;

  // ---------------------------------------------------------------------------
  // Claim-summary-fee fields (provider-amendable, before values)
  // ---------------------------------------------------------------------------
  Integer adviceTime;
  Integer travelTime;
  Integer waitingTime;
  BigDecimal netProfitCostsAmount;
  BigDecimal netDisbursementAmount;
  BigDecimal netCounselCostsAmount;
  BigDecimal disbursementsVatAmount;
  BigDecimal travelWaitingCostsAmount;
  BigDecimal netWaitingCostsAmount;
  Boolean isVatApplicable;
  Boolean isToleranceApplicable;
  String priorAuthorityReference;
  Boolean isLondonRate;
  Integer adjournedHearingFeeAmount;
  Boolean isAdditionalTravelPayment;
  BigDecimal costsDamagesRecoveredAmount;
  String meetingsAttendedCode;
  BigDecimal detentionTravelWaitingCostsAmount;
  BigDecimal jrFormFillingAmount;
  Boolean isEligibleClient;
  String courtLocationCode;
  String adviceTypeCode;
  Integer medicalReportsCount;
  Boolean isIrcSurgery;
  LocalDate surgeryDate;
  Integer surgeryClientsCount;
  Integer surgeryMattersCount;
  Integer cmrhOralCount;
  Integer cmrhTelephoneCount;
  String aitHearingCentreCode;
  Boolean isSubstantiveHearing;
  Integer hoInterview;
  String localAuthorityNumber;

  // ---------------------------------------------------------------------------
  // Read-only FSP / assessment context
  // ---------------------------------------------------------------------------
  String categoryOfLaw;
  CalculatedFeeDetailSnapshot calculatedFeeDetail;
  AssessmentSnapshot latestAssessment;

  /**
   * Whether the claim has an assessment (assessed-state indicator).
   *
   * @return {@code true} if the claim is assessed
   */
  public boolean hasAssessment() {
    return hasAssessment;
  }
}
