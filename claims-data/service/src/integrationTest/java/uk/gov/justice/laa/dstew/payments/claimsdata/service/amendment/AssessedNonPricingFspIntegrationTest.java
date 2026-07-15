package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.AmendmentTestFixtures.REASON_PROVIDER_ERROR;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.AmendmentTestFixtures.REQUESTED_BY_PROVIDER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.AmendmentTestFixtures.VALID_USER_UUID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentFspValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimAmendmentValidationStep;

@DisplayName("Assessed non-pricing amendment produces no FSP pricing outcome (integration)")
class AssessedNonPricingFspIntegrationTest extends AbstractAmendmentPipelineIntegrationTest {

  // This test commits a claim_amendment row (it is not @Transactional). Remove it afterwards so it
  // does not leak into later tests - notably ClaimAmendmentValidationServiceIntegrationTest bulk
  // deletes the governed amendment_reason_reference rows this row references (FK RESTRICT).
  @AfterEach
  void removeCommittedAmendments() {
    claimAmendmentRepository.deleteAll();
  }

  // Note: this test is intentionally NOT @Transactional. The commit phase runs in a
  // REQUIRES_NEW transaction, so the seeded claim must be committed (visible to that new
  // transaction) for a successful amendment to persist. The submitAmendment call is wrapped in a
  // TransactionTemplate (mirroring the production ClaimService.updateClaim @Transactional boundary)
  // so the claim is managed while the prepare step navigates its lazy associations. The
  // AbstractIntegrationTest @BeforeEach clears all data between tests.
  @Test
  void assessedNonPricingProducesNoFspPricingOutcome() throws IOException {
    // Seed an assessed claim (claim1 has assessment data) and put it into an amendable state.
    // ClaimStatusValidationStep only allows VALID, so without this the pipeline would short-circuit
    // before reaching the assessed-pricing/FSP steps. This is committed (test is not
    // @Transactional).
    seedAssessmentsData();
    Claim amendable = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    amendable.setStatus(ClaimStatus.VALID);
    claimRepository.saveAndFlush(amendable);

    // Stub the external fee-scheme/PDA endpoints so the genuine AmendmentExternalValidationStep
    // runs against MockServer (as in ClaimAmendmentPdaCallIntegrationTest); this test asserts only
    // the FSP step's (non-)pricing outcome.
    stubExternalValidationEndpoints();

    // Capture the latest calculated-fee row (if any) before the amendment so we can prove the FSP
    // step attaches no new pricing outcome for a non-pricing amendment.
    Optional<UUID> beforeCalcId =
        calculatedFeeDetailRepository
            .findFirstByClaimIdOrderByCreatedOnDescIdDesc(CLAIM_1_ID)
            .map(CalculatedFeeDetail::getId);

    // Non-pricing amendment payload: change client surname only.
    ClaimAmendmentPayload payload =
        ClaimAmendmentPayload.builder()
            .amendmentRequestedBy(JsonNullable.of(REQUESTED_BY_PROVIDER))
            .amendmentReasonCode(JsonNullable.of(REASON_PROVIDER_ERROR))
            .amendmentUserId(JsonNullable.of(VALID_USER_UUID))
            .clientSurname(JsonNullable.of("NewSurname"))
            .build();

    // Spy the real FSP validation step. The orchestrator runs every step in STEP_ORDER, so
    // validate() is still invoked; the "skip" of the external FSP call for a non-pricing amendment
    // is internal to the step (covered by its own ticket/tests). Here we assert the observable
    // outcome: no new calculated-fee (pricing) row is written. The genuine external step still runs
    // against MockServer.
    //
    // TODO(DSTEW-1758-1762): once AmendmentFspValidationStep makes its real outbound FSP call and
    // writes the amendment-driven calculated-fee result, drop this whitebox spy and the manual
    // pipeline assembly: stub the FSP endpoint on MockServer and assert the observable outcome
    // (that no outbound FSP call is made and no calculated-fee row is written) directly, mirroring
    // ClaimAmendmentPdaCallIntegrationTest.
    Pipeline pipeline = amendmentPipeline();
    ClaimAmendmentValidationStep spiedFsp =
        spy(pipeline.realStep(AmendmentFspValidationStep.class));
    ClaimAmendmentService service =
        pipeline.replaceStep(AmendmentFspValidationStep.class, spiedFsp).build();

    // Run inside a transaction with a freshly-loaded, managed claim (mirrors production).
    ClaimAmendmentResult result = submitInNewTransaction(service, CLAIM_1_ID, payload);

    // Assessed claim + non-pricing-only change is accepted (not gated by the assessed-pricing
    // rule).
    assertThat(result).isNotNull();
    assertThat(result.isSuccess()).isTrue();

    // The FSP step runs as part of the ordered pipeline...
    verify(spiedFsp, times(1)).validate(any());

    // ...but performs no pricing work for a non-pricing amendment: no new calculated-fee row.
    Optional<UUID> afterCalcId =
        calculatedFeeDetailRepository
            .findFirstByClaimIdOrderByCreatedOnDescIdDesc(CLAIM_1_ID)
            .map(CalculatedFeeDetail::getId);
    assertThat(afterCalcId).isEqualTo(beforeCalcId);
  }
}
