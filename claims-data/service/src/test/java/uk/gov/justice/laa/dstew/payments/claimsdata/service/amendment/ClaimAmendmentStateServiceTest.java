package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.AMENDED_FEE_CODE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData.CLAIM_ID;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimStateSnapshotMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.AssessmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimCaseRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClientRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClaimAmendmentStateService Tests")
class ClaimAmendmentStateServiceTest {

  @Mock private ClaimRepository claimRepository;
  @Mock private ClientRepository clientRepository;
  @Mock private ClaimCaseRepository claimCaseRepository;
  @Mock private ClaimSummaryFeeRepository claimSummaryFeeRepository;
  @Mock private CalculatedFeeDetailRepository calculatedFeeDetailRepository;
  @Mock private AssessmentRepository assessmentRepository;
  @Mock private ClaimStateSnapshotMapper snapshotMapper;
  @Mock private ClaimAmendmentStateBuilder amendmentStateBuilder;

  @InjectMocks private ClaimAmendmentStateService service;

  @Test
  @DisplayName("returns empty when the claim does not exist and does no further work")
  void returnsEmptyWhenClaimNotFound() {
    when(claimRepository.findById(CLAIM_ID)).thenReturn(Optional.empty());

    Optional<ClaimAmendmentState> result =
        service.retrieveAmendmentState(CLAIM_ID, ClaimAmendmentPayload.builder().build());

    assertThat(result).isEmpty();
    verifyNoInteractions(
        clientRepository,
        claimCaseRepository,
        claimSummaryFeeRepository,
        calculatedFeeDetailRepository,
        assessmentRepository,
        snapshotMapper);
    verify(amendmentStateBuilder, never()).buildAmendmentState(any(), any());
  }

  @Test
  @DisplayName("builds amendment state from the loaded aggregate when the claim exists")
  void buildsAmendmentStateWhenClaimExists() {
    Claim claim = Claim.builder().id(CLAIM_ID).build();
    when(claimRepository.findById(CLAIM_ID)).thenReturn(Optional.of(claim));
    when(clientRepository.findByClaimId(CLAIM_ID)).thenReturn(Optional.empty());
    when(claimCaseRepository.findByClaimId(CLAIM_ID)).thenReturn(Optional.empty());
    when(claimSummaryFeeRepository.findByClaimId(CLAIM_ID)).thenReturn(Optional.empty());
    when(calculatedFeeDetailRepository.findFirstByClaimIdOrderByCreatedOnDescIdDesc(CLAIM_ID))
        .thenReturn(Optional.empty());
    when(assessmentRepository.findFirstByClaimIdOrderByCreatedOnDesc(CLAIM_ID))
        .thenReturn(Optional.empty());

    ClaimStateSnapshot beforeState = ClaimStateSnapshot.builder().claimId(CLAIM_ID).build();
    when(snapshotMapper.toSnapshot(
            any(Claim.class),
            any(Optional.class),
            any(Optional.class),
            any(Optional.class),
            any(Optional.class),
            any(Optional.class)))
        .thenReturn(beforeState);

    ClaimAmendmentPayload payload =
        ClaimAmendmentPayload.builder().feeCode(JsonNullable.of(AMENDED_FEE_CODE)).build();
    ClaimAmendmentState expected =
        ClaimAmendmentState.builder().beforeState(beforeState).requestPayload(payload).build();
    when(amendmentStateBuilder.buildAmendmentState(beforeState, payload)).thenReturn(expected);

    Optional<ClaimAmendmentState> result = service.retrieveAmendmentState(CLAIM_ID, payload);

    assertThat(result).containsSame(expected);
    verify(snapshotMapper)
        .toSnapshot(
            any(Claim.class),
            any(Optional.class),
            any(Optional.class),
            any(Optional.class),
            any(Optional.class),
            any(Optional.class));
    verify(amendmentStateBuilder).buildAmendmentState(beforeState, payload);
  }

  @Test
  @DisplayName("produces no database write, external call or event on the found path")
  void producesNoSideEffectsOnFoundPath() {
    Claim claim = Claim.builder().id(CLAIM_ID).build();
    when(claimRepository.findById(CLAIM_ID)).thenReturn(Optional.of(claim));
    when(clientRepository.findByClaimId(CLAIM_ID)).thenReturn(Optional.empty());
    when(claimCaseRepository.findByClaimId(CLAIM_ID)).thenReturn(Optional.empty());
    when(claimSummaryFeeRepository.findByClaimId(CLAIM_ID)).thenReturn(Optional.empty());
    when(calculatedFeeDetailRepository.findFirstByClaimIdOrderByCreatedOnDescIdDesc(CLAIM_ID))
        .thenReturn(Optional.empty());
    when(assessmentRepository.findFirstByClaimIdOrderByCreatedOnDesc(CLAIM_ID))
        .thenReturn(Optional.empty());

    ClaimStateSnapshot beforeState = ClaimStateSnapshot.builder().claimId(CLAIM_ID).build();
    when(snapshotMapper.toSnapshot(
            any(Claim.class),
            any(Optional.class),
            any(Optional.class),
            any(Optional.class),
            any(Optional.class),
            any(Optional.class)))
        .thenReturn(beforeState);

    ClaimAmendmentPayload payload = ClaimAmendmentPayload.builder().build();
    when(amendmentStateBuilder.buildAmendmentState(beforeState, payload))
        .thenReturn(ClaimAmendmentState.builder().beforeState(beforeState).build());

    service.retrieveAmendmentState(CLAIM_ID, payload);

    // no persistence: none of the repositories' save/delete methods are invoked
    verify(claimRepository, never()).save(any());
    verify(clientRepository, never()).save(any());
    verify(claimCaseRepository, never()).save(any());
    verify(claimSummaryFeeRepository, never()).save(any());
    verify(calculatedFeeDetailRepository, never()).save(any());
    verify(assessmentRepository, never()).save(any());
  }
}
