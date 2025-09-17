package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClientMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionClaim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClientRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationMessageLogRepository;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {
  @Mock private SubmissionRepository submissionRepository;
  @Mock private ClaimRepository claimRepository;
  @Mock private ClientRepository clientRepository;
  @Mock private ClaimMapper claimMapper;
  @Mock private ClientMapper clientMapper;
  @Mock private ValidationMessageLogRepository validationMessageLogRepository;

  @InjectMocks private ClaimService claimService;

  @ParameterizedTest
  @MethodSource("getClientTestingArguments")
  void shouldCreateClaimAndClient(Client client) {
    final UUID submissionId = UUID.randomUUID();
    final Submission submission = Submission.builder().id(submissionId).build();
    final ClaimPost post = new ClaimPost();
    final Claim claim = Claim.builder().build();

    when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
    when(claimMapper.toClaim(post)).thenReturn(claim);
    when(clientMapper.toClient(post)).thenReturn(client);

    final UUID id = claimService.createClaim(submissionId, post);

    assertThat(id).isNotNull();
    assertThat(claim.getId()).isEqualTo(id);
    //  TODO: DSTEW-323 replace with the actual user ID/name when available
    assertThat(claim.getCreatedByUserId()).isEqualTo("todo");
    assertThat(client.getClaim()).isSameAs(claim);
    //  TODO: DSTEW-323 replace with the actual user ID/name when available
    assertThat(client.getCreatedByUserId()).isEqualTo("todo");
    verify(claimRepository).save(claim);
    verify(clientRepository).save(client);
  }

  public static Stream<Arguments> getClientTestingArguments() {
    return Stream.of(
        Arguments.of(Client.builder().clientForename("John").build()),
        Arguments.of(Client.builder().clientSurname("Smith").build()),
        Arguments.of(Client.builder().clientDateOfBirth(LocalDate.of(1980, 1, 1)).build()),
        Arguments.of(Client.builder().client2Forename("TestName").build()),
        Arguments.of(Client.builder().client2Surname("TestSurname").build()),
        Arguments.of(Client.builder().client2DateOfBirth(LocalDate.of(1983, 12, 12)).build()));
  }

  @Test
  void shouldCreateClaimWithoutClientWhenNoClientData() {
    final UUID submissionId = UUID.randomUUID();
    final Submission submission = Submission.builder().id(submissionId).build();
    final ClaimPost post = new ClaimPost();
    final Claim claim = Claim.builder().build();
    final Client emptyClient = Client.builder().build();

    when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
    when(claimMapper.toClaim(post)).thenReturn(claim);
    when(clientMapper.toClient(post)).thenReturn(emptyClient);

    final UUID id = claimService.createClaim(submissionId, post);

    assertThat(id).isNotNull();
    verify(claimRepository).save(claim);
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
    final Claim claim = Claim.builder().id(claimId).build();
    final ClaimResponse fields = new ClaimResponse();
    final Client client = Client.builder().clientForename("John").build();

    when(claimRepository.findByIdAndSubmissionId(claimId, submissionId))
        .thenReturn(Optional.of(claim));
    when(claimMapper.toClaimResponse(claim)).thenReturn(fields);
    when(clientRepository.findByClaimId(claimId)).thenReturn(Optional.of(client));

    final ClaimResponse result = claimService.getClaim(submissionId, claimId);

    assertThat(result).isSameAs(fields);
    verify(clientMapper).updateClaimResponseFromClient(client, fields);
  }

  @Test
  void shouldGetClaimWithoutClient() {
    final UUID submissionId = UUID.randomUUID();
    final UUID claimId = UUID.randomUUID();
    final Claim claim = Claim.builder().id(claimId).build();
    final ClaimResponse fields = new ClaimResponse();

    when(claimRepository.findByIdAndSubmissionId(claimId, submissionId))
        .thenReturn(Optional.of(claim));
    when(claimMapper.toClaimResponse(claim)).thenReturn(fields);
    when(clientRepository.findByClaimId(claimId)).thenReturn(Optional.empty());

    final ClaimResponse result = claimService.getClaim(submissionId, claimId);

    assertThat(result).isSameAs(fields);
    verify(clientMapper, never()).updateClaimResponseFromClient(any(), eq(fields));
  }

  @Test
  void shouldThrowWhenClaimNotFound() {
    final UUID submissionId = UUID.randomUUID();
    final UUID claimId = UUID.randomUUID();

    when(claimRepository.findByIdAndSubmissionId(claimId, submissionId))
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
    final Claim claim = Claim.builder().id(claimId).build();
    final ClaimPatch patch = new ClaimPatch();

    when(claimRepository.findByIdAndSubmissionId(claimId, submissionId))
        .thenReturn(Optional.of(claim));

    claimService.updateClaim(submissionId, claimId, patch);

    verify(claimMapper).updateSubmissionClaimFromPatch(patch, claim);
    verify(claimRepository).save(claim);
  }

  @Test
  void shouldThrowWhenClaimNotFoundOnUpdate() {
    final UUID submissionId = UUID.randomUUID();
    final UUID claimId = UUID.randomUUID();
    final ClaimPatch patch = new ClaimPatch();

    when(claimRepository.findByIdAndSubmissionId(claimId, submissionId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> claimService.updateClaim(submissionId, claimId, patch))
        .isInstanceOf(ClaimNotFoundException.class)
        .hasMessageContaining(claimId.toString())
        .hasMessageContaining(submissionId.toString());
  }

  @Test
  void shouldGetClaimsForSubmission() {
    final UUID submissionId = UUID.randomUUID();
    final Claim claim = Claim.builder().build();
    final SubmissionClaim inner = new SubmissionClaim();

    when(claimRepository.findBySubmissionId(submissionId)).thenReturn(List.of(claim));
    when(claimMapper.toSubmissionClaim(claim)).thenReturn(inner);

    final List<SubmissionClaim> result = claimService.getClaimsForSubmission(submissionId);

    assertThat(result).containsExactly(inner);
  }

  @Test
  void shouldUpdateClaimAndLogValidationErrors() {
    final UUID submissionId = UUID.randomUUID();
    final UUID claimId = UUID.randomUUID();
    final Claim claim =
        Claim.builder()
            .id(claimId)
            .submission(Submission.builder().id(submissionId).build())
            .build();
    final ClaimPatch patch = new ClaimPatch();
    final ValidationMessagePatch message1 = new ValidationMessagePatch();
    patch.setValidationMessages(List.of(message1));

    when(claimRepository.findByIdAndSubmissionId(claimId, submissionId))
        .thenReturn(Optional.of(claim));
    when(claimMapper.toValidationMessageLog(message1, claim))
        .thenReturn(new ValidationMessageLog());

    claimService.updateClaim(submissionId, claimId, patch);

    verify(claimMapper).updateSubmissionClaimFromPatch(any(), eq(claim));
    verify(claimRepository).save(claim);
    verify(claimMapper).toValidationMessageLog(message1, claim);
  }
}
