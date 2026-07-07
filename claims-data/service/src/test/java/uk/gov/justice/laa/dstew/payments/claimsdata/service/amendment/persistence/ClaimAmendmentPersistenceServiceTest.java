package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentDiff;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimAmendmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/** Tests for {@link ClaimAmendmentPersistenceService}. */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClaimAmendmentPersistenceService Tests")
class ClaimAmendmentPersistenceServiceTest {

  @Mock private ClaimAmendmentRepository claimAmendmentRepository;
  @Mock private AmendmentDiffAssembler diffAssembler;
  @Mock private AmendmentJsonWriter jsonWriter;
  @Mock private AmendmentEntitiesWriter entitiesWriter;
  @Mock private AmendmentCalculatedFeeWriter calculatedFeeWriter;

  @InjectMocks private ClaimAmendmentPersistenceService service;

  private static final UUID CLAIM_ID = Uuid7.timeBasedUuid();

  private static ClaimAmendmentState state() {
    ClaimAmendmentPayload payload =
        ClaimAmendmentPayload.builder()
            .amendmentRequestedBy(JsonNullable.of("PROVIDER"))
            .amendmentReasonCode(JsonNullable.of("PROVIDER_ERROR"))
            .amendmentUserId(JsonNullable.of("user-123"))
            .feeCode(JsonNullable.of("NEW_FEE"))
            .build();
    return ClaimAmendmentState.builder()
        .beforeState(ClaimStateSnapshot.builder().claimId(CLAIM_ID).feeCode("OLD_FEE").build())
        .postAmendmentState(
            ClaimStateSnapshot.builder().claimId(CLAIM_ID).feeCode("NEW_FEE").build())
        .requestPayload(payload)
        .build();
  }

  @Test
  @DisplayName(
      "assembles and inserts the claim_amendment row, applies claim writes and attaches fee")
  void persistsSuccessfulAmendment() {
    Claim claim = Claim.builder().id(CLAIM_ID).build();
    ClaimAmendmentState state = state();
    AmendmentDiff diff = AmendmentDiff.of(List.of());

    when(diffAssembler.assemble(state)).thenReturn(diff);
    when(jsonWriter.writeBeforeState(state.getBeforeState())).thenReturn("BEFORE_JSON");
    when(jsonWriter.writeRequestPayload(state.getRequestPayload())).thenReturn("PAYLOAD_JSON");
    when(jsonWriter.writeDiff(diff)).thenReturn("DIFF_JSON");
    when(claimAmendmentRepository.save(any(ClaimAmendment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ClaimAmendment saved = service.persistSuccessfulAmendment(claim, state);

    ArgumentCaptor<ClaimAmendment> captor = ArgumentCaptor.forClass(ClaimAmendment.class);
    verify(claimAmendmentRepository).save(captor.capture());
    ClaimAmendment inserted = captor.getValue();

    assertThat(inserted.getId()).isNotNull();
    assertThat(inserted.getClaim()).isSameAs(claim);
    assertThat(inserted.getRequestedByCode()).isEqualTo("PROVIDER");
    assertThat(inserted.getAmendmentReasonCode()).isEqualTo("PROVIDER_ERROR");
    assertThat(inserted.getCreatedByUserId()).isEqualTo("user-123");
    assertThat(inserted.getCreatedOn()).isNotNull();
    assertThat(inserted.getBeforeState()).isEqualTo("BEFORE_JSON");
    assertThat(inserted.getRequestPayload()).isEqualTo("PAYLOAD_JSON");
    assertThat(inserted.getDiff()).isEqualTo("DIFF_JSON");

    // Delegation to the target writer (which applies claim + related-entity values and marks the
    // claim amended) is verified in AmendmentEntitiesWriterTest.
    verify(entitiesWriter).applyAmendedValues(claim, state.getPostAmendmentState());
    verify(calculatedFeeWriter).attach(eq(saved), eq(state));
  }
}
