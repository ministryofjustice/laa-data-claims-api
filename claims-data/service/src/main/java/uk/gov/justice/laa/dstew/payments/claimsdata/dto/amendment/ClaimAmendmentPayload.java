package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import org.openapitools.jackson.nullable.JsonNullable;

/**
 * Sparse amendment payload as submitted by the provider (via AaBC).
 *
 * <p>Each field is wrapped in {@link JsonNullable} so the three submission states can be
 * distinguished and preserved end to end:
 *
 * <ul>
 *   <li><b>omitted</b> &rarr; {@code JsonNullable.undefined()} (field not present, leave
 *       unchanged);
 *   <li><b>explicit null</b> &rarr; {@code JsonNullable.of(null)} (requested clear, validated
 *       later);
 *   <li><b>value</b> &rarr; {@code JsonNullable.of(value)} (requested change).
 * </ul>
 *
 * <p>{@link JsonInclude.Include#NON_ABSENT} ensures undefined (omitted) fields are dropped during
 * serialisation while an explicit null is written as {@code "field": null}. This shape is the basis
 * for the {@code requestPayload} JSONB column in the amendment history record.
 *
 * <p>Deserialisation is supported via {@code @Jacksonized}: Jackson populates the Lombok builder so
 * the {@code @Builder.Default} values apply, meaning an omitted field is restored as {@code
 * JsonNullable.undefined()} and an explicit null as {@code JsonNullable.of(null)}. The omitted,
 * explicit-null and value distinctions therefore survive a serialise/deserialise round trip.
 *
 * <p>Covers every provider-amendable field across the claim, client, claim-case and
 * claim-summary-fee records. This is an in-memory contract only; it performs no persistence and
 * makes no external calls.
 */
