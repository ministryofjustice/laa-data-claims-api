package uk.gov.justice.laa.dstew.payments.claimsdata.factory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentType;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AssessmentFactoryTest {

  private AssessmentFactory assessmentFactory;

  @BeforeEach
  void setUp() {
    assessmentFactory = new AssessmentFactory();
  }

  @Test
  void createVoidAssessment_shouldCreateAssessmentWithZeroMonetaryValues() {

    Claim claim = new Claim();
    ClaimSummaryFee claimSummaryFee = new ClaimSummaryFee();
    UUID userId = UUID.randomUUID();
    String reason = "Void assessment reason";

    Assessment result = assessmentFactory.createVoidAssessment(
        reason,
        claim,
        claimSummaryFee,
        userId
    );

    assertThat(result).isNotNull();

    assertThat(result.getClaim()).isEqualTo(claim);
    assertThat(result.getClaimSummaryFee()).isEqualTo(claimSummaryFee);
    assertThat(result.getAssessmentReason()).isEqualTo(reason);
    assertThat(result.getAssessmentType()).isEqualTo(AssessmentType.VOID);

    assertThat(result.getCreatedByUserId()).isEqualTo(userId.toString());
    assertThat(result.getUpdatedByUserId()).isEqualTo(userId.toString());

    assertThat(result.getId()).isNotNull();

    BigDecimal zero = BigDecimal.ZERO;

    assertThat(result.getFixedFeeAmount()).isEqualTo(zero);
    assertThat(result.getNetTravelCostsAmount()).isEqualTo(zero);
    assertThat(result.getNetWaitingCostsAmount()).isEqualTo(zero);
    assertThat(result.getNetProfitCostsAmount()).isEqualTo(zero);
    assertThat(result.getDisbursementAmount()).isEqualTo(zero);
    assertThat(result.getDisbursementVatAmount()).isEqualTo(zero);
    assertThat(result.getNetCostOfCounselAmount()).isEqualTo(zero);
    assertThat(result.getDetentionTravelAndWaitingCostsAmount()).isEqualTo(zero);
    assertThat(result.getBoltOnAdjournedHearingFee()).isEqualTo(zero);
    assertThat(result.getJrFormFillingAmount()).isEqualTo(zero);
    assertThat(result.getBoltOnCmrhOralFee()).isEqualTo(zero);
    assertThat(result.getBoltOnCmrhTelephoneFee()).isEqualTo(zero);
    assertThat(result.getBoltOnSubstantiveHearingFee()).isEqualTo(zero);
    assertThat(result.getBoltOnHomeOfficeInterviewFee()).isEqualTo(zero);
    assertThat(result.getAssessedTotalVat()).isEqualTo(zero);
    assertThat(result.getAssessedTotalInclVat()).isEqualTo(zero);
    assertThat(result.getAllowedTotalVat()).isEqualTo(zero);
    assertThat(result.getAllowedTotalInclVat()).isEqualTo(zero);
  }

  @Test
  void applyCommonFields_shouldPopulateAssessmentFields() {

    Assessment assessment = new Assessment();
    Claim claim = new Claim();
    ClaimSummaryFee claimSummaryFee = new ClaimSummaryFee();

    String userId = UUID.randomUUID().toString();
    String reason = "Test reason";

    assessmentFactory.applyCommonFields(
        assessment,
        claim,
        claimSummaryFee,
        userId,
        reason,
        AssessmentType.ESCAPE_CASE_ASSESSMENT
    );

    assertThat(assessment.getId()).isNotNull();
    assertThat(assessment.getClaim()).isEqualTo(claim);
    assertThat(assessment.getClaimSummaryFee()).isEqualTo(claimSummaryFee);
    assertThat(assessment.getCreatedByUserId()).isEqualTo(userId);
    assertThat(assessment.getUpdatedByUserId()).isEqualTo(userId);
    assertThat(assessment.getAssessmentReason()).isEqualTo(reason);
    assertThat(assessment.getAssessmentType()).isEqualTo(AssessmentType.ESCAPE_CASE_ASSESSMENT);
  }
}