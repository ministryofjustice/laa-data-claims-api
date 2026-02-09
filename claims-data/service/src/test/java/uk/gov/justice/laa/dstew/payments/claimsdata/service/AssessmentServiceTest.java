package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.AssessmentInvalidUserException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.AssessmentNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimSummaryFeeNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.AssessmentMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentGet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.AssessmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@ExtendWith(MockitoExtension.class)
class AssessmentServiceTest {

  @Mock private ClaimRepository claimRepository;
  @Mock private ClaimSummaryFeeRepository claimSummaryFeeRepository;
  @Mock private AssessmentRepository assessmentRepository;
  @Mock private AssessmentMapper assessmentMapper;

  @InjectMocks private AssessmentService assessmentService;

  @Nested
  @DisplayName("create assessment")
  class CreateAssessmentTests {

    @Test
    void shouldCreateAssessment() {
      final String userId = Uuid7.timeBasedUuid().toString();
      final UUID assessmentId = Uuid7.timeBasedUuid();
      final UUID claimId = Uuid7.timeBasedUuid();
      final UUID claimSummaryFeeId = Uuid7.timeBasedUuid();
      final AssessmentPost post =
          AssessmentPost.builder()
              .claimId(claimId)
              .createdByUserId(userId)
              .claimSummaryFeeId(claimSummaryFeeId)
              .createdByUserId(API_USER_ID)
              .build();

      final Claim claim = Claim.builder().id(claimId).build();

      final ClaimSummaryFee claimSummaryFee =
          ClaimSummaryFee.builder().id(claimSummaryFeeId).build();

      final Assessment assessment =
          Assessment.builder()
              .id(assessmentId)
              .claim(claim)
              .claimSummaryFee(claimSummaryFee)
              .createdByUserId(API_USER_ID)
              .updatedByUserId(API_USER_ID)
              .build();

      when(claimRepository.existsById(claimId)).thenReturn(true);
      when(claimRepository.getReferenceById(claimId)).thenReturn(claim);

      when(claimSummaryFeeRepository.existsById(claimSummaryFeeId)).thenReturn(true);
      when(claimSummaryFeeRepository.getReferenceById(claimSummaryFeeId))
          .thenReturn(claimSummaryFee);

      when(assessmentMapper.toAssessment(post)).thenReturn(assessment);
      when(assessmentRepository.save(assessment)).thenReturn(assessment);

      final UUID id = assessmentService.createAssessment(claimId, post);

      assertThat(id).isNotNull();
      assertThat(assessment.getId()).isEqualTo(id);
      assertThat(assessment.getCreatedByUserId()).isEqualTo(API_USER_ID);
      assertThat(assessment.getUpdatedByUserId()).isEqualTo(API_USER_ID);
      assertThat(assessment.getClaim()).isSameAs(claim);
      assertThat(assessment.getClaimSummaryFee()).isSameAs(claimSummaryFee);
      verify(claimRepository).existsById(claimId);
      verify(claimRepository).getReferenceById(claimId);
      verify(claimSummaryFeeRepository).existsById(claimSummaryFeeId);
      verify(claimSummaryFeeRepository).getReferenceById(claimSummaryFeeId);
      verify(assessmentRepository).save(assessment);
      verify(claimRepository).updateAssessmentStatus(claim.getId(), true);
    }

    @Test
    void shouldThrowWhenClaimNotFound() {
      final String userId = Uuid7.timeBasedUuid().toString();
      final UUID missingClaimId = Uuid7.timeBasedUuid();
      final AssessmentPost post =
          AssessmentPost.builder()
              .createdByUserId(userId)
              .claimId(missingClaimId)
              .build();

      when(claimRepository.existsById(missingClaimId)).thenReturn(false);

      assertThatThrownBy(() -> assessmentService.createAssessment(missingClaimId, post))
          .isInstanceOf(ClaimNotFoundException.class)
          .hasMessageContaining(missingClaimId.toString());
    }

    /**
     * Helper method to test invalid user ID validation scenarios.
     *
     * <p>Verifies that {@link AssessmentService#createAssessment(UUID, AssessmentPost)} throws
     * {@link AssessmentInvalidUserException} with the expected error message when provided with an
     * invalid user ID.
     *
     * @param userid the user ID to test (can be null, empty, blank, or invalid UUID format)
     * @param errorMessage the expected error message enum that should be in the exception
     */
    void invalidUserTest(String userid, AssessmentInvalidUserException.ErrorMessage errorMessage){
      final UUID missingClaimId = Uuid7.timeBasedUuid();
      final AssessmentPost post =
              AssessmentPost.builder()
                      .createdByUserId(userid)
                      .claimId(missingClaimId)
                      .build();

      assertThatThrownBy(() -> assessmentService.createAssessment(missingClaimId, post))
              .isInstanceOf(AssessmentInvalidUserException.class)
              .hasMessageContaining(errorMessage.getMessage(userid));
    }

