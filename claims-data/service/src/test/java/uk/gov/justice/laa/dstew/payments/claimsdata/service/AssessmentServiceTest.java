package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_USER_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.ASSESSMENT_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.USER_ID;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.AssessmentNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.factory.AssessmentFactory;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.AssessmentMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentGet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentType;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.AssessmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;

@ExtendWith(MockitoExtension.class)
class AssessmentServiceTest {

  @Mock private ClaimRepository claimRepository;
  @Mock private AssessmentRepository assessmentRepository;
  @Mock private AssessmentMapper assessmentMapper;
  @Mock private ClaimValidationService claimValidationService;
  @Mock private AssessmentFactory assessmentFactory;

  @InjectMocks private AssessmentService assessmentService;

  @Nested
  @DisplayName("create assessment")
  class CreateAssessmentTests {

    @Test
    void shouldCreateAssessmentAndUpdateClaimStatusWhenFirstAssessment() {

      UUID claimId = UUID.randomUUID();
      UUID assessmentId = UUID.randomUUID();
      UUID claimSummaryFeeId = UUID.randomUUID();

      AssessmentPost post =
          AssessmentPost.builder()
              .claimId(claimId)
              .claimSummaryFeeId(claimSummaryFeeId)
              .createdByUserId(API_USER_ID)
              .assessmentType(AssessmentType.ESCAPE_CASE_ASSESSMENT)
              .build();

      Claim claim = Claim.builder()
          .id(claimId)
          .hasAssessment(false)
          .build();

      ClaimSummaryFee fee = ClaimSummaryFee.builder()
          .id(claimSummaryFeeId)
          .build();

      Assessment assessment = Assessment.builder().id(assessmentId).build();

      when(claimValidationService.getValidClaimOrThrow(claimId)).thenReturn(claim);
      when(claimValidationService.getClaimSummaryFeeByIdOrThrow(claimSummaryFeeId)).thenReturn(fee);
      when(assessmentMapper.toAssessment(post)).thenReturn(assessment);
      when(assessmentRepository.save(assessment)).thenReturn(assessment);

      UUID result = assessmentService.createAssessment(claimId, post);

      assertThat(result).isEqualTo(assessmentId);

      verify(claimValidationService).validateUserId(API_USER_ID);
      verify(claimValidationService).ensureAssessmentTypeIsNotVoid(post.getAssessmentType());

      verify(claimRepository).updateAssessmentStatus(claimId, true);

      verify(assessmentFactory)
          .applyCommonFields(
              eq(assessment),
              eq(claim),
              eq(fee),
              eq(API_USER_ID),
              eq(post.getAssessmentReason()),
              eq(AssessmentType.ESCAPE_CASE_ASSESSMENT));

      verify(assessmentRepository).save(assessment);
    }

    @Test
    void shouldCreateAssessmentWithoutUpdatingClaimStatusWhenAlreadyAssessed() {

      UUID claimId = UUID.randomUUID();
      UUID claimSummaryFeeId = UUID.randomUUID();

      AssessmentPost post =
          AssessmentPost.builder()
              .claimId(claimId)
              .claimSummaryFeeId(claimSummaryFeeId)
              .createdByUserId(API_USER_ID)
              .assessmentType(AssessmentType.ESCAPE_CASE_ASSESSMENT)
              .build();

      Claim claim = Claim.builder()
          .id(claimId)
          .hasAssessment(true)
          .build();

      ClaimSummaryFee fee = ClaimSummaryFee.builder()
          .id(claimSummaryFeeId)
          .build();

      Assessment assessment = Assessment.builder().id(UUID.randomUUID()).build();

      when(claimValidationService.getValidClaimOrThrow(claimId)).thenReturn(claim);
      when(claimValidationService.getClaimSummaryFeeByIdOrThrow(claimSummaryFeeId)).thenReturn(fee);
      when(assessmentMapper.toAssessment(post)).thenReturn(assessment);
      when(assessmentRepository.save(assessment)).thenReturn(assessment);

      assessmentService.createAssessment(claimId, post);

      verify(claimRepository, never()).updateAssessmentStatus(any(), anyBoolean());
      verify(assessmentRepository).save(assessment);
    }

