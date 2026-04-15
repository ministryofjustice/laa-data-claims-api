package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentGet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@ExtendWith(MockitoExtension.class)
class AssessmentMapperTest {

  @InjectMocks private final AssessmentMapperImpl mapper = new AssessmentMapperImpl();

  @Spy private GlobalStringMapper globalStringMapper = new GlobalStringMapperImpl();
  @Mock private GlobalDateTimeMapper globalDateTimeMapper;

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
    assertEquals(post.getAssessmentReason(), entity.getAssessmentReason());
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

  @Test
  void toAssessmentResultSet_mapsContentAndPagination() {
    Assessment a1 =
        assessment(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.parse("2024-01-01T10:00:00Z"));
    Assessment a2 =
        assessment(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.parse("2024-02-02T11:00:00Z"));

    int pageNumber = 3;
    int pageSize = 5;
    int totalElements = 42;

    var page = new PageImpl<>(List.of(a1, a2), PageRequest.of(pageNumber, pageSize), totalElements);
    Mockito.when(globalDateTimeMapper.map(a1.getCreatedOn()))
        .thenReturn(a1.getCreatedOn().atOffset(ZoneOffset.UTC));
    Mockito.when(globalDateTimeMapper.map(a2.getCreatedOn()))
        .thenReturn(a2.getCreatedOn().atOffset(ZoneOffset.UTC));
    // when
    AssessmentResultSet result = mapper.toAssessmentResultSet(page);

    assertNotNull(result);

    // pagination metadata
    assertEquals(pageNumber, result.getNumber());
    assertEquals(pageSize, result.getSize());
    assertEquals(totalElements, result.getTotalElements());
    assertEquals(result.getTotalPages(), page.getTotalPages());

    // content mapped via toAssessmentGet
    assertEquals(2, result.getAssessments().size());

    AssessmentGet r1 = result.getAssessments().get(0);
    AssessmentGet r2 = result.getAssessments().get(1);

    assertNotNull(r1);
    assertNotNull(r2);

    assertEquals(r1.getId(), a1.getId());
    assertEquals(r2.getId(), a2.getId());

    assertEquals(r1.getClaimSummaryFeeId(), a1.getClaimSummaryFee().getId());
    assertEquals(r2.getClaimSummaryFeeId(), a2.getClaimSummaryFee().getId());
  }

  @Test
  void toAssessmentResultSet_mapsEmptyPage() {
    var page = new PageImpl<Assessment>(List.of());

    var result = mapper.toAssessmentResultSet(page);

    assertNotNull(result);
    assertEquals(0, result.getNumber());
    assertEquals(0, result.getSize());
    assertEquals(0, result.getTotalElements());
    assertEquals(1, page.getTotalPages());
  }

  private static Assessment assessment(
      UUID id, UUID claimId, UUID claimSummaryFeeId, Instant createdAt) {
    Assessment a = new Assessment();
    a.setId(id);

    Claim claim = new Claim();
    claim.setId(claimId);
    a.setClaim(claim);

    ClaimSummaryFee fee = new ClaimSummaryFee();
    fee.setId(claimSummaryFeeId);
    a.setClaimSummaryFee(fee);

    a.setCreatedOn(createdAt);
    return a;
  }
}
