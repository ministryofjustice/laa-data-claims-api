package uk.gov.justice.laa.dstew.payments.claimsdata.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonValue;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.lang.Nullable;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;
import java.util.NoSuchElementException;
import org.openapitools.jackson.nullable.JsonNullable;
import java.io.Serializable;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * Amendment PATCH representation of a claim. This is the SAME shape as claim_patch (it is derived from the shared ./schemas/claim-patch.yml#/claim_patch via allOf, so there is a single source of truth for the field list and no risk of drift), but because this spec is generated with openApiNullable &#x3D; true every nullable field becomes a JsonNullable&lt;T&gt;. That lets the amendment pipeline distinguish an omitted field from an explicit null while keeping the JSON wire format identical to claim_patch. 
 */

@Schema(name = "claim_amendment_patch", description = "Amendment PATCH representation of a claim. This is the SAME shape as claim_patch (it is derived from the shared ./schemas/claim-patch.yml#/claim_patch via allOf, so there is a single source of truth for the field list and no risk of drift), but because this spec is generated with openApiNullable = true every nullable field becomes a JsonNullable<T>. That lets the amendment pipeline distinguish an omitted field from an explicit null while keeping the JSON wire format identical to claim_patch. ")
@JsonTypeName("claim_amendment_patch")
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2026-08-03T14:46:04.587400+01:00[Europe/London]", comments = "Generator version: 7.23.0")
public class ClaimAmendmentPatch implements Serializable {

  private static final long serialVersionUID = 1L;

  private JsonNullable<String> id = JsonNullable.<String>undefined();

  private JsonNullable<String> submissionId = JsonNullable.<String>undefined();

  private @Nullable ClaimStatus status;

  private JsonNullable<String> scheduleReference = JsonNullable.<String>undefined();

  private JsonNullable<Integer> lineNumber = JsonNullable.<Integer>undefined();

  private JsonNullable<String> caseReferenceNumber = JsonNullable.<String>undefined();

  private JsonNullable<String> uniqueFileNumber = JsonNullable.<String>undefined();

  private JsonNullable<String> caseStartDate = JsonNullable.<String>undefined();

  private JsonNullable<String> caseConcludedDate = JsonNullable.<String>undefined();

  private JsonNullable<String> matterTypeCode = JsonNullable.<String>undefined();

  private JsonNullable<String> crimeMatterTypeCode = JsonNullable.<String>undefined();

  private JsonNullable<String> feeSchemeCode = JsonNullable.<String>undefined();

  private JsonNullable<String> feeCode = JsonNullable.<String>undefined();

  private JsonNullable<String> procurementAreaCode = JsonNullable.<String>undefined();

  private JsonNullable<String> accessPointCode = JsonNullable.<String>undefined();

  private JsonNullable<String> deliveryLocation = JsonNullable.<String>undefined();

  private JsonNullable<String> representationOrderDate = JsonNullable.<String>undefined();

  private JsonNullable<Integer> suspectsDefendantsCount = JsonNullable.<Integer>undefined();

  private JsonNullable<Integer> policeStationCourtAttendancesCount = JsonNullable.<Integer>undefined();

  private JsonNullable<String> policeStationCourtPrisonId = JsonNullable.<String>undefined();

  private JsonNullable<String> dsccNumber = JsonNullable.<String>undefined();

  private JsonNullable<String> maatId = JsonNullable.<String>undefined();

  private JsonNullable<String> prisonLawPriorApprovalNumber = JsonNullable.<String>undefined();

  private JsonNullable<Boolean> isDutySolicitor = JsonNullable.<Boolean>undefined();

  private JsonNullable<Boolean> isYouthCourt = JsonNullable.<Boolean>undefined();

  private JsonNullable<String> schemeId = JsonNullable.<String>undefined();

  private JsonNullable<Integer> mediationSessionsCount = JsonNullable.<Integer>undefined();

  private JsonNullable<Integer> mediationTimeMinutes = JsonNullable.<Integer>undefined();

  private JsonNullable<String> outreachLocation = JsonNullable.<String>undefined();

  private JsonNullable<String> referralSource = JsonNullable.<String>undefined();

  private JsonNullable<String> clientForename = JsonNullable.<String>undefined();

  private JsonNullable<String> clientSurname = JsonNullable.<String>undefined();

  private JsonNullable<String> clientDateOfBirth = JsonNullable.<String>undefined();

  private JsonNullable<String> uniqueClientNumber = JsonNullable.<String>undefined();

  private JsonNullable<String> clientPostcode = JsonNullable.<String>undefined();

  private JsonNullable<String> genderCode = JsonNullable.<String>undefined();

  private JsonNullable<String> ethnicityCode = JsonNullable.<String>undefined();

  private JsonNullable<String> disabilityCode = JsonNullable.<String>undefined();

  private JsonNullable<Boolean> isLegallyAided = JsonNullable.<Boolean>undefined();

  private JsonNullable<String> clientTypeCode = JsonNullable.<String>undefined();

  private JsonNullable<String> homeOfficeClientNumber = JsonNullable.<String>undefined();

  private JsonNullable<String> claReferenceNumber = JsonNullable.<String>undefined();

  private JsonNullable<String> claExemptionCode = JsonNullable.<String>undefined();

  private JsonNullable<String> client2Forename = JsonNullable.<String>undefined();

  private JsonNullable<String> client2Surname = JsonNullable.<String>undefined();

  private JsonNullable<String> client2DateOfBirth = JsonNullable.<String>undefined();

  private JsonNullable<String> client2Ucn = JsonNullable.<String>undefined();

  private JsonNullable<String> client2Postcode = JsonNullable.<String>undefined();

  private JsonNullable<String> client2GenderCode = JsonNullable.<String>undefined();

  private JsonNullable<String> client2EthnicityCode = JsonNullable.<String>undefined();

  private JsonNullable<String> client2DisabilityCode = JsonNullable.<String>undefined();

  private JsonNullable<Boolean> client2IsLegallyAided = JsonNullable.<Boolean>undefined();

  private JsonNullable<String> caseId = JsonNullable.<String>undefined();

  private JsonNullable<String> uniqueCaseId = JsonNullable.<String>undefined();

  private JsonNullable<String> caseStageCode = JsonNullable.<String>undefined();

  private JsonNullable<String> stageReachedCode = JsonNullable.<String>undefined();

  private JsonNullable<String> standardFeeCategoryCode = JsonNullable.<String>undefined();

  private JsonNullable<String> outcomeCode = JsonNullable.<String>undefined();

  private JsonNullable<String> designatedAccreditedRepresentativeCode = JsonNullable.<String>undefined();

  private JsonNullable<Boolean> isPostalApplicationAccepted = JsonNullable.<Boolean>undefined();

  private JsonNullable<Boolean> isClient2PostalApplicationAccepted = JsonNullable.<Boolean>undefined();

  private JsonNullable<String> mentalHealthTribunalReference = JsonNullable.<String>undefined();

  private JsonNullable<Boolean> isNrmAdvice = JsonNullable.<Boolean>undefined();

  private JsonNullable<String> followOnWork = JsonNullable.<String>undefined();

  private JsonNullable<String> transferDate = JsonNullable.<String>undefined();

  private JsonNullable<String> exemptionCriteriaSatisfied = JsonNullable.<String>undefined();

  private JsonNullable<String> exceptionalCaseFundingReference = JsonNullable.<String>undefined();

  private JsonNullable<Boolean> isLegacyCase = JsonNullable.<Boolean>undefined();

  private JsonNullable<Integer> adviceTime = JsonNullable.<Integer>undefined();

  private JsonNullable<Integer> travelTime = JsonNullable.<Integer>undefined();

  private JsonNullable<Integer> waitingTime = JsonNullable.<Integer>undefined();

  private JsonNullable<BigDecimal> netProfitCostsAmount = JsonNullable.<BigDecimal>undefined();

  private JsonNullable<BigDecimal> netDisbursementAmount = JsonNullable.<BigDecimal>undefined();

  private JsonNullable<BigDecimal> netCounselCostsAmount = JsonNullable.<BigDecimal>undefined();

  private JsonNullable<BigDecimal> disbursementsVatAmount = JsonNullable.<BigDecimal>undefined();

  private JsonNullable<BigDecimal> travelWaitingCostsAmount = JsonNullable.<BigDecimal>undefined();

  private JsonNullable<BigDecimal> netWaitingCostsAmount = JsonNullable.<BigDecimal>undefined();

  private JsonNullable<Boolean> isVatApplicable = JsonNullable.<Boolean>undefined();

  private JsonNullable<Boolean> isToleranceApplicable = JsonNullable.<Boolean>undefined();

  private JsonNullable<String> priorAuthorityReference = JsonNullable.<String>undefined();

  private JsonNullable<Boolean> isLondonRate = JsonNullable.<Boolean>undefined();

  private JsonNullable<Integer> adjournedHearingFeeAmount = JsonNullable.<Integer>undefined();

  private JsonNullable<Boolean> isAdditionalTravelPayment = JsonNullable.<Boolean>undefined();

  private JsonNullable<BigDecimal> costsDamagesRecoveredAmount = JsonNullable.<BigDecimal>undefined();

  private JsonNullable<String> meetingsAttendedCode = JsonNullable.<String>undefined();

  private JsonNullable<BigDecimal> detentionTravelWaitingCostsAmount = JsonNullable.<BigDecimal>undefined();

  private JsonNullable<BigDecimal> jrFormFillingAmount = JsonNullable.<BigDecimal>undefined();

  private JsonNullable<Boolean> isEligibleClient = JsonNullable.<Boolean>undefined();

  private JsonNullable<String> courtLocationCode = JsonNullable.<String>undefined();

  private JsonNullable<String> adviceTypeCode = JsonNullable.<String>undefined();

  private JsonNullable<Integer> medicalReportsCount = JsonNullable.<Integer>undefined();

  private JsonNullable<Boolean> isIrcSurgery = JsonNullable.<Boolean>undefined();

  private JsonNullable<String> surgeryDate = JsonNullable.<String>undefined();

  private JsonNullable<Integer> surgeryClientsCount = JsonNullable.<Integer>undefined();

  private JsonNullable<Integer> surgeryMattersCount = JsonNullable.<Integer>undefined();

  private JsonNullable<Integer> cmrhOralCount = JsonNullable.<Integer>undefined();

  private JsonNullable<Integer> cmrhTelephoneCount = JsonNullable.<Integer>undefined();

  private JsonNullable<String> aitHearingCentreCode = JsonNullable.<String>undefined();

  private JsonNullable<Boolean> isSubstantiveHearing = JsonNullable.<Boolean>undefined();

  private JsonNullable<Integer> hoInterview = JsonNullable.<Integer>undefined();

  private JsonNullable<String> localAuthorityNumber = JsonNullable.<String>undefined();

  private JsonNullable<String> submissionPeriod = JsonNullable.<String>undefined();

  private JsonNullable<String> createdByUserId = JsonNullable.<String>undefined();

  private JsonNullable<Boolean> isAmended = JsonNullable.<Boolean>undefined();

  private JsonNullable<Boolean> hasAssessment = JsonNullable.<Boolean>undefined();

  private JsonNullable<Long> version = JsonNullable.<Long>undefined();

  private @Nullable Integer totalWarnings;

  private @Nullable FeeCalculationPatch feeCalculationResponse;

  private JsonNullable<String> amendmentRequestedBy = JsonNullable.<String>undefined();

  private JsonNullable<UUID> amendmentUserId = JsonNullable.<UUID>undefined();

  private JsonNullable<String> amendmentReasonCode = JsonNullable.<String>undefined();

  private List<@Valid ValidationMessagePatch> validationMessages = new ArrayList<>();

  public ClaimAmendmentPatch id(String id) {
    this.id = JsonNullable.of(id);
    return this;
  }

  /**
   * Get id
   * @return id
   */
  
  @Schema(name = "id", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("id")
  public JsonNullable<String> getId() {
    return id;
  }

  public void setId(JsonNullable<String> id) {
    this.id = id;
  }

  public ClaimAmendmentPatch submissionId(String submissionId) {
    this.submissionId = JsonNullable.of(submissionId);
    return this;
  }

  /**
   * Get submissionId
   * @return submissionId
   */
  
  @Schema(name = "submission_id", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("submission_id")
  public JsonNullable<String> getSubmissionId() {
    return submissionId;
  }

  public void setSubmissionId(JsonNullable<String> submissionId) {
    this.submissionId = submissionId;
  }

  public ClaimAmendmentPatch status(@Nullable ClaimStatus status) {
    this.status = status;
    return this;
  }

  /**
   * Get status
   * @return status
   */
  @Valid 
  @Schema(name = "status", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("status")
  public @Nullable ClaimStatus getStatus() {
    return status;
  }

  @JsonProperty("status")
  public void setStatus(@Nullable ClaimStatus status) {
    this.status = status;
  }

  public ClaimAmendmentPatch scheduleReference(String scheduleReference) {
    this.scheduleReference = JsonNullable.of(scheduleReference);
    return this;
  }

  /**
   * Get scheduleReference
   * @return scheduleReference
   */
  
  @Schema(name = "schedule_reference", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("schedule_reference")
  public JsonNullable<String> getScheduleReference() {
    return scheduleReference;
  }

  public void setScheduleReference(JsonNullable<String> scheduleReference) {
    this.scheduleReference = scheduleReference;
  }

  public ClaimAmendmentPatch lineNumber(Integer lineNumber) {
    this.lineNumber = JsonNullable.of(lineNumber);
    return this;
  }

  /**
   * Get lineNumber
   * @return lineNumber
   */
  
  @Schema(name = "line_number", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("line_number")
  public JsonNullable<Integer> getLineNumber() {
    return lineNumber;
  }

  public void setLineNumber(JsonNullable<Integer> lineNumber) {
    this.lineNumber = lineNumber;
  }

  public ClaimAmendmentPatch caseReferenceNumber(String caseReferenceNumber) {
    this.caseReferenceNumber = JsonNullable.of(caseReferenceNumber);
    return this;
  }

  /**
   * Get caseReferenceNumber
   * @return caseReferenceNumber
   */
  
  @Schema(name = "case_reference_number", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("case_reference_number")
  public JsonNullable<String> getCaseReferenceNumber() {
    return caseReferenceNumber;
  }

  public void setCaseReferenceNumber(JsonNullable<String> caseReferenceNumber) {
    this.caseReferenceNumber = caseReferenceNumber;
  }

  public ClaimAmendmentPatch uniqueFileNumber(String uniqueFileNumber) {
    this.uniqueFileNumber = JsonNullable.of(uniqueFileNumber);
    return this;
  }

  /**
   * Get uniqueFileNumber
   * @return uniqueFileNumber
   */
  
  @Schema(name = "unique_file_number", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("unique_file_number")
  public JsonNullable<String> getUniqueFileNumber() {
    return uniqueFileNumber;
  }

  public void setUniqueFileNumber(JsonNullable<String> uniqueFileNumber) {
    this.uniqueFileNumber = uniqueFileNumber;
  }

  public ClaimAmendmentPatch caseStartDate(String caseStartDate) {
    this.caseStartDate = JsonNullable.of(caseStartDate);
    return this;
  }

  /**
   * Date the case was started (format DD/MM/YYYY)
   * @return caseStartDate
   */
  
  @Schema(name = "case_start_date", description = "Date the case was started (format DD/MM/YYYY)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("case_start_date")
  public JsonNullable<String> getCaseStartDate() {
    return caseStartDate;
  }

  public void setCaseStartDate(JsonNullable<String> caseStartDate) {
    this.caseStartDate = caseStartDate;
  }

  public ClaimAmendmentPatch caseConcludedDate(String caseConcludedDate) {
    this.caseConcludedDate = JsonNullable.of(caseConcludedDate);
    return this;
  }

  /**
   * Date the case was concluded (format DD/MM/YYYY)
   * @return caseConcludedDate
   */
  
  @Schema(name = "case_concluded_date", description = "Date the case was concluded (format DD/MM/YYYY)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("case_concluded_date")
  public JsonNullable<String> getCaseConcludedDate() {
    return caseConcludedDate;
  }

  public void setCaseConcludedDate(JsonNullable<String> caseConcludedDate) {
    this.caseConcludedDate = caseConcludedDate;
  }

  public ClaimAmendmentPatch matterTypeCode(String matterTypeCode) {
    this.matterTypeCode = JsonNullable.of(matterTypeCode);
    return this;
  }

  /**
   * Get matterTypeCode
   * @return matterTypeCode
   */
  
  @Schema(name = "matter_type_code", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("matter_type_code")
  public JsonNullable<String> getMatterTypeCode() {
    return matterTypeCode;
  }

  public void setMatterTypeCode(JsonNullable<String> matterTypeCode) {
    this.matterTypeCode = matterTypeCode;
  }

  public ClaimAmendmentPatch crimeMatterTypeCode(String crimeMatterTypeCode) {
    this.crimeMatterTypeCode = JsonNullable.of(crimeMatterTypeCode);
    return this;
  }

  /**
   * Get crimeMatterTypeCode
   * @return crimeMatterTypeCode
   */
  
  @Schema(name = "crime_matter_type_code", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("crime_matter_type_code")
  public JsonNullable<String> getCrimeMatterTypeCode() {
    return crimeMatterTypeCode;
  }

  public void setCrimeMatterTypeCode(JsonNullable<String> crimeMatterTypeCode) {
    this.crimeMatterTypeCode = crimeMatterTypeCode;
  }

  public ClaimAmendmentPatch feeSchemeCode(String feeSchemeCode) {
    this.feeSchemeCode = JsonNullable.of(feeSchemeCode);
    return this;
  }

  /**
   * Get feeSchemeCode
   * @return feeSchemeCode
   */
  
  @Schema(name = "fee_scheme_code", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("fee_scheme_code")
  public JsonNullable<String> getFeeSchemeCode() {
    return feeSchemeCode;
  }

  public void setFeeSchemeCode(JsonNullable<String> feeSchemeCode) {
    this.feeSchemeCode = feeSchemeCode;
  }

  public ClaimAmendmentPatch feeCode(String feeCode) {
    this.feeCode = JsonNullable.of(feeCode);
    return this;
  }

  /**
   * Get feeCode
   * @return feeCode
   */
  
  @Schema(name = "fee_code", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("fee_code")
  public JsonNullable<String> getFeeCode() {
    return feeCode;
  }

  public void setFeeCode(JsonNullable<String> feeCode) {
    this.feeCode = feeCode;
  }

  public ClaimAmendmentPatch procurementAreaCode(String procurementAreaCode) {
    this.procurementAreaCode = JsonNullable.of(procurementAreaCode);
    return this;
  }

  /**
   * Get procurementAreaCode
   * @return procurementAreaCode
   */
  
  @Schema(name = "procurement_area_code", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("procurement_area_code")
  public JsonNullable<String> getProcurementAreaCode() {
    return procurementAreaCode;
  }

  public void setProcurementAreaCode(JsonNullable<String> procurementAreaCode) {
    this.procurementAreaCode = procurementAreaCode;
  }

  public ClaimAmendmentPatch accessPointCode(String accessPointCode) {
    this.accessPointCode = JsonNullable.of(accessPointCode);
    return this;
  }

  /**
   * Get accessPointCode
   * @return accessPointCode
   */
  
  @Schema(name = "access_point_code", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("access_point_code")
  public JsonNullable<String> getAccessPointCode() {
    return accessPointCode;
  }

  public void setAccessPointCode(JsonNullable<String> accessPointCode) {
    this.accessPointCode = accessPointCode;
  }

  public ClaimAmendmentPatch deliveryLocation(String deliveryLocation) {
    this.deliveryLocation = JsonNullable.of(deliveryLocation);
    return this;
  }

  /**
   * Get deliveryLocation
   * @return deliveryLocation
   */
  
  @Schema(name = "delivery_location", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("delivery_location")
  public JsonNullable<String> getDeliveryLocation() {
    return deliveryLocation;
  }

  public void setDeliveryLocation(JsonNullable<String> deliveryLocation) {
    this.deliveryLocation = deliveryLocation;
  }

  public ClaimAmendmentPatch representationOrderDate(String representationOrderDate) {
    this.representationOrderDate = JsonNullable.of(representationOrderDate);
    return this;
  }

  /**
   * Date the rep order was created (format DD/MM/YYYY)
   * @return representationOrderDate
   */
  
  @Schema(name = "representation_order_date", description = "Date the rep order was created (format DD/MM/YYYY)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("representation_order_date")
  public JsonNullable<String> getRepresentationOrderDate() {
    return representationOrderDate;
  }

  public void setRepresentationOrderDate(JsonNullable<String> representationOrderDate) {
    this.representationOrderDate = representationOrderDate;
  }

  public ClaimAmendmentPatch suspectsDefendantsCount(Integer suspectsDefendantsCount) {
    this.suspectsDefendantsCount = JsonNullable.of(suspectsDefendantsCount);
    return this;
  }

  /**
   * Get suspectsDefendantsCount
   * @return suspectsDefendantsCount
   */
  
  @Schema(name = "suspects_defendants_count", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("suspects_defendants_count")
  public JsonNullable<Integer> getSuspectsDefendantsCount() {
    return suspectsDefendantsCount;
  }

  public void setSuspectsDefendantsCount(JsonNullable<Integer> suspectsDefendantsCount) {
    this.suspectsDefendantsCount = suspectsDefendantsCount;
  }

  public ClaimAmendmentPatch policeStationCourtAttendancesCount(Integer policeStationCourtAttendancesCount) {
    this.policeStationCourtAttendancesCount = JsonNullable.of(policeStationCourtAttendancesCount);
    return this;
  }

  /**
   * Get policeStationCourtAttendancesCount
   * @return policeStationCourtAttendancesCount
   */
  
  @Schema(name = "police_station_court_attendances_count", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("police_station_court_attendances_count")
  public JsonNullable<Integer> getPoliceStationCourtAttendancesCount() {
    return policeStationCourtAttendancesCount;
  }

  public void setPoliceStationCourtAttendancesCount(JsonNullable<Integer> policeStationCourtAttendancesCount) {
    this.policeStationCourtAttendancesCount = policeStationCourtAttendancesCount;
  }

  public ClaimAmendmentPatch policeStationCourtPrisonId(String policeStationCourtPrisonId) {
    this.policeStationCourtPrisonId = JsonNullable.of(policeStationCourtPrisonId);
    return this;
  }

  /**
   * Get policeStationCourtPrisonId
   * @return policeStationCourtPrisonId
   */
  
  @Schema(name = "police_station_court_prison_id", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("police_station_court_prison_id")
  public JsonNullable<String> getPoliceStationCourtPrisonId() {
    return policeStationCourtPrisonId;
  }

  public void setPoliceStationCourtPrisonId(JsonNullable<String> policeStationCourtPrisonId) {
    this.policeStationCourtPrisonId = policeStationCourtPrisonId;
  }

  public ClaimAmendmentPatch dsccNumber(String dsccNumber) {
    this.dsccNumber = JsonNullable.of(dsccNumber);
    return this;
  }

  /**
   * Get dsccNumber
   * @return dsccNumber
   */
  
  @Schema(name = "dscc_number", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("dscc_number")
  public JsonNullable<String> getDsccNumber() {
    return dsccNumber;
  }

  public void setDsccNumber(JsonNullable<String> dsccNumber) {
    this.dsccNumber = dsccNumber;
  }

  public ClaimAmendmentPatch maatId(String maatId) {
    this.maatId = JsonNullable.of(maatId);
    return this;
  }

  /**
   * Get maatId
   * @return maatId
   */
  
  @Schema(name = "maat_id", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("maat_id")
  public JsonNullable<String> getMaatId() {
    return maatId;
  }

  public void setMaatId(JsonNullable<String> maatId) {
    this.maatId = maatId;
  }

  public ClaimAmendmentPatch prisonLawPriorApprovalNumber(String prisonLawPriorApprovalNumber) {
    this.prisonLawPriorApprovalNumber = JsonNullable.of(prisonLawPriorApprovalNumber);
    return this;
  }

  /**
   * Get prisonLawPriorApprovalNumber
   * @return prisonLawPriorApprovalNumber
   */
  
  @Schema(name = "prison_law_prior_approval_number", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("prison_law_prior_approval_number")
  public JsonNullable<String> getPrisonLawPriorApprovalNumber() {
    return prisonLawPriorApprovalNumber;
  }

  public void setPrisonLawPriorApprovalNumber(JsonNullable<String> prisonLawPriorApprovalNumber) {
    this.prisonLawPriorApprovalNumber = prisonLawPriorApprovalNumber;
  }

  public ClaimAmendmentPatch isDutySolicitor(Boolean isDutySolicitor) {
    this.isDutySolicitor = JsonNullable.of(isDutySolicitor);
    return this;
  }

  /**
   * Get isDutySolicitor
   * @return isDutySolicitor
   */
  
  @Schema(name = "is_duty_solicitor", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("is_duty_solicitor")
  public JsonNullable<Boolean> getIsDutySolicitor() {
    return isDutySolicitor;
  }

  public void setIsDutySolicitor(JsonNullable<Boolean> isDutySolicitor) {
    this.isDutySolicitor = isDutySolicitor;
  }

  public ClaimAmendmentPatch isYouthCourt(Boolean isYouthCourt) {
    this.isYouthCourt = JsonNullable.of(isYouthCourt);
    return this;
  }

  /**
   * Get isYouthCourt
   * @return isYouthCourt
   */
  
  @Schema(name = "is_youth_court", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("is_youth_court")
  public JsonNullable<Boolean> getIsYouthCourt() {
    return isYouthCourt;
  }

  public void setIsYouthCourt(JsonNullable<Boolean> isYouthCourt) {
    this.isYouthCourt = isYouthCourt;
  }

  public ClaimAmendmentPatch schemeId(String schemeId) {
    this.schemeId = JsonNullable.of(schemeId);
    return this;
  }

  /**
   * Get schemeId
   * @return schemeId
   */
  
  @Schema(name = "scheme_id", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("scheme_id")
  public JsonNullable<String> getSchemeId() {
    return schemeId;
  }

  public void setSchemeId(JsonNullable<String> schemeId) {
    this.schemeId = schemeId;
  }

  public ClaimAmendmentPatch mediationSessionsCount(Integer mediationSessionsCount) {
    this.mediationSessionsCount = JsonNullable.of(mediationSessionsCount);
    return this;
  }

  /**
   * Get mediationSessionsCount
   * @return mediationSessionsCount
   */
  
  @Schema(name = "mediation_sessions_count", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("mediation_sessions_count")
  public JsonNullable<Integer> getMediationSessionsCount() {
    return mediationSessionsCount;
  }

  public void setMediationSessionsCount(JsonNullable<Integer> mediationSessionsCount) {
    this.mediationSessionsCount = mediationSessionsCount;
  }

  public ClaimAmendmentPatch mediationTimeMinutes(Integer mediationTimeMinutes) {
    this.mediationTimeMinutes = JsonNullable.of(mediationTimeMinutes);
    return this;
  }

  /**
   * Get mediationTimeMinutes
   * @return mediationTimeMinutes
   */
  
  @Schema(name = "mediation_time_minutes", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("mediation_time_minutes")
  public JsonNullable<Integer> getMediationTimeMinutes() {
    return mediationTimeMinutes;
  }

  public void setMediationTimeMinutes(JsonNullable<Integer> mediationTimeMinutes) {
    this.mediationTimeMinutes = mediationTimeMinutes;
  }

  public ClaimAmendmentPatch outreachLocation(String outreachLocation) {
    this.outreachLocation = JsonNullable.of(outreachLocation);
    return this;
  }

  /**
   * Get outreachLocation
   * @return outreachLocation
   */
  
  @Schema(name = "outreach_location", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("outreach_location")
  public JsonNullable<String> getOutreachLocation() {
    return outreachLocation;
  }

  public void setOutreachLocation(JsonNullable<String> outreachLocation) {
    this.outreachLocation = outreachLocation;
  }

  public ClaimAmendmentPatch referralSource(String referralSource) {
    this.referralSource = JsonNullable.of(referralSource);
    return this;
  }

  /**
   * Get referralSource
   * @return referralSource
   */
  
  @Schema(name = "referral_source", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("referral_source")
  public JsonNullable<String> getReferralSource() {
    return referralSource;
  }

  public void setReferralSource(JsonNullable<String> referralSource) {
    this.referralSource = referralSource;
  }

  public ClaimAmendmentPatch clientForename(String clientForename) {
    this.clientForename = JsonNullable.of(clientForename);
    return this;
  }

  /**
   * Get clientForename
   * @return clientForename
   */
  
  @Schema(name = "client_forename", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("client_forename")
  public JsonNullable<String> getClientForename() {
    return clientForename;
  }

  public void setClientForename(JsonNullable<String> clientForename) {
    this.clientForename = clientForename;
  }

  public ClaimAmendmentPatch clientSurname(String clientSurname) {
    this.clientSurname = JsonNullable.of(clientSurname);
    return this;
  }

  /**
   * Get clientSurname
   * @return clientSurname
   */
  
  @Schema(name = "client_surname", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("client_surname")
  public JsonNullable<String> getClientSurname() {
    return clientSurname;
  }

  public void setClientSurname(JsonNullable<String> clientSurname) {
    this.clientSurname = clientSurname;
  }

  public ClaimAmendmentPatch clientDateOfBirth(String clientDateOfBirth) {
    this.clientDateOfBirth = JsonNullable.of(clientDateOfBirth);
    return this;
  }

  /**
   * Client's date of birth (format DD/MM/YYYY)
   * @return clientDateOfBirth
   */
  
  @Schema(name = "client_date_of_birth", description = "Client's date of birth (format DD/MM/YYYY)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("client_date_of_birth")
  public JsonNullable<String> getClientDateOfBirth() {
    return clientDateOfBirth;
  }

  public void setClientDateOfBirth(JsonNullable<String> clientDateOfBirth) {
    this.clientDateOfBirth = clientDateOfBirth;
  }

  public ClaimAmendmentPatch uniqueClientNumber(String uniqueClientNumber) {
    this.uniqueClientNumber = JsonNullable.of(uniqueClientNumber);
    return this;
  }

  /**
   * Get uniqueClientNumber
   * @return uniqueClientNumber
   */
  
  @Schema(name = "unique_client_number", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("unique_client_number")
  public JsonNullable<String> getUniqueClientNumber() {
    return uniqueClientNumber;
  }

  public void setUniqueClientNumber(JsonNullable<String> uniqueClientNumber) {
    this.uniqueClientNumber = uniqueClientNumber;
  }

  public ClaimAmendmentPatch clientPostcode(String clientPostcode) {
    this.clientPostcode = JsonNullable.of(clientPostcode);
    return this;
  }

  /**
   * Get clientPostcode
   * @return clientPostcode
   */
  
  @Schema(name = "client_postcode", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("client_postcode")
  public JsonNullable<String> getClientPostcode() {
    return clientPostcode;
  }

  public void setClientPostcode(JsonNullable<String> clientPostcode) {
    this.clientPostcode = clientPostcode;
  }

  public ClaimAmendmentPatch genderCode(String genderCode) {
    this.genderCode = JsonNullable.of(genderCode);
    return this;
  }

  /**
   * Get genderCode
   * @return genderCode
   */
  
  @Schema(name = "gender_code", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("gender_code")
  public JsonNullable<String> getGenderCode() {
    return genderCode;
  }

  public void setGenderCode(JsonNullable<String> genderCode) {
    this.genderCode = genderCode;
  }

  public ClaimAmendmentPatch ethnicityCode(String ethnicityCode) {
    this.ethnicityCode = JsonNullable.of(ethnicityCode);
    return this;
  }

  /**
   * Get ethnicityCode
   * @return ethnicityCode
   */
  
  @Schema(name = "ethnicity_code", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("ethnicity_code")
  public JsonNullable<String> getEthnicityCode() {
    return ethnicityCode;
  }

  public void setEthnicityCode(JsonNullable<String> ethnicityCode) {
    this.ethnicityCode = ethnicityCode;
  }

  public ClaimAmendmentPatch disabilityCode(String disabilityCode) {
    this.disabilityCode = JsonNullable.of(disabilityCode);
    return this;
  }

  /**
   * Get disabilityCode
   * @return disabilityCode
   */
  
  @Schema(name = "disability_code", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("disability_code")
  public JsonNullable<String> getDisabilityCode() {
    return disabilityCode;
  }

  public void setDisabilityCode(JsonNullable<String> disabilityCode) {
    this.disabilityCode = disabilityCode;
  }

  public ClaimAmendmentPatch isLegallyAided(Boolean isLegallyAided) {
    this.isLegallyAided = JsonNullable.of(isLegallyAided);
    return this;
  }

  /**
   * Get isLegallyAided
   * @return isLegallyAided
   */
  
  @Schema(name = "is_legally_aided", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("is_legally_aided")
  public JsonNullable<Boolean> getIsLegallyAided() {
    return isLegallyAided;
  }

  public void setIsLegallyAided(JsonNullable<Boolean> isLegallyAided) {
    this.isLegallyAided = isLegallyAided;
  }

  public ClaimAmendmentPatch clientTypeCode(String clientTypeCode) {
    this.clientTypeCode = JsonNullable.of(clientTypeCode);
    return this;
  }

  /**
   * Get clientTypeCode
   * @return clientTypeCode
   */
  
  @Schema(name = "client_type_code", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("client_type_code")
  public JsonNullable<String> getClientTypeCode() {
    return clientTypeCode;
  }

  public void setClientTypeCode(JsonNullable<String> clientTypeCode) {
    this.clientTypeCode = clientTypeCode;
  }

  public ClaimAmendmentPatch homeOfficeClientNumber(String homeOfficeClientNumber) {
    this.homeOfficeClientNumber = JsonNullable.of(homeOfficeClientNumber);
    return this;
  }

  /**
   * Get homeOfficeClientNumber
   * @return homeOfficeClientNumber
   */
  
  @Schema(name = "home_office_client_number", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("home_office_client_number")
  public JsonNullable<String> getHomeOfficeClientNumber() {
    return homeOfficeClientNumber;
  }

  public void setHomeOfficeClientNumber(JsonNullable<String> homeOfficeClientNumber) {
    this.homeOfficeClientNumber = homeOfficeClientNumber;
  }

  public ClaimAmendmentPatch claReferenceNumber(String claReferenceNumber) {
    this.claReferenceNumber = JsonNullable.of(claReferenceNumber);
    return this;
  }

  /**
   * Get claReferenceNumber
   * @return claReferenceNumber
   */
  
  @Schema(name = "cla_reference_number", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("cla_reference_number")
  public JsonNullable<String> getClaReferenceNumber() {
    return claReferenceNumber;
  }

  public void setClaReferenceNumber(JsonNullable<String> claReferenceNumber) {
    this.claReferenceNumber = claReferenceNumber;
  }

  public ClaimAmendmentPatch claExemptionCode(String claExemptionCode) {
    this.claExemptionCode = JsonNullable.of(claExemptionCode);
    return this;
  }

  /**
   * Get claExemptionCode
   * @return claExemptionCode
   */
  
  @Schema(name = "cla_exemption_code", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("cla_exemption_code")
  public JsonNullable<String> getClaExemptionCode() {
    return claExemptionCode;
  }

  public void setClaExemptionCode(JsonNullable<String> claExemptionCode) {
    this.claExemptionCode = claExemptionCode;
  }

  public ClaimAmendmentPatch client2Forename(String client2Forename) {
    this.client2Forename = JsonNullable.of(client2Forename);
    return this;
  }

  /**
   * Get client2Forename
   * @return client2Forename
   */
  
  @Schema(name = "client_2_forename", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("client_2_forename")
  public JsonNullable<String> getClient2Forename() {
    return client2Forename;
  }

  public void setClient2Forename(JsonNullable<String> client2Forename) {
    this.client2Forename = client2Forename;
  }

  public ClaimAmendmentPatch client2Surname(String client2Surname) {
    this.client2Surname = JsonNullable.of(client2Surname);
    return this;
  }

  /**
   * Get client2Surname
   * @return client2Surname
   */
  
  @Schema(name = "client_2_surname", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("client_2_surname")
  public JsonNullable<String> getClient2Surname() {
    return client2Surname;
  }

  public void setClient2Surname(JsonNullable<String> client2Surname) {
    this.client2Surname = client2Surname;
  }

  public ClaimAmendmentPatch client2DateOfBirth(String client2DateOfBirth) {
    this.client2DateOfBirth = JsonNullable.of(client2DateOfBirth);
    return this;
  }

  /**
   * Client 2's date of birth (format DD/MM/YYYY)
   * @return client2DateOfBirth
   */
  
  @Schema(name = "client_2_date_of_birth", description = "Client 2's date of birth (format DD/MM/YYYY)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("client_2_date_of_birth")
  public JsonNullable<String> getClient2DateOfBirth() {
    return client2DateOfBirth;
  }

  public void setClient2DateOfBirth(JsonNullable<String> client2DateOfBirth) {
    this.client2DateOfBirth = client2DateOfBirth;
  }

  public ClaimAmendmentPatch client2Ucn(String client2Ucn) {
    this.client2Ucn = JsonNullable.of(client2Ucn);
    return this;
  }

  /**
   * Get client2Ucn
   * @return client2Ucn
   */
  
  @Schema(name = "client_2_ucn", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("client_2_ucn")
  public JsonNullable<String> getClient2Ucn() {
    return client2Ucn;
  }

  public void setClient2Ucn(JsonNullable<String> client2Ucn) {
    this.client2Ucn = client2Ucn;
  }

  public ClaimAmendmentPatch client2Postcode(String client2Postcode) {
    this.client2Postcode = JsonNullable.of(client2Postcode);
    return this;
  }

  /**
   * Get client2Postcode
   * @return client2Postcode
   */
  
  @Schema(name = "client_2_postcode", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("client_2_postcode")
  public JsonNullable<String> getClient2Postcode() {
    return client2Postcode;
  }

  public void setClient2Postcode(JsonNullable<String> client2Postcode) {
    this.client2Postcode = client2Postcode;
  }

  public ClaimAmendmentPatch client2GenderCode(String client2GenderCode) {
    this.client2GenderCode = JsonNullable.of(client2GenderCode);
    return this;
  }

  /**
   * Get client2GenderCode
   * @return client2GenderCode
   */
  
  @Schema(name = "client_2_gender_code", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("client_2_gender_code")
  public JsonNullable<String> getClient2GenderCode() {
    return client2GenderCode;
  }

  public void setClient2GenderCode(JsonNullable<String> client2GenderCode) {
    this.client2GenderCode = client2GenderCode;
  }

  public ClaimAmendmentPatch client2EthnicityCode(String client2EthnicityCode) {
    this.client2EthnicityCode = JsonNullable.of(client2EthnicityCode);
    return this;
  }

  /**
   * Get client2EthnicityCode
   * @return client2EthnicityCode
   */
  
  @Schema(name = "client_2_ethnicity_code", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("client_2_ethnicity_code")
  public JsonNullable<String> getClient2EthnicityCode() {
    return client2EthnicityCode;
  }

  public void setClient2EthnicityCode(JsonNullable<String> client2EthnicityCode) {
    this.client2EthnicityCode = client2EthnicityCode;
  }

  public ClaimAmendmentPatch client2DisabilityCode(String client2DisabilityCode) {
    this.client2DisabilityCode = JsonNullable.of(client2DisabilityCode);
    return this;
  }

  /**
   * Get client2DisabilityCode
   * @return client2DisabilityCode
   */
  
  @Schema(name = "client_2_disability_code", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("client_2_disability_code")
  public JsonNullable<String> getClient2DisabilityCode() {
    return client2DisabilityCode;
  }

  public void setClient2DisabilityCode(JsonNullable<String> client2DisabilityCode) {
    this.client2DisabilityCode = client2DisabilityCode;
  }

  public ClaimAmendmentPatch client2IsLegallyAided(Boolean client2IsLegallyAided) {
    this.client2IsLegallyAided = JsonNullable.of(client2IsLegallyAided);
    return this;
  }

  /**
   * Get client2IsLegallyAided
   * @return client2IsLegallyAided
   */
  
  @Schema(name = "client_2_is_legally_aided", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("client_2_is_legally_aided")
  public JsonNullable<Boolean> getClient2IsLegallyAided() {
    return client2IsLegallyAided;
  }

  public void setClient2IsLegallyAided(JsonNullable<Boolean> client2IsLegallyAided) {
    this.client2IsLegallyAided = client2IsLegallyAided;
  }

  public ClaimAmendmentPatch caseId(String caseId) {
    this.caseId = JsonNullable.of(caseId);
    return this;
  }

  /**
   * Get caseId
   * @return caseId
   */
  
  @Schema(name = "case_id", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("case_id")
  public JsonNullable<String> getCaseId() {
    return caseId;
  }

  public void setCaseId(JsonNullable<String> caseId) {
    this.caseId = caseId;
  }

  public ClaimAmendmentPatch uniqueCaseId(String uniqueCaseId) {
    this.uniqueCaseId = JsonNullable.of(uniqueCaseId);
    return this;
  }

  /**
   * Get uniqueCaseId
   * @return uniqueCaseId
   */
  
  @Schema(name = "unique_case_id", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("unique_case_id")
  public JsonNullable<String> getUniqueCaseId() {
    return uniqueCaseId;
  }

  public void setUniqueCaseId(JsonNullable<String> uniqueCaseId) {
    this.uniqueCaseId = uniqueCaseId;
  }

  public ClaimAmendmentPatch caseStageCode(String caseStageCode) {
    this.caseStageCode = JsonNullable.of(caseStageCode);
    return this;
  }

  /**
   * Get caseStageCode
   * @return caseStageCode
   */
  
  @Schema(name = "case_stage_code", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("case_stage_code")
  public JsonNullable<String> getCaseStageCode() {
    return caseStageCode;
  }

  public void setCaseStageCode(JsonNullable<String> caseStageCode) {
    this.caseStageCode = caseStageCode;
  }

  public ClaimAmendmentPatch stageReachedCode(String stageReachedCode) {
    this.stageReachedCode = JsonNullable.of(stageReachedCode);
    return this;
  }

  /**
   * Get stageReachedCode
   * @return stageReachedCode
   */
  
  @Schema(name = "stage_reached_code", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("stage_reached_code")
  public JsonNullable<String> getStageReachedCode() {
    return stageReachedCode;
  }

  public void setStageReachedCode(JsonNullable<String> stageReachedCode) {
    this.stageReachedCode = stageReachedCode;
  }

  public ClaimAmendmentPatch standardFeeCategoryCode(String standardFeeCategoryCode) {
    this.standardFeeCategoryCode = JsonNullable.of(standardFeeCategoryCode);
    return this;
  }

  /**
   * Get standardFeeCategoryCode
   * @return standardFeeCategoryCode
   */
  
  @Schema(name = "standard_fee_category_code", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("standard_fee_category_code")
  public JsonNullable<String> getStandardFeeCategoryCode() {
    return standardFeeCategoryCode;
  }

  public void setStandardFeeCategoryCode(JsonNullable<String> standardFeeCategoryCode) {
    this.standardFeeCategoryCode = standardFeeCategoryCode;
  }

  public ClaimAmendmentPatch outcomeCode(String outcomeCode) {
    this.outcomeCode = JsonNullable.of(outcomeCode);
    return this;
  }

  /**
   * Get outcomeCode
   * @return outcomeCode
   */
  
  @Schema(name = "outcome_code", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("outcome_code")
  public JsonNullable<String> getOutcomeCode() {
    return outcomeCode;
  }

  public void setOutcomeCode(JsonNullable<String> outcomeCode) {
    this.outcomeCode = outcomeCode;
  }

  public ClaimAmendmentPatch designatedAccreditedRepresentativeCode(String designatedAccreditedRepresentativeCode) {
    this.designatedAccreditedRepresentativeCode = JsonNullable.of(designatedAccreditedRepresentativeCode);
    return this;
  }

  /**
   * Get designatedAccreditedRepresentativeCode
   * @return designatedAccreditedRepresentativeCode
   */
  
  @Schema(name = "designated_accredited_representative_code", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("designated_accredited_representative_code")
  public JsonNullable<String> getDesignatedAccreditedRepresentativeCode() {
    return designatedAccreditedRepresentativeCode;
  }

  public void setDesignatedAccreditedRepresentativeCode(JsonNullable<String> designatedAccreditedRepresentativeCode) {
    this.designatedAccreditedRepresentativeCode = designatedAccreditedRepresentativeCode;
  }

  public ClaimAmendmentPatch isPostalApplicationAccepted(Boolean isPostalApplicationAccepted) {
    this.isPostalApplicationAccepted = JsonNullable.of(isPostalApplicationAccepted);
    return this;
  }

  /**
   * Get isPostalApplicationAccepted
   * @return isPostalApplicationAccepted
   */
  
  @Schema(name = "is_postal_application_accepted", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("is_postal_application_accepted")
  public JsonNullable<Boolean> getIsPostalApplicationAccepted() {
    return isPostalApplicationAccepted;
  }

  public void setIsPostalApplicationAccepted(JsonNullable<Boolean> isPostalApplicationAccepted) {
    this.isPostalApplicationAccepted = isPostalApplicationAccepted;
  }

  public ClaimAmendmentPatch isClient2PostalApplicationAccepted(Boolean isClient2PostalApplicationAccepted) {
    this.isClient2PostalApplicationAccepted = JsonNullable.of(isClient2PostalApplicationAccepted);
    return this;
  }

  /**
   * Get isClient2PostalApplicationAccepted
   * @return isClient2PostalApplicationAccepted
   */
  
  @Schema(name = "is_client_2_postal_application_accepted", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("is_client_2_postal_application_accepted")
  public JsonNullable<Boolean> getIsClient2PostalApplicationAccepted() {
    return isClient2PostalApplicationAccepted;
  }

  public void setIsClient2PostalApplicationAccepted(JsonNullable<Boolean> isClient2PostalApplicationAccepted) {
    this.isClient2PostalApplicationAccepted = isClient2PostalApplicationAccepted;
  }

  public ClaimAmendmentPatch mentalHealthTribunalReference(String mentalHealthTribunalReference) {
    this.mentalHealthTribunalReference = JsonNullable.of(mentalHealthTribunalReference);
    return this;
  }

  /**
   * Get mentalHealthTribunalReference
   * @return mentalHealthTribunalReference
   */
  
  @Schema(name = "mental_health_tribunal_reference", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("mental_health_tribunal_reference")
  public JsonNullable<String> getMentalHealthTribunalReference() {
    return mentalHealthTribunalReference;
  }

  public void setMentalHealthTribunalReference(JsonNullable<String> mentalHealthTribunalReference) {
    this.mentalHealthTribunalReference = mentalHealthTribunalReference;
  }

  public ClaimAmendmentPatch isNrmAdvice(Boolean isNrmAdvice) {
    this.isNrmAdvice = JsonNullable.of(isNrmAdvice);
    return this;
  }

  /**
   * Get isNrmAdvice
   * @return isNrmAdvice
   */
  
  @Schema(name = "is_nrm_advice", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("is_nrm_advice")
  public JsonNullable<Boolean> getIsNrmAdvice() {
    return isNrmAdvice;
  }

  public void setIsNrmAdvice(JsonNullable<Boolean> isNrmAdvice) {
    this.isNrmAdvice = isNrmAdvice;
  }

  public ClaimAmendmentPatch followOnWork(String followOnWork) {
    this.followOnWork = JsonNullable.of(followOnWork);
    return this;
  }

  /**
   * Get followOnWork
   * @return followOnWork
   */
  
  @Schema(name = "follow_on_work", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("follow_on_work")
  public JsonNullable<String> getFollowOnWork() {
    return followOnWork;
  }

  public void setFollowOnWork(JsonNullable<String> followOnWork) {
    this.followOnWork = followOnWork;
  }

  public ClaimAmendmentPatch transferDate(String transferDate) {
    this.transferDate = JsonNullable.of(transferDate);
    return this;
  }

  /**
   * Transfer Date (format DD/MM/YYYY)
   * @return transferDate
   */
  
  @Schema(name = "transfer_date", description = "Transfer Date (format DD/MM/YYYY)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("transfer_date")
  public JsonNullable<String> getTransferDate() {
    return transferDate;
  }

  public void setTransferDate(JsonNullable<String> transferDate) {
    this.transferDate = transferDate;
  }

  public ClaimAmendmentPatch exemptionCriteriaSatisfied(String exemptionCriteriaSatisfied) {
    this.exemptionCriteriaSatisfied = JsonNullable.of(exemptionCriteriaSatisfied);
    return this;
  }

  /**
   * Get exemptionCriteriaSatisfied
   * @return exemptionCriteriaSatisfied
   */
  
  @Schema(name = "exemption_criteria_satisfied", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("exemption_criteria_satisfied")
  public JsonNullable<String> getExemptionCriteriaSatisfied() {
    return exemptionCriteriaSatisfied;
  }

  public void setExemptionCriteriaSatisfied(JsonNullable<String> exemptionCriteriaSatisfied) {
    this.exemptionCriteriaSatisfied = exemptionCriteriaSatisfied;
  }

  public ClaimAmendmentPatch exceptionalCaseFundingReference(String exceptionalCaseFundingReference) {
    this.exceptionalCaseFundingReference = JsonNullable.of(exceptionalCaseFundingReference);
    return this;
  }

  /**
   * Get exceptionalCaseFundingReference
   * @return exceptionalCaseFundingReference
   */
  
  @Schema(name = "exceptional_case_funding_reference", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("exceptional_case_funding_reference")
  public JsonNullable<String> getExceptionalCaseFundingReference() {
    return exceptionalCaseFundingReference;
  }

  public void setExceptionalCaseFundingReference(JsonNullable<String> exceptionalCaseFundingReference) {
    this.exceptionalCaseFundingReference = exceptionalCaseFundingReference;
  }

  public ClaimAmendmentPatch isLegacyCase(Boolean isLegacyCase) {
    this.isLegacyCase = JsonNullable.of(isLegacyCase);
    return this;
  }

  /**
   * Get isLegacyCase
   * @return isLegacyCase
   */
  
  @Schema(name = "is_legacy_case", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("is_legacy_case")
  public JsonNullable<Boolean> getIsLegacyCase() {
    return isLegacyCase;
  }

  public void setIsLegacyCase(JsonNullable<Boolean> isLegacyCase) {
    this.isLegacyCase = isLegacyCase;
  }

  public ClaimAmendmentPatch adviceTime(Integer adviceTime) {
    this.adviceTime = JsonNullable.of(adviceTime);
    return this;
  }

  /**
   * Get adviceTime
   * @return adviceTime
   */
  
  @Schema(name = "advice_time", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("advice_time")
  public JsonNullable<Integer> getAdviceTime() {
    return adviceTime;
  }

  public void setAdviceTime(JsonNullable<Integer> adviceTime) {
    this.adviceTime = adviceTime;
  }

  public ClaimAmendmentPatch travelTime(Integer travelTime) {
    this.travelTime = JsonNullable.of(travelTime);
    return this;
  }

  /**
   * Get travelTime
   * @return travelTime
   */
  
  @Schema(name = "travel_time", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("travel_time")
  public JsonNullable<Integer> getTravelTime() {
    return travelTime;
  }

  public void setTravelTime(JsonNullable<Integer> travelTime) {
    this.travelTime = travelTime;
  }

  public ClaimAmendmentPatch waitingTime(Integer waitingTime) {
    this.waitingTime = JsonNullable.of(waitingTime);
    return this;
  }

  /**
   * Get waitingTime
   * @return waitingTime
   */
  
  @Schema(name = "waiting_time", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("waiting_time")
  public JsonNullable<Integer> getWaitingTime() {
    return waitingTime;
  }

  public void setWaitingTime(JsonNullable<Integer> waitingTime) {
    this.waitingTime = waitingTime;
  }

  public ClaimAmendmentPatch netProfitCostsAmount(BigDecimal netProfitCostsAmount) {
    this.netProfitCostsAmount = JsonNullable.of(netProfitCostsAmount);
    return this;
  }

  /**
   * Get netProfitCostsAmount
   * @return netProfitCostsAmount
   */
  @Valid 
  @Schema(name = "net_profit_costs_amount", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("net_profit_costs_amount")
  public JsonNullable<BigDecimal> getNetProfitCostsAmount() {
    return netProfitCostsAmount;
  }

  public void setNetProfitCostsAmount(JsonNullable<BigDecimal> netProfitCostsAmount) {
    this.netProfitCostsAmount = netProfitCostsAmount;
  }

  public ClaimAmendmentPatch netDisbursementAmount(BigDecimal netDisbursementAmount) {
    this.netDisbursementAmount = JsonNullable.of(netDisbursementAmount);
    return this;
  }

  /**
   * Get netDisbursementAmount
   * @return netDisbursementAmount
   */
  @Valid 
  @Schema(name = "net_disbursement_amount", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("net_disbursement_amount")
  public JsonNullable<BigDecimal> getNetDisbursementAmount() {
    return netDisbursementAmount;
  }

  public void setNetDisbursementAmount(JsonNullable<BigDecimal> netDisbursementAmount) {
    this.netDisbursementAmount = netDisbursementAmount;
  }

  public ClaimAmendmentPatch netCounselCostsAmount(BigDecimal netCounselCostsAmount) {
    this.netCounselCostsAmount = JsonNullable.of(netCounselCostsAmount);
    return this;
  }

  /**
   * Get netCounselCostsAmount
   * @return netCounselCostsAmount
   */
  @Valid 
  @Schema(name = "net_counsel_costs_amount", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("net_counsel_costs_amount")
  public JsonNullable<BigDecimal> getNetCounselCostsAmount() {
    return netCounselCostsAmount;
  }

  public void setNetCounselCostsAmount(JsonNullable<BigDecimal> netCounselCostsAmount) {
    this.netCounselCostsAmount = netCounselCostsAmount;
  }

  public ClaimAmendmentPatch disbursementsVatAmount(BigDecimal disbursementsVatAmount) {
    this.disbursementsVatAmount = JsonNullable.of(disbursementsVatAmount);
    return this;
  }

  /**
   * Get disbursementsVatAmount
   * @return disbursementsVatAmount
   */
  @Valid 
  @Schema(name = "disbursements_vat_amount", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("disbursements_vat_amount")
  public JsonNullable<BigDecimal> getDisbursementsVatAmount() {
    return disbursementsVatAmount;
  }

  public void setDisbursementsVatAmount(JsonNullable<BigDecimal> disbursementsVatAmount) {
    this.disbursementsVatAmount = disbursementsVatAmount;
  }

  public ClaimAmendmentPatch travelWaitingCostsAmount(BigDecimal travelWaitingCostsAmount) {
    this.travelWaitingCostsAmount = JsonNullable.of(travelWaitingCostsAmount);
    return this;
  }

  /**
   * Get travelWaitingCostsAmount
   * @return travelWaitingCostsAmount
   */
  @Valid 
  @Schema(name = "travel_waiting_costs_amount", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("travel_waiting_costs_amount")
  public JsonNullable<BigDecimal> getTravelWaitingCostsAmount() {
    return travelWaitingCostsAmount;
  }

  public void setTravelWaitingCostsAmount(JsonNullable<BigDecimal> travelWaitingCostsAmount) {
    this.travelWaitingCostsAmount = travelWaitingCostsAmount;
  }

  public ClaimAmendmentPatch netWaitingCostsAmount(BigDecimal netWaitingCostsAmount) {
    this.netWaitingCostsAmount = JsonNullable.of(netWaitingCostsAmount);
    return this;
  }

  /**
   * Get netWaitingCostsAmount
   * @return netWaitingCostsAmount
   */
  @Valid 
  @Schema(name = "net_waiting_costs_amount", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("net_waiting_costs_amount")
  public JsonNullable<BigDecimal> getNetWaitingCostsAmount() {
    return netWaitingCostsAmount;
  }

  public void setNetWaitingCostsAmount(JsonNullable<BigDecimal> netWaitingCostsAmount) {
    this.netWaitingCostsAmount = netWaitingCostsAmount;
  }

  public ClaimAmendmentPatch isVatApplicable(Boolean isVatApplicable) {
    this.isVatApplicable = JsonNullable.of(isVatApplicable);
    return this;
  }

  /**
   * Get isVatApplicable
   * @return isVatApplicable
   */
  
  @Schema(name = "is_vat_applicable", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("is_vat_applicable")
  public JsonNullable<Boolean> getIsVatApplicable() {
    return isVatApplicable;
  }

  public void setIsVatApplicable(JsonNullable<Boolean> isVatApplicable) {
    this.isVatApplicable = isVatApplicable;
  }

  public ClaimAmendmentPatch isToleranceApplicable(Boolean isToleranceApplicable) {
    this.isToleranceApplicable = JsonNullable.of(isToleranceApplicable);
    return this;
  }

  /**
   * Get isToleranceApplicable
   * @return isToleranceApplicable
   */
  
  @Schema(name = "is_tolerance_applicable", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("is_tolerance_applicable")
  public JsonNullable<Boolean> getIsToleranceApplicable() {
    return isToleranceApplicable;
  }

  public void setIsToleranceApplicable(JsonNullable<Boolean> isToleranceApplicable) {
    this.isToleranceApplicable = isToleranceApplicable;
  }

  public ClaimAmendmentPatch priorAuthorityReference(String priorAuthorityReference) {
    this.priorAuthorityReference = JsonNullable.of(priorAuthorityReference);
    return this;
  }

  /**
   * Get priorAuthorityReference
   * @return priorAuthorityReference
   */
  
  @Schema(name = "prior_authority_reference", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("prior_authority_reference")
  public JsonNullable<String> getPriorAuthorityReference() {
    return priorAuthorityReference;
  }

  public void setPriorAuthorityReference(JsonNullable<String> priorAuthorityReference) {
    this.priorAuthorityReference = priorAuthorityReference;
  }

  public ClaimAmendmentPatch isLondonRate(Boolean isLondonRate) {
    this.isLondonRate = JsonNullable.of(isLondonRate);
    return this;
  }

  /**
   * Get isLondonRate
   * @return isLondonRate
   */
  
  @Schema(name = "is_london_rate", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("is_london_rate")
  public JsonNullable<Boolean> getIsLondonRate() {
    return isLondonRate;
  }

  public void setIsLondonRate(JsonNullable<Boolean> isLondonRate) {
    this.isLondonRate = isLondonRate;
  }

  public ClaimAmendmentPatch adjournedHearingFeeAmount(Integer adjournedHearingFeeAmount) {
    this.adjournedHearingFeeAmount = JsonNullable.of(adjournedHearingFeeAmount);
    return this;
  }

  /**
   * Note: actually stores the number of times the hearing was adjourned
   * @return adjournedHearingFeeAmount
   */
  
  @Schema(name = "adjourned_hearing_fee_amount", description = "Note: actually stores the number of times the hearing was adjourned", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("adjourned_hearing_fee_amount")
  public JsonNullable<Integer> getAdjournedHearingFeeAmount() {
    return adjournedHearingFeeAmount;
  }

  public void setAdjournedHearingFeeAmount(JsonNullable<Integer> adjournedHearingFeeAmount) {
    this.adjournedHearingFeeAmount = adjournedHearingFeeAmount;
  }

  public ClaimAmendmentPatch isAdditionalTravelPayment(Boolean isAdditionalTravelPayment) {
    this.isAdditionalTravelPayment = JsonNullable.of(isAdditionalTravelPayment);
    return this;
  }

  /**
   * Get isAdditionalTravelPayment
   * @return isAdditionalTravelPayment
   */
  
  @Schema(name = "is_additional_travel_payment", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("is_additional_travel_payment")
  public JsonNullable<Boolean> getIsAdditionalTravelPayment() {
    return isAdditionalTravelPayment;
  }

  public void setIsAdditionalTravelPayment(JsonNullable<Boolean> isAdditionalTravelPayment) {
    this.isAdditionalTravelPayment = isAdditionalTravelPayment;
  }

  public ClaimAmendmentPatch costsDamagesRecoveredAmount(BigDecimal costsDamagesRecoveredAmount) {
    this.costsDamagesRecoveredAmount = JsonNullable.of(costsDamagesRecoveredAmount);
    return this;
  }

  /**
   * Get costsDamagesRecoveredAmount
   * @return costsDamagesRecoveredAmount
   */
  @Valid 
  @Schema(name = "costs_damages_recovered_amount", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("costs_damages_recovered_amount")
  public JsonNullable<BigDecimal> getCostsDamagesRecoveredAmount() {
    return costsDamagesRecoveredAmount;
  }

  public void setCostsDamagesRecoveredAmount(JsonNullable<BigDecimal> costsDamagesRecoveredAmount) {
    this.costsDamagesRecoveredAmount = costsDamagesRecoveredAmount;
  }

  public ClaimAmendmentPatch meetingsAttendedCode(String meetingsAttendedCode) {
    this.meetingsAttendedCode = JsonNullable.of(meetingsAttendedCode);
    return this;
  }

  /**
   * Get meetingsAttendedCode
   * @return meetingsAttendedCode
   */
  
  @Schema(name = "meetings_attended_code", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("meetings_attended_code")
  public JsonNullable<String> getMeetingsAttendedCode() {
    return meetingsAttendedCode;
  }

  public void setMeetingsAttendedCode(JsonNullable<String> meetingsAttendedCode) {
    this.meetingsAttendedCode = meetingsAttendedCode;
  }

  public ClaimAmendmentPatch detentionTravelWaitingCostsAmount(BigDecimal detentionTravelWaitingCostsAmount) {
    this.detentionTravelWaitingCostsAmount = JsonNullable.of(detentionTravelWaitingCostsAmount);
    return this;
  }

  /**
   * Get detentionTravelWaitingCostsAmount
   * @return detentionTravelWaitingCostsAmount
   */
  @Valid 
  @Schema(name = "detention_travel_waiting_costs_amount", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("detention_travel_waiting_costs_amount")
  public JsonNullable<BigDecimal> getDetentionTravelWaitingCostsAmount() {
    return detentionTravelWaitingCostsAmount;
  }

  public void setDetentionTravelWaitingCostsAmount(JsonNullable<BigDecimal> detentionTravelWaitingCostsAmount) {
    this.detentionTravelWaitingCostsAmount = detentionTravelWaitingCostsAmount;
  }

  public ClaimAmendmentPatch jrFormFillingAmount(BigDecimal jrFormFillingAmount) {
    this.jrFormFillingAmount = JsonNullable.of(jrFormFillingAmount);
    return this;
  }

  /**
   * Get jrFormFillingAmount
   * @return jrFormFillingAmount
   */
  @Valid 
  @Schema(name = "jr_form_filling_amount", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("jr_form_filling_amount")
  public JsonNullable<BigDecimal> getJrFormFillingAmount() {
    return jrFormFillingAmount;
  }

  public void setJrFormFillingAmount(JsonNullable<BigDecimal> jrFormFillingAmount) {
    this.jrFormFillingAmount = jrFormFillingAmount;
  }

  public ClaimAmendmentPatch isEligibleClient(Boolean isEligibleClient) {
    this.isEligibleClient = JsonNullable.of(isEligibleClient);
    return this;
  }

  /**
   * Get isEligibleClient
   * @return isEligibleClient
   */
  
  @Schema(name = "is_eligible_client", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("is_eligible_client")
  public JsonNullable<Boolean> getIsEligibleClient() {
    return isEligibleClient;
  }

  public void setIsEligibleClient(JsonNullable<Boolean> isEligibleClient) {
    this.isEligibleClient = isEligibleClient;
  }

  public ClaimAmendmentPatch courtLocationCode(String courtLocationCode) {
    this.courtLocationCode = JsonNullable.of(courtLocationCode);
    return this;
  }

  /**
   * Get courtLocationCode
   * @return courtLocationCode
   */
  
  @Schema(name = "court_location_code", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("court_location_code")
  public JsonNullable<String> getCourtLocationCode() {
    return courtLocationCode;
  }

  public void setCourtLocationCode(JsonNullable<String> courtLocationCode) {
    this.courtLocationCode = courtLocationCode;
  }

  public ClaimAmendmentPatch adviceTypeCode(String adviceTypeCode) {
    this.adviceTypeCode = JsonNullable.of(adviceTypeCode);
    return this;
  }

  /**
   * Get adviceTypeCode
   * @return adviceTypeCode
   */
  
  @Schema(name = "advice_type_code", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("advice_type_code")
  public JsonNullable<String> getAdviceTypeCode() {
    return adviceTypeCode;
  }

  public void setAdviceTypeCode(JsonNullable<String> adviceTypeCode) {
    this.adviceTypeCode = adviceTypeCode;
  }

  public ClaimAmendmentPatch medicalReportsCount(Integer medicalReportsCount) {
    this.medicalReportsCount = JsonNullable.of(medicalReportsCount);
    return this;
  }

  /**
   * Get medicalReportsCount
   * @return medicalReportsCount
   */
  
  @Schema(name = "medical_reports_count", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("medical_reports_count")
  public JsonNullable<Integer> getMedicalReportsCount() {
    return medicalReportsCount;
  }

  public void setMedicalReportsCount(JsonNullable<Integer> medicalReportsCount) {
    this.medicalReportsCount = medicalReportsCount;
  }

  public ClaimAmendmentPatch isIrcSurgery(Boolean isIrcSurgery) {
    this.isIrcSurgery = JsonNullable.of(isIrcSurgery);
    return this;
  }

  /**
   * Get isIrcSurgery
   * @return isIrcSurgery
   */
  
  @Schema(name = "is_irc_surgery", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("is_irc_surgery")
  public JsonNullable<Boolean> getIsIrcSurgery() {
    return isIrcSurgery;
  }

  public void setIsIrcSurgery(JsonNullable<Boolean> isIrcSurgery) {
    this.isIrcSurgery = isIrcSurgery;
  }

  public ClaimAmendmentPatch surgeryDate(String surgeryDate) {
    this.surgeryDate = JsonNullable.of(surgeryDate);
    return this;
  }

  /**
   * Surgery Date (format DD/MM/YYYY)
   * @return surgeryDate
   */
  
  @Schema(name = "surgery_date", description = "Surgery Date (format DD/MM/YYYY)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("surgery_date")
  public JsonNullable<String> getSurgeryDate() {
    return surgeryDate;
  }

  public void setSurgeryDate(JsonNullable<String> surgeryDate) {
    this.surgeryDate = surgeryDate;
  }

  public ClaimAmendmentPatch surgeryClientsCount(Integer surgeryClientsCount) {
    this.surgeryClientsCount = JsonNullable.of(surgeryClientsCount);
    return this;
  }

  /**
   * Get surgeryClientsCount
   * @return surgeryClientsCount
   */
  
  @Schema(name = "surgery_clients_count", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("surgery_clients_count")
  public JsonNullable<Integer> getSurgeryClientsCount() {
    return surgeryClientsCount;
  }

  public void setSurgeryClientsCount(JsonNullable<Integer> surgeryClientsCount) {
    this.surgeryClientsCount = surgeryClientsCount;
  }

  public ClaimAmendmentPatch surgeryMattersCount(Integer surgeryMattersCount) {
    this.surgeryMattersCount = JsonNullable.of(surgeryMattersCount);
    return this;
  }

  /**
   * Get surgeryMattersCount
   * @return surgeryMattersCount
   */
  
  @Schema(name = "surgery_matters_count", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("surgery_matters_count")
  public JsonNullable<Integer> getSurgeryMattersCount() {
    return surgeryMattersCount;
  }

  public void setSurgeryMattersCount(JsonNullable<Integer> surgeryMattersCount) {
    this.surgeryMattersCount = surgeryMattersCount;
  }

  public ClaimAmendmentPatch cmrhOralCount(Integer cmrhOralCount) {
    this.cmrhOralCount = JsonNullable.of(cmrhOralCount);
    return this;
  }

  /**
   * Get cmrhOralCount
   * @return cmrhOralCount
   */
  
  @Schema(name = "cmrh_oral_count", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("cmrh_oral_count")
  public JsonNullable<Integer> getCmrhOralCount() {
    return cmrhOralCount;
  }

  public void setCmrhOralCount(JsonNullable<Integer> cmrhOralCount) {
    this.cmrhOralCount = cmrhOralCount;
  }

  public ClaimAmendmentPatch cmrhTelephoneCount(Integer cmrhTelephoneCount) {
    this.cmrhTelephoneCount = JsonNullable.of(cmrhTelephoneCount);
    return this;
  }

  /**
   * Get cmrhTelephoneCount
   * @return cmrhTelephoneCount
   */
  
  @Schema(name = "cmrh_telephone_count", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("cmrh_telephone_count")
  public JsonNullable<Integer> getCmrhTelephoneCount() {
    return cmrhTelephoneCount;
  }

  public void setCmrhTelephoneCount(JsonNullable<Integer> cmrhTelephoneCount) {
    this.cmrhTelephoneCount = cmrhTelephoneCount;
  }

  public ClaimAmendmentPatch aitHearingCentreCode(String aitHearingCentreCode) {
    this.aitHearingCentreCode = JsonNullable.of(aitHearingCentreCode);
    return this;
  }

  /**
   * Get aitHearingCentreCode
   * @return aitHearingCentreCode
   */
  
  @Schema(name = "ait_hearing_centre_code", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("ait_hearing_centre_code")
  public JsonNullable<String> getAitHearingCentreCode() {
    return aitHearingCentreCode;
  }

  public void setAitHearingCentreCode(JsonNullable<String> aitHearingCentreCode) {
    this.aitHearingCentreCode = aitHearingCentreCode;
  }

  public ClaimAmendmentPatch isSubstantiveHearing(Boolean isSubstantiveHearing) {
    this.isSubstantiveHearing = JsonNullable.of(isSubstantiveHearing);
    return this;
  }

  /**
   * Get isSubstantiveHearing
   * @return isSubstantiveHearing
   */
  
  @Schema(name = "is_substantive_hearing", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("is_substantive_hearing")
  public JsonNullable<Boolean> getIsSubstantiveHearing() {
    return isSubstantiveHearing;
  }

  public void setIsSubstantiveHearing(JsonNullable<Boolean> isSubstantiveHearing) {
    this.isSubstantiveHearing = isSubstantiveHearing;
  }

  public ClaimAmendmentPatch hoInterview(Integer hoInterview) {
    this.hoInterview = JsonNullable.of(hoInterview);
    return this;
  }

  /**
   * Number of Home Office Interviews
   * @return hoInterview
   */
  
  @Schema(name = "ho_interview", description = "Number of Home Office Interviews", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("ho_interview")
  public JsonNullable<Integer> getHoInterview() {
    return hoInterview;
  }

  public void setHoInterview(JsonNullable<Integer> hoInterview) {
    this.hoInterview = hoInterview;
  }

  public ClaimAmendmentPatch localAuthorityNumber(String localAuthorityNumber) {
    this.localAuthorityNumber = JsonNullable.of(localAuthorityNumber);
    return this;
  }

  /**
   * Get localAuthorityNumber
   * @return localAuthorityNumber
   */
  
  @Schema(name = "local_authority_number", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("local_authority_number")
  public JsonNullable<String> getLocalAuthorityNumber() {
    return localAuthorityNumber;
  }

  public void setLocalAuthorityNumber(JsonNullable<String> localAuthorityNumber) {
    this.localAuthorityNumber = localAuthorityNumber;
  }

  public ClaimAmendmentPatch submissionPeriod(String submissionPeriod) {
    this.submissionPeriod = JsonNullable.of(submissionPeriod);
    return this;
  }

  /**
   * Submission period (e.g., \"JUL-2025\").
   * @return submissionPeriod
   */
  
  @Schema(name = "submission_period", description = "Submission period (e.g., \"JUL-2025\").", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("submission_period")
  public JsonNullable<String> getSubmissionPeriod() {
    return submissionPeriod;
  }

  public void setSubmissionPeriod(JsonNullable<String> submissionPeriod) {
    this.submissionPeriod = submissionPeriod;
  }

  public ClaimAmendmentPatch createdByUserId(String createdByUserId) {
    this.createdByUserId = JsonNullable.of(createdByUserId);
    return this;
  }

  /**
   * The id of the user who created the claim.
   * @return createdByUserId
   */
  
  @Schema(name = "created_by_user_id", description = "The id of the user who created the claim.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("created_by_user_id")
  public JsonNullable<String> getCreatedByUserId() {
    return createdByUserId;
  }

  public void setCreatedByUserId(JsonNullable<String> createdByUserId) {
    this.createdByUserId = createdByUserId;
  }

  public ClaimAmendmentPatch isAmended(Boolean isAmended) {
    this.isAmended = JsonNullable.of(isAmended);
    return this;
  }

  /**
   * Indicates if the claim has been amended.
   * @return isAmended
   */
  
  @Schema(name = "is_amended", description = "Indicates if the claim has been amended.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("is_amended")
  public JsonNullable<Boolean> getIsAmended() {
    return isAmended;
  }

  public void setIsAmended(JsonNullable<Boolean> isAmended) {
    this.isAmended = isAmended;
  }

  public ClaimAmendmentPatch hasAssessment(Boolean hasAssessment) {
    this.hasAssessment = JsonNullable.of(hasAssessment);
    return this;
  }

  /**
   * Indicates if the claim has an associated assessment.
   * @return hasAssessment
   */
  
  @Schema(name = "has_assessment", description = "Indicates if the claim has an associated assessment.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("has_assessment")
  public JsonNullable<Boolean> getHasAssessment() {
    return hasAssessment;
  }

  public void setHasAssessment(JsonNullable<Boolean> hasAssessment) {
    this.hasAssessment = hasAssessment;
  }

  public ClaimAmendmentPatch version(Long version) {
    this.version = JsonNullable.of(version);
    return this;
  }

  /**
   * Used for optimistic locking
   * @return version
   */
  
  @Schema(name = "version", description = "Used for optimistic locking", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("version")
  public JsonNullable<Long> getVersion() {
    return version;
  }

  public void setVersion(JsonNullable<Long> version) {
    this.version = version;
  }

  public ClaimAmendmentPatch totalWarnings(@Nullable Integer totalWarnings) {
    this.totalWarnings = totalWarnings;
    return this;
  }

  /**
   * Number of validation warnings for this claim.
   * @return totalWarnings
   */
  
  @Schema(name = "total_warnings", description = "Number of validation warnings for this claim.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("total_warnings")
  public @Nullable Integer getTotalWarnings() {
    return totalWarnings;
  }

  @JsonProperty("total_warnings")
  public void setTotalWarnings(@Nullable Integer totalWarnings) {
    this.totalWarnings = totalWarnings;
  }

  public ClaimAmendmentPatch feeCalculationResponse(@Nullable FeeCalculationPatch feeCalculationResponse) {
    this.feeCalculationResponse = feeCalculationResponse;
    return this;
  }

  /**
   * Get feeCalculationResponse
   * @return feeCalculationResponse
   */
  @Valid 
  @Schema(name = "fee_calculation_response", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("fee_calculation_response")
  public @Nullable FeeCalculationPatch getFeeCalculationResponse() {
    return feeCalculationResponse;
  }

  @JsonProperty("fee_calculation_response")
  public void setFeeCalculationResponse(@Nullable FeeCalculationPatch feeCalculationResponse) {
    this.feeCalculationResponse = feeCalculationResponse;
  }

  public ClaimAmendmentPatch amendmentRequestedBy(String amendmentRequestedBy) {
    this.amendmentRequestedBy = JsonNullable.of(amendmentRequestedBy);
    return this;
  }

  /**
   * Requesting party code for an amendment (governed reference). Use values from GET /api/v1/system/references/amendment-requested-by
   * @return amendmentRequestedBy
   */
  
  @Schema(name = "amendment_requested_by", description = "Requesting party code for an amendment (governed reference). Use values from GET /api/v1/system/references/amendment-requested-by", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("amendment_requested_by")
  public JsonNullable<String> getAmendmentRequestedBy() {
    return amendmentRequestedBy;
  }

  public void setAmendmentRequestedBy(JsonNullable<String> amendmentRequestedBy) {
    this.amendmentRequestedBy = amendmentRequestedBy;
  }

  public ClaimAmendmentPatch amendmentUserId(UUID amendmentUserId) {
    this.amendmentUserId = JsonNullable.of(amendmentUserId);
    return this;
  }

  /**
   * UUID of the user who requested the amendment (provider/Entra user id).
   * @return amendmentUserId
   */
  @Valid 
  @Schema(name = "amendment_user_id", description = "UUID of the user who requested the amendment (provider/Entra user id).", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("amendment_user_id")
  public JsonNullable<UUID> getAmendmentUserId() {
    return amendmentUserId;
  }

  public void setAmendmentUserId(JsonNullable<UUID> amendmentUserId) {
    this.amendmentUserId = amendmentUserId;
  }

  public ClaimAmendmentPatch amendmentReasonCode(String amendmentReasonCode) {
    this.amendmentReasonCode = JsonNullable.of(amendmentReasonCode);
    return this;
  }

  /**
   * Amendment reason code valid for the requesting party (see amendment_requested_by reference reasons).
   * @return amendmentReasonCode
   */
  
  @Schema(name = "amendment_reason_code", description = "Amendment reason code valid for the requesting party (see amendment_requested_by reference reasons).", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("amendment_reason_code")
  public JsonNullable<String> getAmendmentReasonCode() {
    return amendmentReasonCode;
  }

  public void setAmendmentReasonCode(JsonNullable<String> amendmentReasonCode) {
    this.amendmentReasonCode = amendmentReasonCode;
  }

  public ClaimAmendmentPatch validationMessages(List<@Valid ValidationMessagePatch> validationMessages) {
    this.validationMessages = validationMessages;
    return this;
  }

  public ClaimAmendmentPatch addValidationMessagesItem(ValidationMessagePatch validationMessagesItem) {
    if (this.validationMessages == null) {
      this.validationMessages = new ArrayList<>();
    }
    this.validationMessages.add(validationMessagesItem);
    return this;
  }

  /**
   * Get validationMessages
   * @return validationMessages
   */
  @Valid 
  @Schema(name = "validation_messages", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("validation_messages")
  public List<@Valid ValidationMessagePatch> getValidationMessages() {
    return validationMessages;
  }

  @JsonProperty("validation_messages")
  public void setValidationMessages(List<@Valid ValidationMessagePatch> validationMessages) {
    this.validationMessages = validationMessages;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ClaimAmendmentPatch claimAmendmentPatch = (ClaimAmendmentPatch) o;
    return equalsNullable(this.id, claimAmendmentPatch.id) &&
        equalsNullable(this.submissionId, claimAmendmentPatch.submissionId) &&
        Objects.equals(this.status, claimAmendmentPatch.status) &&
        equalsNullable(this.scheduleReference, claimAmendmentPatch.scheduleReference) &&
        equalsNullable(this.lineNumber, claimAmendmentPatch.lineNumber) &&
        equalsNullable(this.caseReferenceNumber, claimAmendmentPatch.caseReferenceNumber) &&
        equalsNullable(this.uniqueFileNumber, claimAmendmentPatch.uniqueFileNumber) &&
        equalsNullable(this.caseStartDate, claimAmendmentPatch.caseStartDate) &&
        equalsNullable(this.caseConcludedDate, claimAmendmentPatch.caseConcludedDate) &&
        equalsNullable(this.matterTypeCode, claimAmendmentPatch.matterTypeCode) &&
        equalsNullable(this.crimeMatterTypeCode, claimAmendmentPatch.crimeMatterTypeCode) &&
        equalsNullable(this.feeSchemeCode, claimAmendmentPatch.feeSchemeCode) &&
        equalsNullable(this.feeCode, claimAmendmentPatch.feeCode) &&
        equalsNullable(this.procurementAreaCode, claimAmendmentPatch.procurementAreaCode) &&
        equalsNullable(this.accessPointCode, claimAmendmentPatch.accessPointCode) &&
        equalsNullable(this.deliveryLocation, claimAmendmentPatch.deliveryLocation) &&
        equalsNullable(this.representationOrderDate, claimAmendmentPatch.representationOrderDate) &&
        equalsNullable(this.suspectsDefendantsCount, claimAmendmentPatch.suspectsDefendantsCount) &&
        equalsNullable(this.policeStationCourtAttendancesCount, claimAmendmentPatch.policeStationCourtAttendancesCount) &&
        equalsNullable(this.policeStationCourtPrisonId, claimAmendmentPatch.policeStationCourtPrisonId) &&
        equalsNullable(this.dsccNumber, claimAmendmentPatch.dsccNumber) &&
        equalsNullable(this.maatId, claimAmendmentPatch.maatId) &&
        equalsNullable(this.prisonLawPriorApprovalNumber, claimAmendmentPatch.prisonLawPriorApprovalNumber) &&
        equalsNullable(this.isDutySolicitor, claimAmendmentPatch.isDutySolicitor) &&
        equalsNullable(this.isYouthCourt, claimAmendmentPatch.isYouthCourt) &&
        equalsNullable(this.schemeId, claimAmendmentPatch.schemeId) &&
        equalsNullable(this.mediationSessionsCount, claimAmendmentPatch.mediationSessionsCount) &&
        equalsNullable(this.mediationTimeMinutes, claimAmendmentPatch.mediationTimeMinutes) &&
        equalsNullable(this.outreachLocation, claimAmendmentPatch.outreachLocation) &&
        equalsNullable(this.referralSource, claimAmendmentPatch.referralSource) &&
        equalsNullable(this.clientForename, claimAmendmentPatch.clientForename) &&
        equalsNullable(this.clientSurname, claimAmendmentPatch.clientSurname) &&
        equalsNullable(this.clientDateOfBirth, claimAmendmentPatch.clientDateOfBirth) &&
        equalsNullable(this.uniqueClientNumber, claimAmendmentPatch.uniqueClientNumber) &&
        equalsNullable(this.clientPostcode, claimAmendmentPatch.clientPostcode) &&
        equalsNullable(this.genderCode, claimAmendmentPatch.genderCode) &&
        equalsNullable(this.ethnicityCode, claimAmendmentPatch.ethnicityCode) &&
        equalsNullable(this.disabilityCode, claimAmendmentPatch.disabilityCode) &&
        equalsNullable(this.isLegallyAided, claimAmendmentPatch.isLegallyAided) &&
        equalsNullable(this.clientTypeCode, claimAmendmentPatch.clientTypeCode) &&
        equalsNullable(this.homeOfficeClientNumber, claimAmendmentPatch.homeOfficeClientNumber) &&
        equalsNullable(this.claReferenceNumber, claimAmendmentPatch.claReferenceNumber) &&
        equalsNullable(this.claExemptionCode, claimAmendmentPatch.claExemptionCode) &&
        equalsNullable(this.client2Forename, claimAmendmentPatch.client2Forename) &&
        equalsNullable(this.client2Surname, claimAmendmentPatch.client2Surname) &&
        equalsNullable(this.client2DateOfBirth, claimAmendmentPatch.client2DateOfBirth) &&
        equalsNullable(this.client2Ucn, claimAmendmentPatch.client2Ucn) &&
        equalsNullable(this.client2Postcode, claimAmendmentPatch.client2Postcode) &&
        equalsNullable(this.client2GenderCode, claimAmendmentPatch.client2GenderCode) &&
        equalsNullable(this.client2EthnicityCode, claimAmendmentPatch.client2EthnicityCode) &&
        equalsNullable(this.client2DisabilityCode, claimAmendmentPatch.client2DisabilityCode) &&
        equalsNullable(this.client2IsLegallyAided, claimAmendmentPatch.client2IsLegallyAided) &&
        equalsNullable(this.caseId, claimAmendmentPatch.caseId) &&
        equalsNullable(this.uniqueCaseId, claimAmendmentPatch.uniqueCaseId) &&
        equalsNullable(this.caseStageCode, claimAmendmentPatch.caseStageCode) &&
        equalsNullable(this.stageReachedCode, claimAmendmentPatch.stageReachedCode) &&
        equalsNullable(this.standardFeeCategoryCode, claimAmendmentPatch.standardFeeCategoryCode) &&
        equalsNullable(this.outcomeCode, claimAmendmentPatch.outcomeCode) &&
        equalsNullable(this.designatedAccreditedRepresentativeCode, claimAmendmentPatch.designatedAccreditedRepresentativeCode) &&
        equalsNullable(this.isPostalApplicationAccepted, claimAmendmentPatch.isPostalApplicationAccepted) &&
        equalsNullable(this.isClient2PostalApplicationAccepted, claimAmendmentPatch.isClient2PostalApplicationAccepted) &&
        equalsNullable(this.mentalHealthTribunalReference, claimAmendmentPatch.mentalHealthTribunalReference) &&
        equalsNullable(this.isNrmAdvice, claimAmendmentPatch.isNrmAdvice) &&
        equalsNullable(this.followOnWork, claimAmendmentPatch.followOnWork) &&
        equalsNullable(this.transferDate, claimAmendmentPatch.transferDate) &&
        equalsNullable(this.exemptionCriteriaSatisfied, claimAmendmentPatch.exemptionCriteriaSatisfied) &&
        equalsNullable(this.exceptionalCaseFundingReference, claimAmendmentPatch.exceptionalCaseFundingReference) &&
        equalsNullable(this.isLegacyCase, claimAmendmentPatch.isLegacyCase) &&
        equalsNullable(this.adviceTime, claimAmendmentPatch.adviceTime) &&
        equalsNullable(this.travelTime, claimAmendmentPatch.travelTime) &&
        equalsNullable(this.waitingTime, claimAmendmentPatch.waitingTime) &&
        equalsNullable(this.netProfitCostsAmount, claimAmendmentPatch.netProfitCostsAmount) &&
        equalsNullable(this.netDisbursementAmount, claimAmendmentPatch.netDisbursementAmount) &&
        equalsNullable(this.netCounselCostsAmount, claimAmendmentPatch.netCounselCostsAmount) &&
        equalsNullable(this.disbursementsVatAmount, claimAmendmentPatch.disbursementsVatAmount) &&
        equalsNullable(this.travelWaitingCostsAmount, claimAmendmentPatch.travelWaitingCostsAmount) &&
        equalsNullable(this.netWaitingCostsAmount, claimAmendmentPatch.netWaitingCostsAmount) &&
        equalsNullable(this.isVatApplicable, claimAmendmentPatch.isVatApplicable) &&
        equalsNullable(this.isToleranceApplicable, claimAmendmentPatch.isToleranceApplicable) &&
        equalsNullable(this.priorAuthorityReference, claimAmendmentPatch.priorAuthorityReference) &&
        equalsNullable(this.isLondonRate, claimAmendmentPatch.isLondonRate) &&
        equalsNullable(this.adjournedHearingFeeAmount, claimAmendmentPatch.adjournedHearingFeeAmount) &&
        equalsNullable(this.isAdditionalTravelPayment, claimAmendmentPatch.isAdditionalTravelPayment) &&
        equalsNullable(this.costsDamagesRecoveredAmount, claimAmendmentPatch.costsDamagesRecoveredAmount) &&
        equalsNullable(this.meetingsAttendedCode, claimAmendmentPatch.meetingsAttendedCode) &&
        equalsNullable(this.detentionTravelWaitingCostsAmount, claimAmendmentPatch.detentionTravelWaitingCostsAmount) &&
        equalsNullable(this.jrFormFillingAmount, claimAmendmentPatch.jrFormFillingAmount) &&
        equalsNullable(this.isEligibleClient, claimAmendmentPatch.isEligibleClient) &&
        equalsNullable(this.courtLocationCode, claimAmendmentPatch.courtLocationCode) &&
        equalsNullable(this.adviceTypeCode, claimAmendmentPatch.adviceTypeCode) &&
        equalsNullable(this.medicalReportsCount, claimAmendmentPatch.medicalReportsCount) &&
        equalsNullable(this.isIrcSurgery, claimAmendmentPatch.isIrcSurgery) &&
        equalsNullable(this.surgeryDate, claimAmendmentPatch.surgeryDate) &&
        equalsNullable(this.surgeryClientsCount, claimAmendmentPatch.surgeryClientsCount) &&
        equalsNullable(this.surgeryMattersCount, claimAmendmentPatch.surgeryMattersCount) &&
        equalsNullable(this.cmrhOralCount, claimAmendmentPatch.cmrhOralCount) &&
        equalsNullable(this.cmrhTelephoneCount, claimAmendmentPatch.cmrhTelephoneCount) &&
        equalsNullable(this.aitHearingCentreCode, claimAmendmentPatch.aitHearingCentreCode) &&
        equalsNullable(this.isSubstantiveHearing, claimAmendmentPatch.isSubstantiveHearing) &&
        equalsNullable(this.hoInterview, claimAmendmentPatch.hoInterview) &&
        equalsNullable(this.localAuthorityNumber, claimAmendmentPatch.localAuthorityNumber) &&
        equalsNullable(this.submissionPeriod, claimAmendmentPatch.submissionPeriod) &&
        equalsNullable(this.createdByUserId, claimAmendmentPatch.createdByUserId) &&
        equalsNullable(this.isAmended, claimAmendmentPatch.isAmended) &&
        equalsNullable(this.hasAssessment, claimAmendmentPatch.hasAssessment) &&
        equalsNullable(this.version, claimAmendmentPatch.version) &&
        Objects.equals(this.totalWarnings, claimAmendmentPatch.totalWarnings) &&
        Objects.equals(this.feeCalculationResponse, claimAmendmentPatch.feeCalculationResponse) &&
        equalsNullable(this.amendmentRequestedBy, claimAmendmentPatch.amendmentRequestedBy) &&
        equalsNullable(this.amendmentUserId, claimAmendmentPatch.amendmentUserId) &&
        equalsNullable(this.amendmentReasonCode, claimAmendmentPatch.amendmentReasonCode) &&
        Objects.equals(this.validationMessages, claimAmendmentPatch.validationMessages);
  }

  private static <T> boolean equalsNullable(JsonNullable<T> a, JsonNullable<T> b) {
    return a == b || (a != null && b != null && a.isPresent() && b.isPresent() && Objects.deepEquals(a.get(), b.get()));
  }

  @Override
  public int hashCode() {
    return Objects.hash(hashCodeNullable(id), hashCodeNullable(submissionId), status, hashCodeNullable(scheduleReference), hashCodeNullable(lineNumber), hashCodeNullable(caseReferenceNumber), hashCodeNullable(uniqueFileNumber), hashCodeNullable(caseStartDate), hashCodeNullable(caseConcludedDate), hashCodeNullable(matterTypeCode), hashCodeNullable(crimeMatterTypeCode), hashCodeNullable(feeSchemeCode), hashCodeNullable(feeCode), hashCodeNullable(procurementAreaCode), hashCodeNullable(accessPointCode), hashCodeNullable(deliveryLocation), hashCodeNullable(representationOrderDate), hashCodeNullable(suspectsDefendantsCount), hashCodeNullable(policeStationCourtAttendancesCount), hashCodeNullable(policeStationCourtPrisonId), hashCodeNullable(dsccNumber), hashCodeNullable(maatId), hashCodeNullable(prisonLawPriorApprovalNumber), hashCodeNullable(isDutySolicitor), hashCodeNullable(isYouthCourt), hashCodeNullable(schemeId), hashCodeNullable(mediationSessionsCount), hashCodeNullable(mediationTimeMinutes), hashCodeNullable(outreachLocation), hashCodeNullable(referralSource), hashCodeNullable(clientForename), hashCodeNullable(clientSurname), hashCodeNullable(clientDateOfBirth), hashCodeNullable(uniqueClientNumber), hashCodeNullable(clientPostcode), hashCodeNullable(genderCode), hashCodeNullable(ethnicityCode), hashCodeNullable(disabilityCode), hashCodeNullable(isLegallyAided), hashCodeNullable(clientTypeCode), hashCodeNullable(homeOfficeClientNumber), hashCodeNullable(claReferenceNumber), hashCodeNullable(claExemptionCode), hashCodeNullable(client2Forename), hashCodeNullable(client2Surname), hashCodeNullable(client2DateOfBirth), hashCodeNullable(client2Ucn), hashCodeNullable(client2Postcode), hashCodeNullable(client2GenderCode), hashCodeNullable(client2EthnicityCode), hashCodeNullable(client2DisabilityCode), hashCodeNullable(client2IsLegallyAided), hashCodeNullable(caseId), hashCodeNullable(uniqueCaseId), hashCodeNullable(caseStageCode), hashCodeNullable(stageReachedCode), hashCodeNullable(standardFeeCategoryCode), hashCodeNullable(outcomeCode), hashCodeNullable(designatedAccreditedRepresentativeCode), hashCodeNullable(isPostalApplicationAccepted), hashCodeNullable(isClient2PostalApplicationAccepted), hashCodeNullable(mentalHealthTribunalReference), hashCodeNullable(isNrmAdvice), hashCodeNullable(followOnWork), hashCodeNullable(transferDate), hashCodeNullable(exemptionCriteriaSatisfied), hashCodeNullable(exceptionalCaseFundingReference), hashCodeNullable(isLegacyCase), hashCodeNullable(adviceTime), hashCodeNullable(travelTime), hashCodeNullable(waitingTime), hashCodeNullable(netProfitCostsAmount), hashCodeNullable(netDisbursementAmount), hashCodeNullable(netCounselCostsAmount), hashCodeNullable(disbursementsVatAmount), hashCodeNullable(travelWaitingCostsAmount), hashCodeNullable(netWaitingCostsAmount), hashCodeNullable(isVatApplicable), hashCodeNullable(isToleranceApplicable), hashCodeNullable(priorAuthorityReference), hashCodeNullable(isLondonRate), hashCodeNullable(adjournedHearingFeeAmount), hashCodeNullable(isAdditionalTravelPayment), hashCodeNullable(costsDamagesRecoveredAmount), hashCodeNullable(meetingsAttendedCode), hashCodeNullable(detentionTravelWaitingCostsAmount), hashCodeNullable(jrFormFillingAmount), hashCodeNullable(isEligibleClient), hashCodeNullable(courtLocationCode), hashCodeNullable(adviceTypeCode), hashCodeNullable(medicalReportsCount), hashCodeNullable(isIrcSurgery), hashCodeNullable(surgeryDate), hashCodeNullable(surgeryClientsCount), hashCodeNullable(surgeryMattersCount), hashCodeNullable(cmrhOralCount), hashCodeNullable(cmrhTelephoneCount), hashCodeNullable(aitHearingCentreCode), hashCodeNullable(isSubstantiveHearing), hashCodeNullable(hoInterview), hashCodeNullable(localAuthorityNumber), hashCodeNullable(submissionPeriod), hashCodeNullable(createdByUserId), hashCodeNullable(isAmended), hashCodeNullable(hasAssessment), hashCodeNullable(version), totalWarnings, feeCalculationResponse, hashCodeNullable(amendmentRequestedBy), hashCodeNullable(amendmentUserId), hashCodeNullable(amendmentReasonCode), validationMessages);
  }

  private static <T> int hashCodeNullable(JsonNullable<T> a) {
    if (a == null) {
      return 1;
    }
    return a.isPresent() ? Arrays.deepHashCode(new Object[]{a.get()}) : 31;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ClaimAmendmentPatch {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    submissionId: ").append(toIndentedString(submissionId)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    scheduleReference: ").append(toIndentedString(scheduleReference)).append("\n");
    sb.append("    lineNumber: ").append(toIndentedString(lineNumber)).append("\n");
    sb.append("    caseReferenceNumber: ").append(toIndentedString(caseReferenceNumber)).append("\n");
    sb.append("    uniqueFileNumber: ").append(toIndentedString(uniqueFileNumber)).append("\n");
    sb.append("    caseStartDate: ").append(toIndentedString(caseStartDate)).append("\n");
    sb.append("    caseConcludedDate: ").append(toIndentedString(caseConcludedDate)).append("\n");
    sb.append("    matterTypeCode: ").append(toIndentedString(matterTypeCode)).append("\n");
    sb.append("    crimeMatterTypeCode: ").append(toIndentedString(crimeMatterTypeCode)).append("\n");
    sb.append("    feeSchemeCode: ").append(toIndentedString(feeSchemeCode)).append("\n");
    sb.append("    feeCode: ").append(toIndentedString(feeCode)).append("\n");
    sb.append("    procurementAreaCode: ").append(toIndentedString(procurementAreaCode)).append("\n");
    sb.append("    accessPointCode: ").append(toIndentedString(accessPointCode)).append("\n");
    sb.append("    deliveryLocation: ").append(toIndentedString(deliveryLocation)).append("\n");
    sb.append("    representationOrderDate: ").append(toIndentedString(representationOrderDate)).append("\n");
    sb.append("    suspectsDefendantsCount: ").append(toIndentedString(suspectsDefendantsCount)).append("\n");
    sb.append("    policeStationCourtAttendancesCount: ").append(toIndentedString(policeStationCourtAttendancesCount)).append("\n");
    sb.append("    policeStationCourtPrisonId: ").append(toIndentedString(policeStationCourtPrisonId)).append("\n");
    sb.append("    dsccNumber: ").append(toIndentedString(dsccNumber)).append("\n");
    sb.append("    maatId: ").append(toIndentedString(maatId)).append("\n");
    sb.append("    prisonLawPriorApprovalNumber: ").append(toIndentedString(prisonLawPriorApprovalNumber)).append("\n");
    sb.append("    isDutySolicitor: ").append(toIndentedString(isDutySolicitor)).append("\n");
    sb.append("    isYouthCourt: ").append(toIndentedString(isYouthCourt)).append("\n");
    sb.append("    schemeId: ").append(toIndentedString(schemeId)).append("\n");
    sb.append("    mediationSessionsCount: ").append(toIndentedString(mediationSessionsCount)).append("\n");
    sb.append("    mediationTimeMinutes: ").append(toIndentedString(mediationTimeMinutes)).append("\n");
    sb.append("    outreachLocation: ").append(toIndentedString(outreachLocation)).append("\n");
    sb.append("    referralSource: ").append(toIndentedString(referralSource)).append("\n");
    sb.append("    clientForename: ").append(toIndentedString(clientForename)).append("\n");
    sb.append("    clientSurname: ").append(toIndentedString(clientSurname)).append("\n");
    sb.append("    clientDateOfBirth: ").append(toIndentedString(clientDateOfBirth)).append("\n");
    sb.append("    uniqueClientNumber: ").append(toIndentedString(uniqueClientNumber)).append("\n");
    sb.append("    clientPostcode: ").append(toIndentedString(clientPostcode)).append("\n");
    sb.append("    genderCode: ").append(toIndentedString(genderCode)).append("\n");
    sb.append("    ethnicityCode: ").append(toIndentedString(ethnicityCode)).append("\n");
    sb.append("    disabilityCode: ").append(toIndentedString(disabilityCode)).append("\n");
    sb.append("    isLegallyAided: ").append(toIndentedString(isLegallyAided)).append("\n");
    sb.append("    clientTypeCode: ").append(toIndentedString(clientTypeCode)).append("\n");
    sb.append("    homeOfficeClientNumber: ").append(toIndentedString(homeOfficeClientNumber)).append("\n");
    sb.append("    claReferenceNumber: ").append(toIndentedString(claReferenceNumber)).append("\n");
    sb.append("    claExemptionCode: ").append(toIndentedString(claExemptionCode)).append("\n");
    sb.append("    client2Forename: ").append(toIndentedString(client2Forename)).append("\n");
    sb.append("    client2Surname: ").append(toIndentedString(client2Surname)).append("\n");
    sb.append("    client2DateOfBirth: ").append(toIndentedString(client2DateOfBirth)).append("\n");
    sb.append("    client2Ucn: ").append(toIndentedString(client2Ucn)).append("\n");
    sb.append("    client2Postcode: ").append(toIndentedString(client2Postcode)).append("\n");
    sb.append("    client2GenderCode: ").append(toIndentedString(client2GenderCode)).append("\n");
    sb.append("    client2EthnicityCode: ").append(toIndentedString(client2EthnicityCode)).append("\n");
    sb.append("    client2DisabilityCode: ").append(toIndentedString(client2DisabilityCode)).append("\n");
    sb.append("    client2IsLegallyAided: ").append(toIndentedString(client2IsLegallyAided)).append("\n");
    sb.append("    caseId: ").append(toIndentedString(caseId)).append("\n");
    sb.append("    uniqueCaseId: ").append(toIndentedString(uniqueCaseId)).append("\n");
    sb.append("    caseStageCode: ").append(toIndentedString(caseStageCode)).append("\n");
    sb.append("    stageReachedCode: ").append(toIndentedString(stageReachedCode)).append("\n");
    sb.append("    standardFeeCategoryCode: ").append(toIndentedString(standardFeeCategoryCode)).append("\n");
    sb.append("    outcomeCode: ").append(toIndentedString(outcomeCode)).append("\n");
    sb.append("    designatedAccreditedRepresentativeCode: ").append(toIndentedString(designatedAccreditedRepresentativeCode)).append("\n");
    sb.append("    isPostalApplicationAccepted: ").append(toIndentedString(isPostalApplicationAccepted)).append("\n");
    sb.append("    isClient2PostalApplicationAccepted: ").append(toIndentedString(isClient2PostalApplicationAccepted)).append("\n");
    sb.append("    mentalHealthTribunalReference: ").append(toIndentedString(mentalHealthTribunalReference)).append("\n");
    sb.append("    isNrmAdvice: ").append(toIndentedString(isNrmAdvice)).append("\n");
    sb.append("    followOnWork: ").append(toIndentedString(followOnWork)).append("\n");
    sb.append("    transferDate: ").append(toIndentedString(transferDate)).append("\n");
    sb.append("    exemptionCriteriaSatisfied: ").append(toIndentedString(exemptionCriteriaSatisfied)).append("\n");
    sb.append("    exceptionalCaseFundingReference: ").append(toIndentedString(exceptionalCaseFundingReference)).append("\n");
    sb.append("    isLegacyCase: ").append(toIndentedString(isLegacyCase)).append("\n");
    sb.append("    adviceTime: ").append(toIndentedString(adviceTime)).append("\n");
    sb.append("    travelTime: ").append(toIndentedString(travelTime)).append("\n");
    sb.append("    waitingTime: ").append(toIndentedString(waitingTime)).append("\n");
    sb.append("    netProfitCostsAmount: ").append(toIndentedString(netProfitCostsAmount)).append("\n");
    sb.append("    netDisbursementAmount: ").append(toIndentedString(netDisbursementAmount)).append("\n");
    sb.append("    netCounselCostsAmount: ").append(toIndentedString(netCounselCostsAmount)).append("\n");
    sb.append("    disbursementsVatAmount: ").append(toIndentedString(disbursementsVatAmount)).append("\n");
    sb.append("    travelWaitingCostsAmount: ").append(toIndentedString(travelWaitingCostsAmount)).append("\n");
    sb.append("    netWaitingCostsAmount: ").append(toIndentedString(netWaitingCostsAmount)).append("\n");
    sb.append("    isVatApplicable: ").append(toIndentedString(isVatApplicable)).append("\n");
    sb.append("    isToleranceApplicable: ").append(toIndentedString(isToleranceApplicable)).append("\n");
    sb.append("    priorAuthorityReference: ").append(toIndentedString(priorAuthorityReference)).append("\n");
    sb.append("    isLondonRate: ").append(toIndentedString(isLondonRate)).append("\n");
    sb.append("    adjournedHearingFeeAmount: ").append(toIndentedString(adjournedHearingFeeAmount)).append("\n");
    sb.append("    isAdditionalTravelPayment: ").append(toIndentedString(isAdditionalTravelPayment)).append("\n");
    sb.append("    costsDamagesRecoveredAmount: ").append(toIndentedString(costsDamagesRecoveredAmount)).append("\n");
    sb.append("    meetingsAttendedCode: ").append(toIndentedString(meetingsAttendedCode)).append("\n");
    sb.append("    detentionTravelWaitingCostsAmount: ").append(toIndentedString(detentionTravelWaitingCostsAmount)).append("\n");
    sb.append("    jrFormFillingAmount: ").append(toIndentedString(jrFormFillingAmount)).append("\n");
    sb.append("    isEligibleClient: ").append(toIndentedString(isEligibleClient)).append("\n");
    sb.append("    courtLocationCode: ").append(toIndentedString(courtLocationCode)).append("\n");
    sb.append("    adviceTypeCode: ").append(toIndentedString(adviceTypeCode)).append("\n");
    sb.append("    medicalReportsCount: ").append(toIndentedString(medicalReportsCount)).append("\n");
    sb.append("    isIrcSurgery: ").append(toIndentedString(isIrcSurgery)).append("\n");
    sb.append("    surgeryDate: ").append(toIndentedString(surgeryDate)).append("\n");
    sb.append("    surgeryClientsCount: ").append(toIndentedString(surgeryClientsCount)).append("\n");
    sb.append("    surgeryMattersCount: ").append(toIndentedString(surgeryMattersCount)).append("\n");
    sb.append("    cmrhOralCount: ").append(toIndentedString(cmrhOralCount)).append("\n");
    sb.append("    cmrhTelephoneCount: ").append(toIndentedString(cmrhTelephoneCount)).append("\n");
    sb.append("    aitHearingCentreCode: ").append(toIndentedString(aitHearingCentreCode)).append("\n");
    sb.append("    isSubstantiveHearing: ").append(toIndentedString(isSubstantiveHearing)).append("\n");
    sb.append("    hoInterview: ").append(toIndentedString(hoInterview)).append("\n");
    sb.append("    localAuthorityNumber: ").append(toIndentedString(localAuthorityNumber)).append("\n");
    sb.append("    submissionPeriod: ").append(toIndentedString(submissionPeriod)).append("\n");
    sb.append("    createdByUserId: ").append(toIndentedString(createdByUserId)).append("\n");
    sb.append("    isAmended: ").append(toIndentedString(isAmended)).append("\n");
    sb.append("    hasAssessment: ").append(toIndentedString(hasAssessment)).append("\n");
    sb.append("    version: ").append(toIndentedString(version)).append("\n");
    sb.append("    totalWarnings: ").append(toIndentedString(totalWarnings)).append("\n");
    sb.append("    feeCalculationResponse: ").append(toIndentedString(feeCalculationResponse)).append("\n");
    sb.append("    amendmentRequestedBy: ").append(toIndentedString(amendmentRequestedBy)).append("\n");
    sb.append("    amendmentUserId: ").append(toIndentedString(amendmentUserId)).append("\n");
    sb.append("    amendmentReasonCode: ").append(toIndentedString(amendmentReasonCode)).append("\n");
    sb.append("    validationMessages: ").append(toIndentedString(validationMessages)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(@Nullable Object o) {
    return o == null ? "null" : o.toString().replace("\n", "\n    ");
  }
  
  public static class Builder {

    private ClaimAmendmentPatch instance;

    public Builder() {
      this(new ClaimAmendmentPatch());
    }

    protected Builder(ClaimAmendmentPatch instance) {
      this.instance = instance;
    }

    protected Builder copyOf(ClaimAmendmentPatch value) { 
      this.instance.setId(value.id);
      this.instance.setSubmissionId(value.submissionId);
      this.instance.setStatus(value.status);
      this.instance.setScheduleReference(value.scheduleReference);
      this.instance.setLineNumber(value.lineNumber);
      this.instance.setCaseReferenceNumber(value.caseReferenceNumber);
      this.instance.setUniqueFileNumber(value.uniqueFileNumber);
      this.instance.setCaseStartDate(value.caseStartDate);
      this.instance.setCaseConcludedDate(value.caseConcludedDate);
      this.instance.setMatterTypeCode(value.matterTypeCode);
      this.instance.setCrimeMatterTypeCode(value.crimeMatterTypeCode);
      this.instance.setFeeSchemeCode(value.feeSchemeCode);
      this.instance.setFeeCode(value.feeCode);
      this.instance.setProcurementAreaCode(value.procurementAreaCode);
      this.instance.setAccessPointCode(value.accessPointCode);
      this.instance.setDeliveryLocation(value.deliveryLocation);
      this.instance.setRepresentationOrderDate(value.representationOrderDate);
      this.instance.setSuspectsDefendantsCount(value.suspectsDefendantsCount);
      this.instance.setPoliceStationCourtAttendancesCount(value.policeStationCourtAttendancesCount);
      this.instance.setPoliceStationCourtPrisonId(value.policeStationCourtPrisonId);
      this.instance.setDsccNumber(value.dsccNumber);
      this.instance.setMaatId(value.maatId);
      this.instance.setPrisonLawPriorApprovalNumber(value.prisonLawPriorApprovalNumber);
      this.instance.setIsDutySolicitor(value.isDutySolicitor);
      this.instance.setIsYouthCourt(value.isYouthCourt);
      this.instance.setSchemeId(value.schemeId);
      this.instance.setMediationSessionsCount(value.mediationSessionsCount);
      this.instance.setMediationTimeMinutes(value.mediationTimeMinutes);
      this.instance.setOutreachLocation(value.outreachLocation);
      this.instance.setReferralSource(value.referralSource);
      this.instance.setClientForename(value.clientForename);
      this.instance.setClientSurname(value.clientSurname);
      this.instance.setClientDateOfBirth(value.clientDateOfBirth);
      this.instance.setUniqueClientNumber(value.uniqueClientNumber);
      this.instance.setClientPostcode(value.clientPostcode);
      this.instance.setGenderCode(value.genderCode);
      this.instance.setEthnicityCode(value.ethnicityCode);
      this.instance.setDisabilityCode(value.disabilityCode);
      this.instance.setIsLegallyAided(value.isLegallyAided);
      this.instance.setClientTypeCode(value.clientTypeCode);
      this.instance.setHomeOfficeClientNumber(value.homeOfficeClientNumber);
      this.instance.setClaReferenceNumber(value.claReferenceNumber);
      this.instance.setClaExemptionCode(value.claExemptionCode);
      this.instance.setClient2Forename(value.client2Forename);
      this.instance.setClient2Surname(value.client2Surname);
      this.instance.setClient2DateOfBirth(value.client2DateOfBirth);
      this.instance.setClient2Ucn(value.client2Ucn);
      this.instance.setClient2Postcode(value.client2Postcode);
      this.instance.setClient2GenderCode(value.client2GenderCode);
      this.instance.setClient2EthnicityCode(value.client2EthnicityCode);
      this.instance.setClient2DisabilityCode(value.client2DisabilityCode);
      this.instance.setClient2IsLegallyAided(value.client2IsLegallyAided);
      this.instance.setCaseId(value.caseId);
      this.instance.setUniqueCaseId(value.uniqueCaseId);
      this.instance.setCaseStageCode(value.caseStageCode);
      this.instance.setStageReachedCode(value.stageReachedCode);
      this.instance.setStandardFeeCategoryCode(value.standardFeeCategoryCode);
      this.instance.setOutcomeCode(value.outcomeCode);
      this.instance.setDesignatedAccreditedRepresentativeCode(value.designatedAccreditedRepresentativeCode);
      this.instance.setIsPostalApplicationAccepted(value.isPostalApplicationAccepted);
      this.instance.setIsClient2PostalApplicationAccepted(value.isClient2PostalApplicationAccepted);
      this.instance.setMentalHealthTribunalReference(value.mentalHealthTribunalReference);
      this.instance.setIsNrmAdvice(value.isNrmAdvice);
      this.instance.setFollowOnWork(value.followOnWork);
      this.instance.setTransferDate(value.transferDate);
      this.instance.setExemptionCriteriaSatisfied(value.exemptionCriteriaSatisfied);
      this.instance.setExceptionalCaseFundingReference(value.exceptionalCaseFundingReference);
      this.instance.setIsLegacyCase(value.isLegacyCase);
      this.instance.setAdviceTime(value.adviceTime);
      this.instance.setTravelTime(value.travelTime);
      this.instance.setWaitingTime(value.waitingTime);
      this.instance.setNetProfitCostsAmount(value.netProfitCostsAmount);
      this.instance.setNetDisbursementAmount(value.netDisbursementAmount);
      this.instance.setNetCounselCostsAmount(value.netCounselCostsAmount);
      this.instance.setDisbursementsVatAmount(value.disbursementsVatAmount);
      this.instance.setTravelWaitingCostsAmount(value.travelWaitingCostsAmount);
      this.instance.setNetWaitingCostsAmount(value.netWaitingCostsAmount);
      this.instance.setIsVatApplicable(value.isVatApplicable);
      this.instance.setIsToleranceApplicable(value.isToleranceApplicable);
      this.instance.setPriorAuthorityReference(value.priorAuthorityReference);
      this.instance.setIsLondonRate(value.isLondonRate);
      this.instance.setAdjournedHearingFeeAmount(value.adjournedHearingFeeAmount);
      this.instance.setIsAdditionalTravelPayment(value.isAdditionalTravelPayment);
      this.instance.setCostsDamagesRecoveredAmount(value.costsDamagesRecoveredAmount);
      this.instance.setMeetingsAttendedCode(value.meetingsAttendedCode);
      this.instance.setDetentionTravelWaitingCostsAmount(value.detentionTravelWaitingCostsAmount);
      this.instance.setJrFormFillingAmount(value.jrFormFillingAmount);
      this.instance.setIsEligibleClient(value.isEligibleClient);
      this.instance.setCourtLocationCode(value.courtLocationCode);
      this.instance.setAdviceTypeCode(value.adviceTypeCode);
      this.instance.setMedicalReportsCount(value.medicalReportsCount);
      this.instance.setIsIrcSurgery(value.isIrcSurgery);
      this.instance.setSurgeryDate(value.surgeryDate);
      this.instance.setSurgeryClientsCount(value.surgeryClientsCount);
      this.instance.setSurgeryMattersCount(value.surgeryMattersCount);
      this.instance.setCmrhOralCount(value.cmrhOralCount);
      this.instance.setCmrhTelephoneCount(value.cmrhTelephoneCount);
      this.instance.setAitHearingCentreCode(value.aitHearingCentreCode);
      this.instance.setIsSubstantiveHearing(value.isSubstantiveHearing);
      this.instance.setHoInterview(value.hoInterview);
      this.instance.setLocalAuthorityNumber(value.localAuthorityNumber);
      this.instance.setSubmissionPeriod(value.submissionPeriod);
      this.instance.setCreatedByUserId(value.createdByUserId);
      this.instance.setIsAmended(value.isAmended);
      this.instance.setHasAssessment(value.hasAssessment);
      this.instance.setVersion(value.version);
      this.instance.setTotalWarnings(value.totalWarnings);
      this.instance.setFeeCalculationResponse(value.feeCalculationResponse);
      this.instance.setAmendmentRequestedBy(value.amendmentRequestedBy);
      this.instance.setAmendmentUserId(value.amendmentUserId);
      this.instance.setAmendmentReasonCode(value.amendmentReasonCode);
      this.instance.setValidationMessages(value.validationMessages);
      return this;
    }

    public ClaimAmendmentPatch.Builder id(String id) {
      this.instance.id(id);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder id(JsonNullable<String> id) {
      this.instance.id = id;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder submissionId(String submissionId) {
      this.instance.submissionId(submissionId);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder submissionId(JsonNullable<String> submissionId) {
      this.instance.submissionId = submissionId;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder status(ClaimStatus status) {
      this.instance.status(status);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder scheduleReference(String scheduleReference) {
      this.instance.scheduleReference(scheduleReference);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder scheduleReference(JsonNullable<String> scheduleReference) {
      this.instance.scheduleReference = scheduleReference;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder lineNumber(Integer lineNumber) {
      this.instance.lineNumber(lineNumber);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder lineNumber(JsonNullable<Integer> lineNumber) {
      this.instance.lineNumber = lineNumber;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder caseReferenceNumber(String caseReferenceNumber) {
      this.instance.caseReferenceNumber(caseReferenceNumber);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder caseReferenceNumber(JsonNullable<String> caseReferenceNumber) {
      this.instance.caseReferenceNumber = caseReferenceNumber;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder uniqueFileNumber(String uniqueFileNumber) {
      this.instance.uniqueFileNumber(uniqueFileNumber);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder uniqueFileNumber(JsonNullable<String> uniqueFileNumber) {
      this.instance.uniqueFileNumber = uniqueFileNumber;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder caseStartDate(String caseStartDate) {
      this.instance.caseStartDate(caseStartDate);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder caseStartDate(JsonNullable<String> caseStartDate) {
      this.instance.caseStartDate = caseStartDate;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder caseConcludedDate(String caseConcludedDate) {
      this.instance.caseConcludedDate(caseConcludedDate);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder caseConcludedDate(JsonNullable<String> caseConcludedDate) {
      this.instance.caseConcludedDate = caseConcludedDate;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder matterTypeCode(String matterTypeCode) {
      this.instance.matterTypeCode(matterTypeCode);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder matterTypeCode(JsonNullable<String> matterTypeCode) {
      this.instance.matterTypeCode = matterTypeCode;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder crimeMatterTypeCode(String crimeMatterTypeCode) {
      this.instance.crimeMatterTypeCode(crimeMatterTypeCode);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder crimeMatterTypeCode(JsonNullable<String> crimeMatterTypeCode) {
      this.instance.crimeMatterTypeCode = crimeMatterTypeCode;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder feeSchemeCode(String feeSchemeCode) {
      this.instance.feeSchemeCode(feeSchemeCode);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder feeSchemeCode(JsonNullable<String> feeSchemeCode) {
      this.instance.feeSchemeCode = feeSchemeCode;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder feeCode(String feeCode) {
      this.instance.feeCode(feeCode);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder feeCode(JsonNullable<String> feeCode) {
      this.instance.feeCode = feeCode;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder procurementAreaCode(String procurementAreaCode) {
      this.instance.procurementAreaCode(procurementAreaCode);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder procurementAreaCode(JsonNullable<String> procurementAreaCode) {
      this.instance.procurementAreaCode = procurementAreaCode;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder accessPointCode(String accessPointCode) {
      this.instance.accessPointCode(accessPointCode);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder accessPointCode(JsonNullable<String> accessPointCode) {
      this.instance.accessPointCode = accessPointCode;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder deliveryLocation(String deliveryLocation) {
      this.instance.deliveryLocation(deliveryLocation);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder deliveryLocation(JsonNullable<String> deliveryLocation) {
      this.instance.deliveryLocation = deliveryLocation;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder representationOrderDate(String representationOrderDate) {
      this.instance.representationOrderDate(representationOrderDate);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder representationOrderDate(JsonNullable<String> representationOrderDate) {
      this.instance.representationOrderDate = representationOrderDate;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder suspectsDefendantsCount(Integer suspectsDefendantsCount) {
      this.instance.suspectsDefendantsCount(suspectsDefendantsCount);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder suspectsDefendantsCount(JsonNullable<Integer> suspectsDefendantsCount) {
      this.instance.suspectsDefendantsCount = suspectsDefendantsCount;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder policeStationCourtAttendancesCount(Integer policeStationCourtAttendancesCount) {
      this.instance.policeStationCourtAttendancesCount(policeStationCourtAttendancesCount);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder policeStationCourtAttendancesCount(JsonNullable<Integer> policeStationCourtAttendancesCount) {
      this.instance.policeStationCourtAttendancesCount = policeStationCourtAttendancesCount;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder policeStationCourtPrisonId(String policeStationCourtPrisonId) {
      this.instance.policeStationCourtPrisonId(policeStationCourtPrisonId);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder policeStationCourtPrisonId(JsonNullable<String> policeStationCourtPrisonId) {
      this.instance.policeStationCourtPrisonId = policeStationCourtPrisonId;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder dsccNumber(String dsccNumber) {
      this.instance.dsccNumber(dsccNumber);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder dsccNumber(JsonNullable<String> dsccNumber) {
      this.instance.dsccNumber = dsccNumber;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder maatId(String maatId) {
      this.instance.maatId(maatId);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder maatId(JsonNullable<String> maatId) {
      this.instance.maatId = maatId;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder prisonLawPriorApprovalNumber(String prisonLawPriorApprovalNumber) {
      this.instance.prisonLawPriorApprovalNumber(prisonLawPriorApprovalNumber);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder prisonLawPriorApprovalNumber(JsonNullable<String> prisonLawPriorApprovalNumber) {
      this.instance.prisonLawPriorApprovalNumber = prisonLawPriorApprovalNumber;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isDutySolicitor(Boolean isDutySolicitor) {
      this.instance.isDutySolicitor(isDutySolicitor);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isDutySolicitor(JsonNullable<Boolean> isDutySolicitor) {
      this.instance.isDutySolicitor = isDutySolicitor;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isYouthCourt(Boolean isYouthCourt) {
      this.instance.isYouthCourt(isYouthCourt);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isYouthCourt(JsonNullable<Boolean> isYouthCourt) {
      this.instance.isYouthCourt = isYouthCourt;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder schemeId(String schemeId) {
      this.instance.schemeId(schemeId);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder schemeId(JsonNullable<String> schemeId) {
      this.instance.schemeId = schemeId;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder mediationSessionsCount(Integer mediationSessionsCount) {
      this.instance.mediationSessionsCount(mediationSessionsCount);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder mediationSessionsCount(JsonNullable<Integer> mediationSessionsCount) {
      this.instance.mediationSessionsCount = mediationSessionsCount;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder mediationTimeMinutes(Integer mediationTimeMinutes) {
      this.instance.mediationTimeMinutes(mediationTimeMinutes);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder mediationTimeMinutes(JsonNullable<Integer> mediationTimeMinutes) {
      this.instance.mediationTimeMinutes = mediationTimeMinutes;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder outreachLocation(String outreachLocation) {
      this.instance.outreachLocation(outreachLocation);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder outreachLocation(JsonNullable<String> outreachLocation) {
      this.instance.outreachLocation = outreachLocation;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder referralSource(String referralSource) {
      this.instance.referralSource(referralSource);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder referralSource(JsonNullable<String> referralSource) {
      this.instance.referralSource = referralSource;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder clientForename(String clientForename) {
      this.instance.clientForename(clientForename);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder clientForename(JsonNullable<String> clientForename) {
      this.instance.clientForename = clientForename;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder clientSurname(String clientSurname) {
      this.instance.clientSurname(clientSurname);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder clientSurname(JsonNullable<String> clientSurname) {
      this.instance.clientSurname = clientSurname;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder clientDateOfBirth(String clientDateOfBirth) {
      this.instance.clientDateOfBirth(clientDateOfBirth);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder clientDateOfBirth(JsonNullable<String> clientDateOfBirth) {
      this.instance.clientDateOfBirth = clientDateOfBirth;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder uniqueClientNumber(String uniqueClientNumber) {
      this.instance.uniqueClientNumber(uniqueClientNumber);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder uniqueClientNumber(JsonNullable<String> uniqueClientNumber) {
      this.instance.uniqueClientNumber = uniqueClientNumber;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder clientPostcode(String clientPostcode) {
      this.instance.clientPostcode(clientPostcode);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder clientPostcode(JsonNullable<String> clientPostcode) {
      this.instance.clientPostcode = clientPostcode;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder genderCode(String genderCode) {
      this.instance.genderCode(genderCode);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder genderCode(JsonNullable<String> genderCode) {
      this.instance.genderCode = genderCode;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder ethnicityCode(String ethnicityCode) {
      this.instance.ethnicityCode(ethnicityCode);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder ethnicityCode(JsonNullable<String> ethnicityCode) {
      this.instance.ethnicityCode = ethnicityCode;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder disabilityCode(String disabilityCode) {
      this.instance.disabilityCode(disabilityCode);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder disabilityCode(JsonNullable<String> disabilityCode) {
      this.instance.disabilityCode = disabilityCode;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isLegallyAided(Boolean isLegallyAided) {
      this.instance.isLegallyAided(isLegallyAided);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isLegallyAided(JsonNullable<Boolean> isLegallyAided) {
      this.instance.isLegallyAided = isLegallyAided;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder clientTypeCode(String clientTypeCode) {
      this.instance.clientTypeCode(clientTypeCode);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder clientTypeCode(JsonNullable<String> clientTypeCode) {
      this.instance.clientTypeCode = clientTypeCode;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder homeOfficeClientNumber(String homeOfficeClientNumber) {
      this.instance.homeOfficeClientNumber(homeOfficeClientNumber);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder homeOfficeClientNumber(JsonNullable<String> homeOfficeClientNumber) {
      this.instance.homeOfficeClientNumber = homeOfficeClientNumber;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder claReferenceNumber(String claReferenceNumber) {
      this.instance.claReferenceNumber(claReferenceNumber);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder claReferenceNumber(JsonNullable<String> claReferenceNumber) {
      this.instance.claReferenceNumber = claReferenceNumber;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder claExemptionCode(String claExemptionCode) {
      this.instance.claExemptionCode(claExemptionCode);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder claExemptionCode(JsonNullable<String> claExemptionCode) {
      this.instance.claExemptionCode = claExemptionCode;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder client2Forename(String client2Forename) {
      this.instance.client2Forename(client2Forename);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder client2Forename(JsonNullable<String> client2Forename) {
      this.instance.client2Forename = client2Forename;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder client2Surname(String client2Surname) {
      this.instance.client2Surname(client2Surname);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder client2Surname(JsonNullable<String> client2Surname) {
      this.instance.client2Surname = client2Surname;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder client2DateOfBirth(String client2DateOfBirth) {
      this.instance.client2DateOfBirth(client2DateOfBirth);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder client2DateOfBirth(JsonNullable<String> client2DateOfBirth) {
      this.instance.client2DateOfBirth = client2DateOfBirth;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder client2Ucn(String client2Ucn) {
      this.instance.client2Ucn(client2Ucn);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder client2Ucn(JsonNullable<String> client2Ucn) {
      this.instance.client2Ucn = client2Ucn;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder client2Postcode(String client2Postcode) {
      this.instance.client2Postcode(client2Postcode);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder client2Postcode(JsonNullable<String> client2Postcode) {
      this.instance.client2Postcode = client2Postcode;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder client2GenderCode(String client2GenderCode) {
      this.instance.client2GenderCode(client2GenderCode);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder client2GenderCode(JsonNullable<String> client2GenderCode) {
      this.instance.client2GenderCode = client2GenderCode;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder client2EthnicityCode(String client2EthnicityCode) {
      this.instance.client2EthnicityCode(client2EthnicityCode);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder client2EthnicityCode(JsonNullable<String> client2EthnicityCode) {
      this.instance.client2EthnicityCode = client2EthnicityCode;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder client2DisabilityCode(String client2DisabilityCode) {
      this.instance.client2DisabilityCode(client2DisabilityCode);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder client2DisabilityCode(JsonNullable<String> client2DisabilityCode) {
      this.instance.client2DisabilityCode = client2DisabilityCode;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder client2IsLegallyAided(Boolean client2IsLegallyAided) {
      this.instance.client2IsLegallyAided(client2IsLegallyAided);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder client2IsLegallyAided(JsonNullable<Boolean> client2IsLegallyAided) {
      this.instance.client2IsLegallyAided = client2IsLegallyAided;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder caseId(String caseId) {
      this.instance.caseId(caseId);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder caseId(JsonNullable<String> caseId) {
      this.instance.caseId = caseId;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder uniqueCaseId(String uniqueCaseId) {
      this.instance.uniqueCaseId(uniqueCaseId);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder uniqueCaseId(JsonNullable<String> uniqueCaseId) {
      this.instance.uniqueCaseId = uniqueCaseId;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder caseStageCode(String caseStageCode) {
      this.instance.caseStageCode(caseStageCode);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder caseStageCode(JsonNullable<String> caseStageCode) {
      this.instance.caseStageCode = caseStageCode;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder stageReachedCode(String stageReachedCode) {
      this.instance.stageReachedCode(stageReachedCode);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder stageReachedCode(JsonNullable<String> stageReachedCode) {
      this.instance.stageReachedCode = stageReachedCode;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder standardFeeCategoryCode(String standardFeeCategoryCode) {
      this.instance.standardFeeCategoryCode(standardFeeCategoryCode);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder standardFeeCategoryCode(JsonNullable<String> standardFeeCategoryCode) {
      this.instance.standardFeeCategoryCode = standardFeeCategoryCode;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder outcomeCode(String outcomeCode) {
      this.instance.outcomeCode(outcomeCode);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder outcomeCode(JsonNullable<String> outcomeCode) {
      this.instance.outcomeCode = outcomeCode;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder designatedAccreditedRepresentativeCode(String designatedAccreditedRepresentativeCode) {
      this.instance.designatedAccreditedRepresentativeCode(designatedAccreditedRepresentativeCode);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder designatedAccreditedRepresentativeCode(JsonNullable<String> designatedAccreditedRepresentativeCode) {
      this.instance.designatedAccreditedRepresentativeCode = designatedAccreditedRepresentativeCode;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isPostalApplicationAccepted(Boolean isPostalApplicationAccepted) {
      this.instance.isPostalApplicationAccepted(isPostalApplicationAccepted);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isPostalApplicationAccepted(JsonNullable<Boolean> isPostalApplicationAccepted) {
      this.instance.isPostalApplicationAccepted = isPostalApplicationAccepted;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isClient2PostalApplicationAccepted(Boolean isClient2PostalApplicationAccepted) {
      this.instance.isClient2PostalApplicationAccepted(isClient2PostalApplicationAccepted);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isClient2PostalApplicationAccepted(JsonNullable<Boolean> isClient2PostalApplicationAccepted) {
      this.instance.isClient2PostalApplicationAccepted = isClient2PostalApplicationAccepted;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder mentalHealthTribunalReference(String mentalHealthTribunalReference) {
      this.instance.mentalHealthTribunalReference(mentalHealthTribunalReference);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder mentalHealthTribunalReference(JsonNullable<String> mentalHealthTribunalReference) {
      this.instance.mentalHealthTribunalReference = mentalHealthTribunalReference;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isNrmAdvice(Boolean isNrmAdvice) {
      this.instance.isNrmAdvice(isNrmAdvice);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isNrmAdvice(JsonNullable<Boolean> isNrmAdvice) {
      this.instance.isNrmAdvice = isNrmAdvice;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder followOnWork(String followOnWork) {
      this.instance.followOnWork(followOnWork);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder followOnWork(JsonNullable<String> followOnWork) {
      this.instance.followOnWork = followOnWork;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder transferDate(String transferDate) {
      this.instance.transferDate(transferDate);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder transferDate(JsonNullable<String> transferDate) {
      this.instance.transferDate = transferDate;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder exemptionCriteriaSatisfied(String exemptionCriteriaSatisfied) {
      this.instance.exemptionCriteriaSatisfied(exemptionCriteriaSatisfied);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder exemptionCriteriaSatisfied(JsonNullable<String> exemptionCriteriaSatisfied) {
      this.instance.exemptionCriteriaSatisfied = exemptionCriteriaSatisfied;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder exceptionalCaseFundingReference(String exceptionalCaseFundingReference) {
      this.instance.exceptionalCaseFundingReference(exceptionalCaseFundingReference);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder exceptionalCaseFundingReference(JsonNullable<String> exceptionalCaseFundingReference) {
      this.instance.exceptionalCaseFundingReference = exceptionalCaseFundingReference;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isLegacyCase(Boolean isLegacyCase) {
      this.instance.isLegacyCase(isLegacyCase);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isLegacyCase(JsonNullable<Boolean> isLegacyCase) {
      this.instance.isLegacyCase = isLegacyCase;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder adviceTime(Integer adviceTime) {
      this.instance.adviceTime(adviceTime);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder adviceTime(JsonNullable<Integer> adviceTime) {
      this.instance.adviceTime = adviceTime;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder travelTime(Integer travelTime) {
      this.instance.travelTime(travelTime);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder travelTime(JsonNullable<Integer> travelTime) {
      this.instance.travelTime = travelTime;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder waitingTime(Integer waitingTime) {
      this.instance.waitingTime(waitingTime);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder waitingTime(JsonNullable<Integer> waitingTime) {
      this.instance.waitingTime = waitingTime;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder netProfitCostsAmount(BigDecimal netProfitCostsAmount) {
      this.instance.netProfitCostsAmount(netProfitCostsAmount);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder netProfitCostsAmount(JsonNullable<BigDecimal> netProfitCostsAmount) {
      this.instance.netProfitCostsAmount = netProfitCostsAmount;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder netDisbursementAmount(BigDecimal netDisbursementAmount) {
      this.instance.netDisbursementAmount(netDisbursementAmount);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder netDisbursementAmount(JsonNullable<BigDecimal> netDisbursementAmount) {
      this.instance.netDisbursementAmount = netDisbursementAmount;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder netCounselCostsAmount(BigDecimal netCounselCostsAmount) {
      this.instance.netCounselCostsAmount(netCounselCostsAmount);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder netCounselCostsAmount(JsonNullable<BigDecimal> netCounselCostsAmount) {
      this.instance.netCounselCostsAmount = netCounselCostsAmount;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder disbursementsVatAmount(BigDecimal disbursementsVatAmount) {
      this.instance.disbursementsVatAmount(disbursementsVatAmount);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder disbursementsVatAmount(JsonNullable<BigDecimal> disbursementsVatAmount) {
      this.instance.disbursementsVatAmount = disbursementsVatAmount;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder travelWaitingCostsAmount(BigDecimal travelWaitingCostsAmount) {
      this.instance.travelWaitingCostsAmount(travelWaitingCostsAmount);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder travelWaitingCostsAmount(JsonNullable<BigDecimal> travelWaitingCostsAmount) {
      this.instance.travelWaitingCostsAmount = travelWaitingCostsAmount;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder netWaitingCostsAmount(BigDecimal netWaitingCostsAmount) {
      this.instance.netWaitingCostsAmount(netWaitingCostsAmount);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder netWaitingCostsAmount(JsonNullable<BigDecimal> netWaitingCostsAmount) {
      this.instance.netWaitingCostsAmount = netWaitingCostsAmount;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isVatApplicable(Boolean isVatApplicable) {
      this.instance.isVatApplicable(isVatApplicable);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isVatApplicable(JsonNullable<Boolean> isVatApplicable) {
      this.instance.isVatApplicable = isVatApplicable;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isToleranceApplicable(Boolean isToleranceApplicable) {
      this.instance.isToleranceApplicable(isToleranceApplicable);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isToleranceApplicable(JsonNullable<Boolean> isToleranceApplicable) {
      this.instance.isToleranceApplicable = isToleranceApplicable;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder priorAuthorityReference(String priorAuthorityReference) {
      this.instance.priorAuthorityReference(priorAuthorityReference);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder priorAuthorityReference(JsonNullable<String> priorAuthorityReference) {
      this.instance.priorAuthorityReference = priorAuthorityReference;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isLondonRate(Boolean isLondonRate) {
      this.instance.isLondonRate(isLondonRate);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isLondonRate(JsonNullable<Boolean> isLondonRate) {
      this.instance.isLondonRate = isLondonRate;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder adjournedHearingFeeAmount(Integer adjournedHearingFeeAmount) {
      this.instance.adjournedHearingFeeAmount(adjournedHearingFeeAmount);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder adjournedHearingFeeAmount(JsonNullable<Integer> adjournedHearingFeeAmount) {
      this.instance.adjournedHearingFeeAmount = adjournedHearingFeeAmount;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isAdditionalTravelPayment(Boolean isAdditionalTravelPayment) {
      this.instance.isAdditionalTravelPayment(isAdditionalTravelPayment);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isAdditionalTravelPayment(JsonNullable<Boolean> isAdditionalTravelPayment) {
      this.instance.isAdditionalTravelPayment = isAdditionalTravelPayment;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder costsDamagesRecoveredAmount(BigDecimal costsDamagesRecoveredAmount) {
      this.instance.costsDamagesRecoveredAmount(costsDamagesRecoveredAmount);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder costsDamagesRecoveredAmount(JsonNullable<BigDecimal> costsDamagesRecoveredAmount) {
      this.instance.costsDamagesRecoveredAmount = costsDamagesRecoveredAmount;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder meetingsAttendedCode(String meetingsAttendedCode) {
      this.instance.meetingsAttendedCode(meetingsAttendedCode);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder meetingsAttendedCode(JsonNullable<String> meetingsAttendedCode) {
      this.instance.meetingsAttendedCode = meetingsAttendedCode;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder detentionTravelWaitingCostsAmount(BigDecimal detentionTravelWaitingCostsAmount) {
      this.instance.detentionTravelWaitingCostsAmount(detentionTravelWaitingCostsAmount);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder detentionTravelWaitingCostsAmount(JsonNullable<BigDecimal> detentionTravelWaitingCostsAmount) {
      this.instance.detentionTravelWaitingCostsAmount = detentionTravelWaitingCostsAmount;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder jrFormFillingAmount(BigDecimal jrFormFillingAmount) {
      this.instance.jrFormFillingAmount(jrFormFillingAmount);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder jrFormFillingAmount(JsonNullable<BigDecimal> jrFormFillingAmount) {
      this.instance.jrFormFillingAmount = jrFormFillingAmount;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isEligibleClient(Boolean isEligibleClient) {
      this.instance.isEligibleClient(isEligibleClient);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isEligibleClient(JsonNullable<Boolean> isEligibleClient) {
      this.instance.isEligibleClient = isEligibleClient;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder courtLocationCode(String courtLocationCode) {
      this.instance.courtLocationCode(courtLocationCode);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder courtLocationCode(JsonNullable<String> courtLocationCode) {
      this.instance.courtLocationCode = courtLocationCode;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder adviceTypeCode(String adviceTypeCode) {
      this.instance.adviceTypeCode(adviceTypeCode);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder adviceTypeCode(JsonNullable<String> adviceTypeCode) {
      this.instance.adviceTypeCode = adviceTypeCode;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder medicalReportsCount(Integer medicalReportsCount) {
      this.instance.medicalReportsCount(medicalReportsCount);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder medicalReportsCount(JsonNullable<Integer> medicalReportsCount) {
      this.instance.medicalReportsCount = medicalReportsCount;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isIrcSurgery(Boolean isIrcSurgery) {
      this.instance.isIrcSurgery(isIrcSurgery);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isIrcSurgery(JsonNullable<Boolean> isIrcSurgery) {
      this.instance.isIrcSurgery = isIrcSurgery;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder surgeryDate(String surgeryDate) {
      this.instance.surgeryDate(surgeryDate);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder surgeryDate(JsonNullable<String> surgeryDate) {
      this.instance.surgeryDate = surgeryDate;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder surgeryClientsCount(Integer surgeryClientsCount) {
      this.instance.surgeryClientsCount(surgeryClientsCount);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder surgeryClientsCount(JsonNullable<Integer> surgeryClientsCount) {
      this.instance.surgeryClientsCount = surgeryClientsCount;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder surgeryMattersCount(Integer surgeryMattersCount) {
      this.instance.surgeryMattersCount(surgeryMattersCount);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder surgeryMattersCount(JsonNullable<Integer> surgeryMattersCount) {
      this.instance.surgeryMattersCount = surgeryMattersCount;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder cmrhOralCount(Integer cmrhOralCount) {
      this.instance.cmrhOralCount(cmrhOralCount);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder cmrhOralCount(JsonNullable<Integer> cmrhOralCount) {
      this.instance.cmrhOralCount = cmrhOralCount;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder cmrhTelephoneCount(Integer cmrhTelephoneCount) {
      this.instance.cmrhTelephoneCount(cmrhTelephoneCount);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder cmrhTelephoneCount(JsonNullable<Integer> cmrhTelephoneCount) {
      this.instance.cmrhTelephoneCount = cmrhTelephoneCount;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder aitHearingCentreCode(String aitHearingCentreCode) {
      this.instance.aitHearingCentreCode(aitHearingCentreCode);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder aitHearingCentreCode(JsonNullable<String> aitHearingCentreCode) {
      this.instance.aitHearingCentreCode = aitHearingCentreCode;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isSubstantiveHearing(Boolean isSubstantiveHearing) {
      this.instance.isSubstantiveHearing(isSubstantiveHearing);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isSubstantiveHearing(JsonNullable<Boolean> isSubstantiveHearing) {
      this.instance.isSubstantiveHearing = isSubstantiveHearing;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder hoInterview(Integer hoInterview) {
      this.instance.hoInterview(hoInterview);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder hoInterview(JsonNullable<Integer> hoInterview) {
      this.instance.hoInterview = hoInterview;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder localAuthorityNumber(String localAuthorityNumber) {
      this.instance.localAuthorityNumber(localAuthorityNumber);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder localAuthorityNumber(JsonNullable<String> localAuthorityNumber) {
      this.instance.localAuthorityNumber = localAuthorityNumber;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder submissionPeriod(String submissionPeriod) {
      this.instance.submissionPeriod(submissionPeriod);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder submissionPeriod(JsonNullable<String> submissionPeriod) {
      this.instance.submissionPeriod = submissionPeriod;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder createdByUserId(String createdByUserId) {
      this.instance.createdByUserId(createdByUserId);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder createdByUserId(JsonNullable<String> createdByUserId) {
      this.instance.createdByUserId = createdByUserId;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isAmended(Boolean isAmended) {
      this.instance.isAmended(isAmended);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder isAmended(JsonNullable<Boolean> isAmended) {
      this.instance.isAmended = isAmended;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder hasAssessment(Boolean hasAssessment) {
      this.instance.hasAssessment(hasAssessment);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder hasAssessment(JsonNullable<Boolean> hasAssessment) {
      this.instance.hasAssessment = hasAssessment;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder version(Long version) {
      this.instance.version(version);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder version(JsonNullable<Long> version) {
      this.instance.version = version;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder totalWarnings(Integer totalWarnings) {
      this.instance.totalWarnings(totalWarnings);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder feeCalculationResponse(FeeCalculationPatch feeCalculationResponse) {
      this.instance.feeCalculationResponse(feeCalculationResponse);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder amendmentRequestedBy(String amendmentRequestedBy) {
      this.instance.amendmentRequestedBy(amendmentRequestedBy);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder amendmentRequestedBy(JsonNullable<String> amendmentRequestedBy) {
      this.instance.amendmentRequestedBy = amendmentRequestedBy;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder amendmentUserId(UUID amendmentUserId) {
      this.instance.amendmentUserId(amendmentUserId);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder amendmentUserId(JsonNullable<UUID> amendmentUserId) {
      this.instance.amendmentUserId = amendmentUserId;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder amendmentReasonCode(String amendmentReasonCode) {
      this.instance.amendmentReasonCode(amendmentReasonCode);
      return this;
    }
    
    public ClaimAmendmentPatch.Builder amendmentReasonCode(JsonNullable<String> amendmentReasonCode) {
      this.instance.amendmentReasonCode = amendmentReasonCode;
      return this;
    }
    
    public ClaimAmendmentPatch.Builder validationMessages(List<ValidationMessagePatch> validationMessages) {
      this.instance.validationMessages(validationMessages);
      return this;
    }
    
    /**
    * returns a built ClaimAmendmentPatch instance.
    *
    * The builder is not reusable (NullPointerException)
    */
    public ClaimAmendmentPatch build() {
      try {
        return this.instance;
      } finally {
        // ensure that this.instance is not reused
        this.instance = null;
      }
    }

    @Override
    public String toString() {
      return getClass() + "=(" + instance + ")";
    }
  }

  /**
  * Create a builder with no initialized field (except for the default values).
  */
  public static ClaimAmendmentPatch.Builder builder() {
    return new ClaimAmendmentPatch.Builder();
  }

  /**
  * Create a builder with a shallow copy of this instance.
  */
  public ClaimAmendmentPatch.Builder toBuilder() {
    ClaimAmendmentPatch.Builder builder = new ClaimAmendmentPatch.Builder();
    return builder.copyOf(this);
  }

}