    @Test
    void shouldThrowWhenClaimNotFound() {

      UUID claimId = UUID.randomUUID();

      AssessmentPost post =
          AssessmentPost.builder()
              .claimId(claimId)
              .createdByUserId(API_USER_ID)
              .build();

      when(claimValidationService.getValidClaimOrThrow(claimId))
          .thenThrow(new ClaimNotFoundException(String.format("No Claim found with id: %s", claimId)));

      assertThatThrownBy(() -> assessmentService.createAssessment(claimId, post))
          .isInstanceOf(ClaimNotFoundException.class);
    }

  }

  @Test
  void getAssessmentShouldReturnMappedObject() {
    Assessment entity = new Assessment();

    AssessmentGet dto = new AssessmentGet();
    dto.setClaimId(CLAIM_1_ID);
    dto.setId(ASSESSMENT_1_ID);
    dto.setCreatedByUserId(USER_ID);

    when(assessmentRepository.findByIdAndClaimId(ASSESSMENT_1_ID, CLAIM_1_ID))
        .thenReturn(Optional.of(entity));
    when(assessmentMapper.toAssessmentGet(entity)).thenReturn(dto);

    AssessmentGet result = assessmentService.getAssessment(CLAIM_1_ID, ASSESSMENT_1_ID);

    assertThat(result).isNotNull();
    assertThat(result.getClaimId()).isEqualTo(CLAIM_1_ID);
    assertThat(result.getId()).isEqualTo(ASSESSMENT_1_ID);
    assertThat(result.getCreatedByUserId()).isEqualTo(USER_ID);
  }

  @Test
  void shouldReturnNullWhenMapperReturnsNull() {
    var mockAssessment = new Assessment();
    mockAssessment.setId(ASSESSMENT_1_ID);
    when(assessmentRepository.findByIdAndClaimId(ASSESSMENT_1_ID, CLAIM_1_ID))
        .thenReturn(Optional.of(mockAssessment));
    when(assessmentMapper.toAssessmentGet(mockAssessment)).thenReturn(null);

    AssessmentGet result = assessmentService.getAssessment(CLAIM_1_ID, ASSESSMENT_1_ID);

    assertThat(result).isNull();
  }

  @Test
  void getAssessmentShouldReturnNotFoundWhenAssessmentNotFound() {
    when(assessmentRepository.findByIdAndClaimId(ASSESSMENT_1_ID, CLAIM_1_ID))
        .thenReturn(Optional.empty());

    assertThrows(
        AssessmentNotFoundException.class,
        () -> assessmentService.getAssessment(CLAIM_1_ID, ASSESSMENT_1_ID));
  }

  @Test
  void shouldReturnAssessmentResultSetWhenAssessmentsExist() {
    Assessment assessment = new Assessment();
    assessment.setId(ASSESSMENT_1_ID);
    Claim claim = new Claim();
    claim.setId(CLAIM_1_ID);
    assessment.setClaim(claim);

    AssessmentGet dto = new AssessmentGet();
    dto.setId(assessment.getId());
    dto.setClaimId(CLAIM_1_ID);

    when(assessmentRepository.findByClaimId(eq(CLAIM_1_ID), any(Pageable.class)))
        .thenReturn(List.of(assessment));
    when(assessmentMapper.toAssessmentGet(assessment)).thenReturn(dto);

    AssessmentResultSet result =
        assessmentService.getAssessmentsByClaimId(CLAIM_1_ID, Pageable.unpaged());

    assertThat(result).isNotNull();
    assertThat(result.getAssessments()).hasSize(1);
    assertThat(result.getAssessments().getFirst().getClaimId()).isEqualTo(CLAIM_1_ID);
  }

  @Test
  void shouldThrowExceptionWhenAssessmentsEmpty() {
    when(assessmentRepository.findByClaimId(eq(CLAIM_1_ID), any(Pageable.class)))
        .thenReturn(Collections.emptyList());

    assertThatThrownBy(
            () -> assessmentService.getAssessmentsByClaimId(CLAIM_1_ID, Pageable.unpaged()))
        .isInstanceOf(AssessmentNotFoundException.class)
        .hasMessageContaining("No assessments found for claimId");
  }
}
