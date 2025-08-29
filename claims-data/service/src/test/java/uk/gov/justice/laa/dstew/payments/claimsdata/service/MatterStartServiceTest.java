package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.MatterStart;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.MatterStartMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MatterStartGet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MatterStartPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.MatterStartRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;

@ExtendWith(MockitoExtension.class)
class MatterStartServiceTest {

  @Mock private SubmissionRepository submissionRepository;
  @Mock private MatterStartRepository matterStartRepository;
  @Mock private MatterStartMapper matterStartMapper;

  @InjectMocks private MatterStartService matterStartService;

  @Nested
  @DisplayName("createMatterStart tests")
  class CreateMatterStartTests {

    @Test
    void shouldCreateMatterStart() {
      final UUID submissionId = UUID.randomUUID();
      final Submission submission = Submission.builder().id(submissionId).build();
      final MatterStartPost request = new MatterStartPost();
      final MatterStart matterStart = MatterStart.builder().build();

      when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
      when(matterStartMapper.toMatterStart(request)).thenReturn(matterStart);

      final UUID id = matterStartService.createMatterStart(submissionId, request);

      assertThat(id).isNotNull();
      assertThat(matterStart.getId()).isEqualTo(id);
      assertThat(matterStart.getSubmission()).isSameAs(submission);
      //  TODO: DSTEW-323 replace with the actual user ID/name when available
      assertThat(matterStart.getCreatedByUserId()).isEqualTo("todo");
      verify(matterStartRepository).save(matterStart);
      verify(matterStartMapper).toMatterStart(request);
    }

    @Test
    void shouldThrowWhenSubmissionNotFound() {
      final UUID missingSubmissionId = UUID.randomUUID();
      final MatterStartPost request = new MatterStartPost();

      when(submissionRepository.findById(missingSubmissionId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> matterStartService.createMatterStart(missingSubmissionId, request))
          .isInstanceOf(SubmissionNotFoundException.class)
          .hasMessageContaining(missingSubmissionId.toString());
    }
  }

  @Nested
  @DisplayName("Get matter start IDs for submission tests")
  class GetMatterStartIdsForSubmissionTests {

    @Test
    void shouldGetMatterStartIdsForSubmission() {
      final UUID submissionId = UUID.randomUUID();
      final MatterStart ms = MatterStart.builder().id(UUID.randomUUID()).build();

      when(matterStartRepository.findBySubmissionId(submissionId)).thenReturn(List.of(ms));

      final List<UUID> result = matterStartService.getMatterStartIdsForSubmission(submissionId);

      assertThat(result).containsExactly(ms.getId());
    }
  }

  @Nested
  @DisplayName("Get matter start tests")
  class GetMatterStartsTests {

    @Test
    @DisplayName("Should return matter start")
    void shouldReturnMatterStart() {
      // Given
      final UUID submissionId = UUID.randomUUID();
      final UUID matterStartsId = UUID.randomUUID();
      when(matterStartRepository.findBySubmissionIdAndId(submissionId, matterStartsId))
          .thenReturn(Optional.of(MatterStart.builder().id(matterStartsId).build()));
      MatterStartGet expected =
          MatterStartGet.builder().categoryCode("CAT A").categoryCode("Access Code").build();
      when(matterStartMapper.toMatterStartGet(any())).thenReturn(expected);
      // When
      Optional<MatterStartGet> result =
          matterStartService.getMatterStart(submissionId, matterStartsId);
      // Then
      assertThat(result).isNotEmpty().get().isEqualTo(expected);
    }

    @Test
    @DisplayName("Should return empty")
    void shouldReturnEmpty() {
      // Given
      final UUID submissionId = UUID.randomUUID();
      final UUID matterStartsId = UUID.randomUUID();
      when(matterStartRepository.findBySubmissionIdAndId(submissionId, matterStartsId))
          .thenReturn(Optional.empty());
      // When
      Optional<MatterStartGet> result =
          matterStartService.getMatterStart(submissionId, matterStartsId);
      // Then
      assertThat(result).isEmpty();
    }
  }
}
