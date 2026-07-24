package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.AmendmentTestFixtures.REASON_PROVIDER_ERROR;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.AmendmentTestFixtures.REQUESTED_BY_PROVIDER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.AmendmentTestFixtures.VALID_USER_UUID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentExternalValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimAmendmentValidationStep;

@DisplayName("No-op amendment halts early with a 204 outcome and persists nothing (integration)")
class AmendmentNoChangeIntegrationTest extends AbstractAmendmentPipelineIntegrationTest {

  @BeforeEach
  void seedAndReset() throws IOException {
    seedClaimsData();
    claimAmendmentRepository.deleteAll();
  }

  @Test
  void noOpAmendmentHaltsBeforeExternalStepsAndPersistsNothing() {
    // Seed CLAIM_1 into the amendable VALID state (committed; test is not @Transactional).
    Claim amendable = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    amendable.setStatus(ClaimStatus.VALID);
    claimRepository.saveAndFlush(amendable);

    long amendmentsBefore = claimAmendmentRepository.count();

    // Metadata-only payload: valid requested-by/reason/user id, but no claim field changes.
    ClaimAmendmentPayload payload =
        ClaimAmendmentPayload.builder()
            .amendmentRequestedBy(JsonNullable.of(REQUESTED_BY_PROVIDER))
            .amendmentReasonCode(JsonNullable.of(REASON_PROVIDER_ERROR))
            .amendmentUserId(JsonNullable.of(VALID_USER_UUID))
            .build();

    // Spy the external (PDA) step so we can prove the no-change guard short-circuits before it
    // runs.
    Pipeline pipeline = amendmentPipeline();
    ClaimAmendmentValidationStep spiedExternal =
        spy(pipeline.realStep(AmendmentExternalValidationStep.class));
    ClaimAmendmentService service =
        pipeline.replaceStep(AmendmentExternalValidationStep.class, spiedExternal).build();

    ClaimAmendmentResult result = submitInNewTransaction(service, CLAIM_1_ID, payload);

    // The no-op is rejected by the pipeline with the fatal 204 no-change code...
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.getCode())
                  .isEqualTo(
                      ClaimAmendmentValidationCode.NO_AMENDMENT_CHANGES_SUBMITTED.toString());
              assertThat(error.isFatal()).isTrue();
            });

    // ...the external PDA step is never reached (proves the guard runs early)...
    verify(spiedExternal, never()).validate(any());

    // ...and nothing is persisted: no phantom claim_amendment row.
    assertThat(claimAmendmentRepository.count()).isEqualTo(amendmentsBefore);
  }
}
