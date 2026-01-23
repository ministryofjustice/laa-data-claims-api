package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@ExtendWith(MockitoExtension.class)
class AssessmentMapperTest {

  @InjectMocks private final AssessmentMapperImpl mapper = new AssessmentMapperImpl();

  @Spy private GlobalStringMapper globalStringMapper = new GlobalStringMapperImpl();

  @Test
  void toAssessment_nullInput_returnsNull() {
    assertNull(mapper.toAssessment(null));
  }

  @Test
  void toAssessment_mapsAllFields() {
    final AssessmentPost post = ClaimsDataTestUtil.getAssessmentPost();

    final Assessment entity = mapper.toAssessment(post);

    assertNotNull(entity);
    assertEquals(post.getAssessmentOutcome(), entity.getAssessmentOutcome());
    assertEquals(post.getFixedFeeAmount(), entity.getFixedFeeAmount());
    assertEquals(post.getNetTravelCostsAmount(), entity.getNetTravelCostsAmount());
    assertEquals(post.getNetWaitingCostsAmount(), entity.getNetWaitingCostsAmount());
    assertEquals(post.getNetProfitCostsAmount(), entity.getNetProfitCostsAmount());
    assertEquals(post.getDisbursementAmount(), entity.getDisbursementAmount());
    assertEquals(post.getDisbursementVatAmount(), entity.getDisbursementVatAmount());
    assertEquals(post.getNetCostOfCounselAmount(), entity.getNetCostOfCounselAmount());
    assertEquals(
        post.getDetentionTravelAndWaitingCostsAmount(),
        entity.getDetentionTravelAndWaitingCostsAmount());
    assertEquals(post.getIsVatApplicable(), entity.getIsVatApplicable());
    assertEquals(post.getBoltOnAdjournedHearingFee(), entity.getBoltOnAdjournedHearingFee());
    assertEquals(post.getJrFormFillingAmount(), entity.getJrFormFillingAmount());
    assertEquals(post.getBoltOnCmrhOralFee(), entity.getBoltOnCmrhOralFee());
    assertEquals(post.getBoltOnCmrhTelephoneFee(), entity.getBoltOnCmrhTelephoneFee());
    assertEquals(post.getBoltOnSubstantiveHearingFee(), entity.getBoltOnSubstantiveHearingFee());
    assertEquals(post.getBoltOnHomeOfficeInterviewFee(), entity.getBoltOnHomeOfficeInterviewFee());
    assertEquals(post.getCreatedByUserId(), entity.getCreatedByUserId());
    assertEquals(post.getAssessedTotalVat(), entity.getAssessedTotalVat());
    assertEquals(post.getAssessedTotalInclVat(), entity.getAssessedTotalInclVat());
    assertEquals(post.getAllowedTotalVat(), entity.getAllowedTotalVat());
    assertEquals(post.getAllowedTotalInclVat(), entity.getAllowedTotalInclVat());
  }

  @Test
  void toValidationMessageLog_mapsFields() {
    final Submission submission = Submission.builder().id(Uuid7.timeBasedUuid()).build();
    final Claim claim = Claim.builder().id(Uuid7.timeBasedUuid()).submission(submission).build();
    final Assessment assessment =
        Assessment.builder().id(Uuid7.timeBasedUuid()).claim(claim).build();

    final ValidationMessagePatch patch =
        new ValidationMessagePatch()
            .type(ValidationMessageType.ERROR)
            .source("SYSTEM")
            .displayMessage("A display message")
            .technicalMessage("A technical message");

    final ValidationMessageLog log = mapper.toValidationMessageLog(patch, assessment);

    assertNotNull(log.getId());
    assertEquals(submission.getId(), log.getSubmissionId());
    assertEquals(claim.getId(), log.getClaimId());
    assertEquals(ValidationMessageType.ERROR, log.getType());
    assertEquals("SYSTEM", log.getSource());
    assertEquals("A display message", log.getDisplayMessage());
    assertEquals("A technical message", log.getTechnicalMessage());
  }
}
