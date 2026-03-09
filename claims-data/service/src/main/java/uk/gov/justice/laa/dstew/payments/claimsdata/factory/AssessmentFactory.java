package uk.gov.justice.laa.dstew.payments.claimsdata.factory;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentType;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Factory class responsible for creating and initializing {@link Assessment} objects. It provides
 * utility methods to create specific types of assessments and to set standardized field values.
 */
@Component
public class AssessmentFactory {

  /**
   * Creates a new {@link Assessment} of type VOID and initializes it with specific parameters. All
   * monetary fields are set to zero and common fields are populated using the provided arguments.
   *
   * @param assessmentReason the reason for creating the void assessment
   * @param claim the associated {@link Claim} instance
   * @param claimSummaryFee the related {@link ClaimSummaryFee} instance
   * @param createdByUserId the UUID of the user creating the assessment
   * @return a new {@link Assessment} of type VOID
   */
  public Assessment createVoidAssessment(
      String assessmentReason, Claim claim, ClaimSummaryFee claimSummaryFee, UUID createdByUserId) {

    Assessment assessment = new Assessment();
    applyCommonFields(
        assessment,
        claim,
        claimSummaryFee,
        createdByUserId.toString(),
        assessmentReason,
        AssessmentType.VOID);
    setMonetaryFieldsToZero(assessment);

    return assessment;
  }

  /**
   * Sets all monetary fields of the given {@link Assessment} to zero.
   *
   * @param assessment the {@link Assessment} whose monetary fields will be reset to zero
   */
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

  /**
   * Populates common fields in the given {@link Assessment} based on the provided parameters.
   *
   * @param assessment the {@link Assessment} to be updated
   * @param claim the associated {@link Claim} instance
   * @param claimSummaryFee the related {@link ClaimSummaryFee} instance
   * @param createdByUserId the ID of the user creating the assessment
   * @param assessmentReason the reason for the assessment
   * @param assessmentType the type of the assessment (e.g., VOID)
   */
  public void applyCommonFields(
      Assessment assessment,
      Claim claim,
      ClaimSummaryFee claimSummaryFee,
      String createdByUserId,
      String assessmentReason,
      AssessmentType assessmentType) {
    assessment.setId(Uuid7.timeBasedUuid());
    assessment.setClaim(claim);
    assessment.setClaimSummaryFee(claimSummaryFee);
    assessment.setCreatedByUserId(createdByUserId);
    assessment.setUpdatedByUserId(createdByUserId);
    assessment.setAssessmentReason(assessmentReason);
    assessment.setAssessmentType(assessmentType);
  }
}
