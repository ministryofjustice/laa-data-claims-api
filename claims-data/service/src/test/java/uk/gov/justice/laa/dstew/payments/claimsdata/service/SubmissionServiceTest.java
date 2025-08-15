package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.SubmissionMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetSubmission200Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.ClaimService;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.MatterStartService;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {
  @Mock private SubmissionRepository submissionRepository;

  @Mock private ClaimService claimService;

  @Mock private MatterStartService matterStartService;

  @Mock private SubmissionMapper submissionMapper;

  @InjectMocks private SubmissionService submissionService;

  @Test
  void shouldCreateSubmission() {
    UUID id = UUID.randomUUID();
    SubmissionPost post = new SubmissionPost().submissionId(id);
    Submission entity = Submission.builder().id(id).build();

    when(submissionMapper.toSubmission(post)).thenReturn(entity);
    when(submissionRepository.save(entity)).thenReturn(entity);

    UUID result = submissionService.createSubmission(post);

    assertThat(result).isEqualTo(id);
    verify(submissionRepository).save(entity);
  }

  @Test
  void shouldGetSubmission() {
    UUID id = UUID.randomUUID();
    Submission entity = Submission.builder().id(id).build();
    SubmissionFields fields = new SubmissionFields().submissionId(id);
    when(submissionRepository.findById(id)).thenReturn(java.util.Optional.of(entity));
    when(submissionMapper.toSubmissionFields(entity)).thenReturn(fields);
    when(claimService.getClaimsForSubmission(id)).thenReturn(java.util.List.of());
    when(matterStartService.getMatterStartIdsForSubmission(id))
        .thenReturn(java.util.List.of());

    GetSubmission200Response result = submissionService.getSubmission(id);

    assertThat(result.getSubmission().getSubmissionId()).isEqualTo(id);
  }

  @Test
  void shouldThrowWhenSubmissionNotFoundOnGet() {
    UUID id = UUID.randomUUID();
    when(submissionRepository.findById(id)).thenReturn(java.util.Optional.empty());

    assertThrows(SubmissionNotFoundException.class, () -> submissionService.getSubmission(id));
  }

  @Test
  void shouldUpdateSubmission() {
    UUID id = UUID.randomUUID();
    Submission entity = Submission.builder().id(id).build();
    SubmissionPatch patch = new SubmissionPatch().scheduleNumber("456");
    when(submissionRepository.findById(id)).thenReturn(java.util.Optional.of(entity));

    submissionService.updateSubmission(id, patch);

    verify(submissionMapper).updateSubmissionFromPatch(patch, entity);
    verify(submissionRepository).save(entity);
  }

  @Test
  void shouldThrowWhenSubmissionNotFoundOnUpdate() {
    UUID id = UUID.randomUUID();
    SubmissionPatch patch = new SubmissionPatch();
    when(submissionRepository.findById(id)).thenReturn(java.util.Optional.empty());

    assertThrows(
        SubmissionNotFoundException.class, () -> submissionService.updateSubmission(id, patch));
  }
}
