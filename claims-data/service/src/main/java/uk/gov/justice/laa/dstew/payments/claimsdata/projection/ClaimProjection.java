package uk.gov.justice.laa.dstew.payments.claimsdata.projection;

import java.math.BigDecimal;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationPatch;

/** Claim Projection. */
public interface ClaimProjection {
  // Claim fields

  String getId();

  String getSubmissionId();

  ClaimStatus getStatus();

  String getScheduleReference();

  Integer getLineNumber();

  String getCaseReferenceNumber();

  String getUniqueFileNumber();

  String getUniqueClientNumber();

  String getUniqueCaseId();

  String getFeeCode();

  String getFeeSchemeCode();

  String getMatterTypeCode();

  String getCrimeMatterTypeCode();

  String getClientForename();

  String getClientSurname();

  String getClientDateOfBirth();

  BigDecimal getNetProfitCostsAmount();

  BigDecimal getNetDisbursementAmount();

  BigDecimal getNetCounselCostsAmount();

  BigDecimal getDisbursementsVatAmount();

  BigDecimal getTravelWaitingCostsAmount();

  BigDecimal getNetWaitingCostsAmount();

  BigDecimal getCostsDamagesRecoveredAmount();

  Boolean getIsVatApplicable();

  Boolean getIsToleranceApplicable();

  String getSubmissionPeriod();

  String getCreatedByUserId();

  Boolean getIsAmended();

  Boolean getHasAssessment();

  Long getVersion();

  Integer getTotalWarnings();

  FeeCalculationPatch getFeeCalculationResponse();

  // Nested submission

  SubmissionProjection getSubmission();
}
