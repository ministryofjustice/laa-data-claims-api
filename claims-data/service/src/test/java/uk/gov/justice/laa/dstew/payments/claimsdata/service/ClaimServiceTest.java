package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.SubmissionClaim;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClientMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.SubmissionClaimMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetSubmission200ResponseClaimsInner;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClientRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {
  @Mock private SubmissionRepository submissionRepository;
  @Mock private SubmissionClaimRepository submissionClaimRepository;
  @Mock private ClientRepository clientRepository;
  @Mock private SubmissionClaimMapper submissionClaimMapper;
  @Mock private ClientMapper clientMapper;

  @InjectMocks private ClaimService claimService;

  @Test
  void shouldCreateClaimAndClient() {
    final UUID submissionId = UUID.randomUUID();
    final Submission submission = Submission.builder().id(submissionId).build();
    final ClaimPost post = new ClaimPost();
    final SubmissionClaim claim = SubmissionClaim.builder().build();
    final Client client = Client.builder().clientForename("John").build();

    when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
    when(submissionClaimMapper.toSubmissionClaim(post)).thenReturn(claim);
    when(clientMapper.toClient(post)).thenReturn(client);

    final UUID id = claimService.createClaim(submissionId, post);

    assertThat(id).isNotNull();
    assertThat(claim.getId()).isEqualTo(id);
    assertThat(claim.getCreatedByUserId()).isEqualTo("todo");
    assertThat(client.getClaim()).isSameAs(claim);
    assertThat(client.getCreatedByUserId()).isEqualTo("todo");
    verify(submissionClaimRepository).save(claim);
    verify(clientRepository).save(client);
  }

  @Test
  void shouldCreateClaimWithoutClientWhenNoClientData() {
    final UUID submissionId = UUID.randomUUID();
    final Submission submission = Submission.builder().id(submissionId).build();
    final ClaimPost post = new ClaimPost();
    final SubmissionClaim claim = SubmissionClaim.builder().build();
    final Client emptyClient = Client.builder().build();

    when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
    when(submissionClaimMapper.toSubmissionClaim(post)).thenReturn(claim);
    when(clientMapper.toClient(post)).thenReturn(emptyClient);

    final UUID id = claimService.createClaim(submissionId, post);

    assertThat(id).isNotNull();
    verify(submissionClaimRepository).save(claim);
    verify(clientRepository, never()).save(emptyClient);
  }

  @Test
  void shouldThrowWhenSubmissionNotFoundOnCreate() {
    final UUID submissionId = UUID.randomUUID();
    final ClaimPost post = new ClaimPost();

    when(submissionRepository.findById(submissionId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> claimService.createClaim(submissionId, post))
        .isInstanceOf(SubmissionNotFoundException.class)
        .hasMessageContaining(submissionId.toString());
  }

  @Test
  void shouldGetClaim() {
    final UUID submissionId = UUID.randomUUID();
    final UUID claimId = UUID.randomUUID();
    final SubmissionClaim claim = SubmissionClaim.builder().id(claimId).build();
    final ClaimFields fields = new ClaimFields();
    final Client client = Client.builder().clientForename("John").build();

    when(submissionClaimRepository.findByIdAndSubmissionId(claimId, submissionId))
        .thenReturn(Optional.of(claim));
    when(submissionClaimMapper.toClaimFields(claim)).thenReturn(fields);
    when(clientRepository.findByClaimId(claimId)).thenReturn(Optional.of(client));

    final ClaimFields result = claimService.getClaim(submissionId, claimId);

    assertThat(result).isSameAs(fields);
    verify(clientMapper).updateClaimFieldsFromClient(client, fields);
  }

  @Test
  void shouldGetClaimWithoutClient() {
    final UUID submissionId = UUID.randomUUID();
    final UUID claimId = UUID.randomUUID();
    final SubmissionClaim claim = SubmissionClaim.builder().id(claimId).build();
    final ClaimFields fields = new ClaimFields();

    when(submissionClaimRepository.findByIdAndSubmissionId(claimId, submissionId))
        .thenReturn(Optional.of(claim));
    when(submissionClaimMapper.toClaimFields(claim)).thenReturn(fields);
    when(clientRepository.findByClaimId(claimId)).thenReturn(Optional.empty());

    final ClaimFields result = claimService.getClaim(submissionId, claimId);

    assertThat(result).isSameAs(fields);
    verify(clientMapper, never()).updateClaimFieldsFromClient(org.mockito.Mockito.any(), org.mockito.Mockito.eq(fields));
  }

  @Test
  void shouldThrowWhenClaimNotFound() {
    final UUID submissionId = UUID.randomUUID();
    final UUID claimId = UUID.randomUUID();

    when(submissionClaimRepository.findByIdAndSubmissionId(claimId, submissionId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> claimService.getClaim(submissionId, claimId))
        .isInstanceOf(ClaimNotFoundException.class)
        .hasMessageContaining(claimId.toString())
        .hasMessageContaining(submissionId.toString());
  }

  @Test
  void shouldUpdateClaim() {
    final UUID submissionId = UUID.randomUUID();
    final UUID claimId = UUID.randomUUID();
    final SubmissionClaim claim = SubmissionClaim.builder().id(claimId).build();
    final ClaimPatch patch = new ClaimPatch();

    when(submissionClaimRepository.findByIdAndSubmissionId(claimId, submissionId))
        .thenReturn(Optional.of(claim));

    claimService.updateClaim(submissionId, claimId, patch);

    verify(submissionClaimMapper).updateSubmissionClaimFromPatch(patch, claim);
    verify(submissionClaimRepository).save(claim);
  }

  @Test
  void shouldThrowWhenClaimNotFoundOnUpdate() {
    final UUID submissionId = UUID.randomUUID();
    final UUID claimId = UUID.randomUUID();
    final ClaimPatch patch = new ClaimPatch();

    when(submissionClaimRepository.findByIdAndSubmissionId(claimId, submissionId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> claimService.updateClaim(submissionId, claimId, patch))
        .isInstanceOf(ClaimNotFoundException.class)
        .hasMessageContaining(claimId.toString())
        .hasMessageContaining(submissionId.toString());
  }

  @Test
  void shouldGetClaimsForSubmission() {
    final UUID submissionId = UUID.randomUUID();
    final SubmissionClaim claim = SubmissionClaim.builder().build();
    final GetSubmission200ResponseClaimsInner inner = new GetSubmission200ResponseClaimsInner();

    when(submissionClaimRepository.findBySubmissionId(submissionId)).thenReturn(List.of(claim));
    when(submissionClaimMapper.toGetSubmission200ResponseClaimsInner(claim)).thenReturn(inner);

    final List<GetSubmission200ResponseClaimsInner> result =
        claimService.getClaimsForSubmission(submissionId);

    assertThat(result).containsExactly(inner);
  }
}