    @Test
    void shouldThrowWhenInvalidUserIdNull() {
      invalidUserTest(null, AssessmentInvalidUserException.ErrorMessage.NULL_OR_BLANK);
    }

    @Test
    void shouldThrowWhenInvalidUserIdEmpty() {
      invalidUserTest("", AssessmentInvalidUserException.ErrorMessage.NULL_OR_BLANK);
    }

    @Test
    void shouldThrowWhenInvalidUserIdWhiteSpace() {
      invalidUserTest("  ", AssessmentInvalidUserException.ErrorMessage.NULL_OR_BLANK);
    }

    @Test
    void shouldThrowWhenInvalidUserIdInvalid() {
      invalidUserTest("INVALIDUUID", AssessmentInvalidUserException.ErrorMessage.INVALID_UUID_FORMAT);
    }

    @Test
    void shouldThrowWhenClaimSummaryFeeNotFound() {
      final String userId = Uuid7.timeBasedUuid().toString();
      final UUID claimId = Uuid7.timeBasedUuid();
      final UUID missingClaimSummaryFeeId = Uuid7.timeBasedUuid();
      final AssessmentPost post =
          AssessmentPost.builder()
              .claimId(claimId)
              .createdByUserId(userId)
              .claimSummaryFeeId(missingClaimSummaryFeeId)
              .build();

      final Claim claim = Claim.builder().id(claimId).build();

      when(claimRepository.existsById(claimId)).thenReturn(true);
      when(claimRepository.getReferenceById(claimId)).thenReturn(claim);

      when(claimSummaryFeeRepository.existsById(missingClaimSummaryFeeId)).thenReturn(false);

      assertThatThrownBy(() -> assessmentService.createAssessment(claimId, post))
          .isInstanceOf(ClaimSummaryFeeNotFoundException.class)
          .hasMessageContaining(missingClaimSummaryFeeId.toString());
    }
  }

  @Nested
  @DisplayName("validate User Id")
  class validateUserIdTests {

    void invalidUserIdTest(String userid, AssessmentInvalidUserException.ErrorMessage errorMessage){
      assertThatThrownBy(() -> assessmentService.validateUserId(userid))
              .isInstanceOf(AssessmentInvalidUserException.class)
              .hasMessageContaining(errorMessage.getMessage(userid));
    }

    @Test
    void shouldThrowWhenInvalidUserIdNull() {
      invalidUserIdTest(null, AssessmentInvalidUserException.ErrorMessage.NULL_OR_BLANK);
    }

    @Test
    void shouldThrowWhenInvalidUserIdEmpty() {
      invalidUserIdTest("", AssessmentInvalidUserException.ErrorMessage.NULL_OR_BLANK);
    }

    @Test
    void shouldThrowWhenInvalidUserIdWhiteSpace() {
      invalidUserIdTest("  ", AssessmentInvalidUserException.ErrorMessage.NULL_OR_BLANK);
    }

    @Test
    void shouldThrowWhenInvalidUserIdInvalid() {
      invalidUserIdTest("INVALIDUUID", AssessmentInvalidUserException.ErrorMessage.INVALID_UUID_FORMAT);
    }

    @Test
    void shouldThrowWhenInvalidUserIdXSS() {
      invalidUserIdTest("<img src=x onerror=alert(\"XSS\")>", AssessmentInvalidUserException.ErrorMessage.INVALID_UUID_FORMAT);
    }

    @Test
    void shouldNotThrowWhenInvalidUserIdValid() {
      final String userId = Uuid7.timeBasedUuid().toString();
      assessmentService.validateUserId(userId);
    }
  }

  @Test
  void getAssessmentShouldReturnMappedObject() {
    Assessment entity = new Assessment();

    AssessmentGet dto = new AssessmentGet();
    dto.setClaimId(CLAIM_1_ID);
    dto.setId(ASSESSMENT_1_ID);
    dto.setCreatedByUserId(USER_ID);
    dto.setClaimId(CLAIM_1_ID);

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
  void getAssessmentShouldReturnEmpty() {
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
