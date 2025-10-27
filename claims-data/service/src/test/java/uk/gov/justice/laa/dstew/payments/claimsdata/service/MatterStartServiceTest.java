package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_USER_ID;

import java.util.*;
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
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CategoryCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MatterStartGet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MatterStartPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.MatterStartRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

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
      final UUID submissionId = Uuid7.timeBasedUuid();
      final Submission submission = Submission.builder().id(submissionId).build();
      final MatterStartPost request = new MatterStartPost();
      request.setCreatedByUserId(API_USER_ID);
      final MatterStart matterStart = MatterStart.builder().build();

      when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
      when(matterStartMapper.toMatterStart(request)).thenReturn(matterStart);

      final UUID id = matterStartService.createMatterStart(submissionId, request);

      assertThat(id).isNotNull();
      assertThat(matterStart.getId()).isEqualTo(id);
      assertThat(matterStart.getSubmission()).isSameAs(submission);
      assertThat(matterStart.getCreatedByUserId()).isEqualTo(API_USER_ID);
      verify(matterStartRepository).save(matterStart);
      verify(matterStartMapper).toMatterStart(request);
    }

    @Test
    void shouldThrowWhenSubmissionNotFound() {
      final UUID missingSubmissionId = Uuid7.timeBasedUuid();
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
      final UUID submissionId = Uuid7.timeBasedUuid();
      final MatterStart ms = MatterStart.builder().id(Uuid7.timeBasedUuid()).build();

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
      final UUID submissionId = Uuid7.timeBasedUuid();
      final UUID matterStartsId = Uuid7.timeBasedUuid();
      when(matterStartRepository.findBySubmissionIdAndId(submissionId, matterStartsId))
          .thenReturn(Optional.of(MatterStart.builder().id(matterStartsId).build()));
      MatterStartGet expected =
          MatterStartGet.builder()
              .categoryCode(CategoryCode.AAP)
              .accessPointCode("Access Code")
              .build();
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
      final UUID submissionId = Uuid7.timeBasedUuid();
      final UUID matterStartsId = Uuid7.timeBasedUuid();
      when(matterStartRepository.findBySubmissionIdAndId(submissionId, matterStartsId))
          .thenReturn(Optional.empty());
      // When
      Optional<MatterStartGet> result =
          matterStartService.getMatterStart(submissionId, matterStartsId);
      // Then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  class GetAllMatterStartsForSubmissionTests {

    @DisplayName("Should throw exception when submission id does not exist")
    @Test
    void shouldThrowExceptionWhenSubmissionIdDoesNotExist() {
      var missingSubmissionId = Uuid7.timeBasedUuid();
      when(submissionRepository.findById(missingSubmissionId)).thenReturn(Optional.empty());

      assertThatThrownBy(
              () -> matterStartService.getAllMatterStartsForSubmission(missingSubmissionId))
          .isInstanceOf(SubmissionNotFoundException.class)
          .hasMessageContaining(missingSubmissionId.toString());
      verify(matterStartRepository, times(0)).findBySubmissionId(eq(missingSubmissionId));
      verify(matterStartMapper, times(0)).toMatterStartGet(any());
    }

    @DisplayName("Should return list of Matter Starts for a valid submission id")
    @Test
    void shouldReturnListMatterStartsForValidSubmissionId() {
      var submissionId = Uuid7.timeBasedUuid();
      var matterStartEntity = MatterStart.builder().id(Uuid7.timeBasedUuid()).build();
      when(submissionRepository.findById(submissionId))
          .thenReturn(Optional.of(Submission.builder().id(submissionId).build()));
      when(matterStartRepository.findBySubmissionId(submissionId))
          .thenReturn(List.of(matterStartEntity, matterStartEntity));
      when(matterStartMapper.toMatterStartGet(matterStartEntity))
          .thenReturn(MatterStartGet.builder().build());

      var actualMatterStarts = matterStartService.getAllMatterStartsForSubmission(submissionId);

      verify(submissionRepository).findById(eq(submissionId));
      verify(matterStartRepository).findBySubmissionId(eq(submissionId));
      verify(matterStartMapper, times(2)).toMatterStartGet(eq(matterStartEntity));

      assertThat(actualMatterStarts.getSubmissionId()).isEqualTo(submissionId);
      assertThat(actualMatterStarts.getMatterStarts()).hasSize(2);
    }

    @DisplayName(
        "Should return empty list of Matter Starts when submission id has no associated matter starts")
    @Test
    void shouldReturnEmptyListOfMatterStarts() {
      var submissionId = Uuid7.timeBasedUuid();
      when(submissionRepository.findById(submissionId))
          .thenReturn(Optional.of(Submission.builder().id(submissionId).build()));
      when(matterStartRepository.findBySubmissionId(submissionId))
          .thenReturn(Collections.emptyList());

      var actualMatterStarts = matterStartService.getAllMatterStartsForSubmission(submissionId);

      verify(matterStartMapper, never()).toMatterStartGet(any());

      assertThat(actualMatterStarts.getSubmissionId()).isEqualTo(submissionId);
      assertThat(actualMatterStarts.getMatterStarts()).hasSize(0);
    }
  }
}
