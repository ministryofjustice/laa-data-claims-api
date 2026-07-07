package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.provider.AmendmentReferenceDataProvider;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentFeatureFlagValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentReferenceValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentUserIdValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimAmendmentValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimStatusValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimVersionValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.PreparedAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Tests for {@link ClaimAmendmentService#submitAmendment}: the phase orchestration (prepare,
 * validate - which includes the inline external steps - then commit).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClaimAmendmentService Tests")
class ClaimAmendmentServiceTest {

  @Mock private ClaimAmendmentPreparationService preparationService;
  @Mock private ClaimAmendmentValidationService validationService;
  @Mock private ClaimAmendmentCommitService commitService;

  @InjectMocks private ClaimAmendmentService service;

  private static final UUID CLAIM_ID = Uuid7.timeBasedUuid();
  private final ClaimAmendmentPayload payload = ClaimAmendmentPayload.builder().build();
  private final ClaimAmendmentState state = ClaimAmendmentState.builder().build();
  private final Claim claim = Claim.builder().id(CLAIM_ID).build();

  @Test
  @DisplayName("prepares, validates and commits, returning the saved amendment on success")
  void commitsWhenValidationPasses() {
    ClaimAmendment amendment = ClaimAmendment.builder().id(Uuid7.timeBasedUuid()).build();
    when(preparationService.prepare(CLAIM_ID, payload))
        .thenReturn(new PreparedAmendment(claim, state));
    when(validationService.validateAmendmentRequest(state)).thenReturn(List.of());
    when(commitService.commit(claim, state)).thenReturn(amendment);

    ClaimAmendmentResult result = service.submitAmendment(CLAIM_ID, payload);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.amendment()).isSameAs(amendment);
    verify(commitService).commit(claim, state);
  }

  @Test
  @DisplayName("rejects on validation errors without committing")
  void rejectsWhenValidationFails() {
    ClaimAmendmentValidationError error =
        ClaimAmendmentValidationError.of(
            ClaimAmendmentValidationCode.INVALID_USER_IDENTIFIER_MISSING);
    when(preparationService.prepare(CLAIM_ID, payload))
        .thenReturn(new PreparedAmendment(claim, state));
    when(validationService.validateAmendmentRequest(state)).thenReturn(List.of(error));

    ClaimAmendmentResult result = service.submitAmendment(CLAIM_ID, payload);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.errors()).containsExactly(error);
    verifyNoInteractions(commitService);
  }

  @Test
  @DisplayName("propagates ClaimNotFoundException from prepare without validating or committing")
  void throwsWhenClaimNotFound() {
    when(preparationService.prepare(CLAIM_ID, payload))
        .thenThrow(new ClaimNotFoundException("No claim found with id " + CLAIM_ID));

    assertThatThrownBy(() -> service.submitAmendment(CLAIM_ID, payload))
        .isInstanceOf(ClaimNotFoundException.class);

    verifyNoInteractions(validationService, commitService);
  }
}
