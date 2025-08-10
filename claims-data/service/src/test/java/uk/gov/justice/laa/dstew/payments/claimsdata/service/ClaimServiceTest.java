package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
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
    UUID submissionId = UUID.randomUUID();
    Submission submission = Submission.builder().id(submissionId).build();
    ClaimPost post = new ClaimPost();
    SubmissionClaim claim = SubmissionClaim.builder().build();
    Client client = Client.builder().clientForename("John").build();

    when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
    when(submissionClaimMapper.toSubmissionClaim(post)).thenReturn(claim);
    when(clientMapper.toClient(post)).thenReturn(client);

    UUID id = claimService.createClaim(submissionId, post);

    assertThat(id).isNotNull();
    verify(submissionClaimRepository).save(claim);
    verify(clientRepository).save(client);
  }

  @Test
  void shouldGetClaim() {
    UUID submissionId = UUID.randomUUID();
    UUID claimId = UUID.randomUUID();
    SubmissionClaim claim = SubmissionClaim.builder().id(claimId).build();
    ClaimFields fields = new ClaimFields();
    Client client = Client.builder().clientForename("John").build();

    when(submissionClaimRepository.findByIdAndSubmissionId(claimId, submissionId))
        .thenReturn(Optional.of(claim));
    when(submissionClaimMapper.toClaimFields(claim)).thenReturn(fields);
    when(clientRepository.findByClaimId(claimId)).thenReturn(Optional.of(client));

    ClaimFields result = claimService.getClaim(submissionId, claimId);

    assertThat(result).isSameAs(fields);
    verify(clientMapper).updateClaimFieldsFromClient(client, fields);
  }

  @Test
  void shouldUpdateClaim() {
    UUID submissionId = UUID.randomUUID();
    UUID claimId = UUID.randomUUID();
    SubmissionClaim claim = SubmissionClaim.builder().id(claimId).build();
    ClaimPatch patch = new ClaimPatch();

    when(submissionClaimRepository.findByIdAndSubmissionId(claimId, submissionId))
        .thenReturn(Optional.of(claim));

    claimService.updateClaim(submissionId, claimId, patch);

    verify(submissionClaimMapper).updateSubmissionClaimFromPatch(patch, claim);
    verify(submissionClaimRepository).save(claim);
  }

  @Test
  void shouldGetClaimsForSubmission() {
    UUID submissionId = UUID.randomUUID();
    SubmissionClaim claim = SubmissionClaim.builder().build();
    GetSubmission200ResponseClaimsInner inner = new GetSubmission200ResponseClaimsInner();

    when(submissionClaimRepository.findBySubmissionId(submissionId))
        .thenReturn(List.of(claim));
    when(submissionClaimMapper.toGetSubmission200ResponseClaimsInner(claim)).thenReturn(inner);

    List<GetSubmission200ResponseClaimsInner> result =
        claimService.getClaimsForSubmission(submissionId);

    assertThat(result).containsExactly(inner);
  }
}
