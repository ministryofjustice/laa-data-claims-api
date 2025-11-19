package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_USER_ID;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimSummaryFeeNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.AssessmentMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentPost;
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
      final UUID assessmentId = Uuid7.timeBasedUuid();
      final UUID claimId = Uuid7.timeBasedUuid();
      final UUID claimSummaryFeeId = Uuid7.timeBasedUuid();
      final AssessmentPost post =
          AssessmentPost.builder().claimId(claimId).claimSummaryFeeId(claimSummaryFeeId).build();

      final Claim claim = Claim.builder().id(claimId).build();

      final ClaimSummaryFee claimSummaryFee =
          ClaimSummaryFee.builder().id(claimSummaryFeeId).build();

      final Assessment assessment =
          Assessment.builder()
              .id(assessmentId)
              .claim(claim)
              .claimSummaryFee(claimSummaryFee)
              .createdByUserId(API_USER_ID)
              .build();

      when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
      when(claimSummaryFeeRepository.findById(claimSummaryFeeId))
          .thenReturn(Optional.of(claimSummaryFee));
      when(assessmentMapper.toAssessment(post)).thenReturn(assessment);
      when(assessmentRepository.save(assessment)).thenReturn(assessment);

      final UUID id = assessmentService.createAssessment(post);

      assertThat(id).isNotNull();
      assertThat(assessment.getId()).isEqualTo(id);
      assertThat(assessment.getCreatedByUserId()).isEqualTo(API_USER_ID);
      assertThat(assessment.getClaim()).isSameAs(claim);
      assertThat(assessment.getClaimSummaryFee()).isSameAs(claimSummaryFee);
      verify(claimRepository).findById(claimId);
      verify(claimSummaryFeeRepository).findById(claimSummaryFeeId);
      verify(assessmentRepository).save(assessment);
    }

    @Test
    void shouldThrowWhenClaimNotFound() {
      final UUID missingClaimId = Uuid7.timeBasedUuid();
      final AssessmentPost post = AssessmentPost.builder().claimId(missingClaimId).build();

      when(claimRepository.findById(missingClaimId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> assessmentService.createAssessment(post))
          .isInstanceOf(ClaimNotFoundException.class)
          .hasMessageContaining(missingClaimId.toString());
    }

    @Test
    void shouldThrowWhenClaimSummaryFeeNotFound() {
      final UUID claimId = Uuid7.timeBasedUuid();
      final UUID missingClaimSummaryFeeId = Uuid7.timeBasedUuid();
      final AssessmentPost post =
          AssessmentPost.builder()
              .claimId(claimId)
              .claimSummaryFeeId(missingClaimSummaryFeeId)
              .build();

      final Claim claim = Claim.builder().id(claimId).build();

      when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
      when(claimSummaryFeeRepository.findById(missingClaimSummaryFeeId))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> assessmentService.createAssessment(post))
          .isInstanceOf(ClaimSummaryFeeNotFoundException.class)
          .hasMessageContaining(missingClaimSummaryFeeId.toString());
    }
  }
}