@Data
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public class ClaimAmendmentPayload {

  // ---------------------------------------------------------------------------
  // Claim fields
  // ---------------------------------------------------------------------------
  @Builder.Default private JsonNullable<String> scheduleReference = JsonNullable.undefined();
  @Builder.Default private JsonNullable<Integer> lineNumber = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> caseReferenceNumber = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> uniqueFileNumber = JsonNullable.undefined();
  @Builder.Default private JsonNullable<LocalDate> caseStartDate = JsonNullable.undefined();
  @Builder.Default private JsonNullable<LocalDate> caseConcludedDate = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> matterTypeCode = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> crimeMatterTypeCode = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> feeSchemeCode = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> feeCode = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> procurementAreaCode = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> accessPointCode = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> deliveryLocation = JsonNullable.undefined();

  @Builder.Default
  private JsonNullable<LocalDate> representationOrderDate = JsonNullable.undefined();

  @Builder.Default private JsonNullable<Integer> suspectsDefendantsCount = JsonNullable.undefined();

  @Builder.Default
  private JsonNullable<Integer> policeStationCourtAttendancesCount = JsonNullable.undefined();

  @Builder.Default
  private JsonNullable<String> policeStationCourtPrisonId = JsonNullable.undefined();

  @Builder.Default private JsonNullable<String> dsccNumber = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> maatId = JsonNullable.undefined();

  @Builder.Default
  private JsonNullable<String> prisonLawPriorApprovalNumber = JsonNullable.undefined();

  @Builder.Default private JsonNullable<Boolean> isDutySolicitor = JsonNullable.undefined();
  @Builder.Default private JsonNullable<Boolean> isYouthCourt = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> schemeId = JsonNullable.undefined();
  @Builder.Default private JsonNullable<Integer> mediationSessionsCount = JsonNullable.undefined();
  @Builder.Default private JsonNullable<Integer> mediationTimeMinutes = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> outreachLocation = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> referralSource = JsonNullable.undefined();

  // ---------------------------------------------------------------------------
  // Client fields
  // ---------------------------------------------------------------------------
  @Builder.Default private JsonNullable<String> clientForename = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> clientSurname = JsonNullable.undefined();
  @Builder.Default private JsonNullable<LocalDate> clientDateOfBirth = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> uniqueClientNumber = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> clientPostcode = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> genderCode = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> ethnicityCode = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> disabilityCode = JsonNullable.undefined();
  @Builder.Default private JsonNullable<Boolean> isLegallyAided = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> clientTypeCode = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> homeOfficeClientNumber = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> claReferenceNumber = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> claExemptionCode = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> client2Forename = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> client2Surname = JsonNullable.undefined();
  @Builder.Default private JsonNullable<LocalDate> client2DateOfBirth = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> client2Ucn = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> client2Postcode = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> client2GenderCode = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> client2EthnicityCode = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> client2DisabilityCode = JsonNullable.undefined();
  @Builder.Default private JsonNullable<Boolean> client2IsLegallyAided = JsonNullable.undefined();

  // ---------------------------------------------------------------------------
  // Claim-case fields
  // ---------------------------------------------------------------------------
  @Builder.Default private JsonNullable<String> caseId = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> uniqueCaseId = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> caseStageCode = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> stageReachedCode = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> standardFeeCategoryCode = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> outcomeCode = JsonNullable.undefined();

  @Builder.Default
  private JsonNullable<String> designatedAccreditedRepresentativeCode = JsonNullable.undefined();

  @Builder.Default
  private JsonNullable<Boolean> isPostalApplicationAccepted = JsonNullable.undefined();

  @Builder.Default
  private JsonNullable<Boolean> isClient2PostalApplicationAccepted = JsonNullable.undefined();

  @Builder.Default
  private JsonNullable<String> mentalHealthTribunalReference = JsonNullable.undefined();

  @Builder.Default private JsonNullable<Boolean> isNrmAdvice = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> followOnWork = JsonNullable.undefined();
  @Builder.Default private JsonNullable<LocalDate> transferDate = JsonNullable.undefined();

  @Builder.Default
  private JsonNullable<String> exemptionCriteriaSatisfied = JsonNullable.undefined();

  @Builder.Default
  private JsonNullable<String> exceptionalCaseFundingReference = JsonNullable.undefined();

  @Builder.Default private JsonNullable<Boolean> isLegacyCase = JsonNullable.undefined();

  // ---------------------------------------------------------------------------
  // Claim-summary-fee fields (provider-entered fee inputs)
  // ---------------------------------------------------------------------------
  @Builder.Default private JsonNullable<Integer> adviceTime = JsonNullable.undefined();
  @Builder.Default private JsonNullable<Integer> travelTime = JsonNullable.undefined();
  @Builder.Default private JsonNullable<Integer> waitingTime = JsonNullable.undefined();
  @Builder.Default private JsonNullable<BigDecimal> netProfitCostsAmount = JsonNullable.undefined();

  @Builder.Default
  private JsonNullable<BigDecimal> netDisbursementAmount = JsonNullable.undefined();

  @Builder.Default
  private JsonNullable<BigDecimal> netCounselCostsAmount = JsonNullable.undefined();

  @Builder.Default
  private JsonNullable<BigDecimal> disbursementsVatAmount = JsonNullable.undefined();

  @Builder.Default
  private JsonNullable<BigDecimal> travelWaitingCostsAmount = JsonNullable.undefined();

  @Builder.Default
  private JsonNullable<BigDecimal> netWaitingCostsAmount = JsonNullable.undefined();

  @Builder.Default private JsonNullable<Boolean> isVatApplicable = JsonNullable.undefined();
  @Builder.Default private JsonNullable<Boolean> isToleranceApplicable = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> priorAuthorityReference = JsonNullable.undefined();
  @Builder.Default private JsonNullable<Boolean> isLondonRate = JsonNullable.undefined();

  @Builder.Default
  private JsonNullable<Integer> adjournedHearingFeeAmount = JsonNullable.undefined();

  @Builder.Default
  private JsonNullable<Boolean> isAdditionalTravelPayment = JsonNullable.undefined();

  @Builder.Default
  private JsonNullable<BigDecimal> costsDamagesRecoveredAmount = JsonNullable.undefined();

  @Builder.Default private JsonNullable<String> meetingsAttendedCode = JsonNullable.undefined();

  @Builder.Default
  private JsonNullable<BigDecimal> detentionTravelWaitingCostsAmount = JsonNullable.undefined();

  @Builder.Default private JsonNullable<BigDecimal> jrFormFillingAmount = JsonNullable.undefined();
  @Builder.Default private JsonNullable<Boolean> isEligibleClient = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> courtLocationCode = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> adviceTypeCode = JsonNullable.undefined();
  @Builder.Default private JsonNullable<Integer> medicalReportsCount = JsonNullable.undefined();
  @Builder.Default private JsonNullable<Boolean> isIrcSurgery = JsonNullable.undefined();
  @Builder.Default private JsonNullable<LocalDate> surgeryDate = JsonNullable.undefined();
  @Builder.Default private JsonNullable<Integer> surgeryClientsCount = JsonNullable.undefined();
  @Builder.Default private JsonNullable<Integer> surgeryMattersCount = JsonNullable.undefined();
  @Builder.Default private JsonNullable<Integer> cmrhOralCount = JsonNullable.undefined();
  @Builder.Default private JsonNullable<Integer> cmrhTelephoneCount = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> aitHearingCentreCode = JsonNullable.undefined();
  @Builder.Default private JsonNullable<Boolean> isSubstantiveHearing = JsonNullable.undefined();
  @Builder.Default private JsonNullable<Integer> hoInterview = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> localAuthorityNumber = JsonNullable.undefined();

  // ---------------------------------------------------------------------------
  // Amendment metadata (provider-submitted)
  // ---------------------------------------------------------------------------
  @Builder.Default private JsonNullable<String> amendmentRequestedBy = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> amendmentUserId = JsonNullable.undefined();
  @Builder.Default private JsonNullable<String> amendmentReasonCode = JsonNullable.undefined();
}
