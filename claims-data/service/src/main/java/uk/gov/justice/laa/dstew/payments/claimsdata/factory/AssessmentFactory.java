package uk.gov.justice.laa.dstew.payments.claimsdata.factory;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentType;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class AssessmentFactory {

  public Assessment createVoidAssessment(
      String assessmentReason, Claim claim, ClaimSummaryFee claimSummaryFee, UUID createdByUserId) {

    Assessment assessment = new Assessment();
    applyCommonFields(assessment, claim, claimSummaryFee, createdByUserId.toString(), assessmentReason, AssessmentType.VOID);
    setMonetaryFieldsToZero(assessment);

    return assessment;
  }

  private void setMonetaryFieldsToZero(Assessment assessment) {
    BigDecimal zero = BigDecimal.ZERO;

    assessment.setFixedFeeAmount(zero);
    assessment.setNetTravelCostsAmount(zero);
    assessment.setNetWaitingCostsAmount(zero);
    assessment.setNetProfitCostsAmount(zero);
    assessment.setDisbursementAmount(zero);
    assessment.setDisbursementVatAmount(zero);
    assessment.setNetCostOfCounselAmount(zero);
    assessment.setDetentionTravelAndWaitingCostsAmount(zero);
    assessment.setBoltOnAdjournedHearingFee(zero);
    assessment.setJrFormFillingAmount(zero);
    assessment.setBoltOnCmrhOralFee(zero);
    assessment.setBoltOnCmrhTelephoneFee(zero);
    assessment.setBoltOnSubstantiveHearingFee(zero);
    assessment.setBoltOnHomeOfficeInterviewFee(zero);
    assessment.setAssessedTotalVat(zero);
    assessment.setAssessedTotalInclVat(zero);
    assessment.setAllowedTotalVat(zero);
    assessment.setAllowedTotalInclVat(zero);
  }

  public void applyCommonFields(Assessment assessment, Claim claim, ClaimSummaryFee claimSummaryFee, String createdByUserId,
                                String assessmentReason, AssessmentType assessmentType) {
    assessment.setId(Uuid7.timeBasedUuid());
    assessment.setClaim(claim);
    assessment.setClaimSummaryFee(claimSummaryFee);
    assessment.setCreatedByUserId(createdByUserId);
    assessment.setUpdatedByUserId(createdByUserId);
    assessment.setAssessmentReason(assessmentReason);
    assessment.setAssessmentType(assessmentType);
  }
}