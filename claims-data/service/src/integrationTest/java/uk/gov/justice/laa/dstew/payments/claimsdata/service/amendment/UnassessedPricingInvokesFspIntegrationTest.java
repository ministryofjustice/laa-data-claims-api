package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.AmendmentTestFixtures.*;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;

import java.io.IOException;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentFspValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimAmendmentValidationStep;

@DisplayName("Unassessed pricing amendment invokes FSP step (integration)")
class UnassessedPricingInvokesFspIntegrationTest extends AbstractAmendmentPipelineIntegrationTest {

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
  void unassessedPricingInvokesFspStep() throws IOException {
    // Ensure claims exist but no assessment data is present for the claim (unassessed case).
    // seedClaimsData creates the claim fixtures; we intentionally do NOT call seedAssessmentsData()
    // so the claim remains unassessed. This is committed (test is not @Transactional).
    seedClaimsData();

    // The claim must be in an amendable state (ClaimStatusValidationStep only allows VALID),
    // otherwise the pipeline short-circuits before reaching the FSP step.
    Claim amendable = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    amendable.setStatus(ClaimStatus.VALID);
    claimRepository.saveAndFlush(amendable);

    // Stub the external fee-scheme/PDA endpoints so the genuine AmendmentExternalValidationStep
    // runs against MockServer (as in ClaimAmendmentPdaCallIntegrationTest), exercising the real
    // client integration while this test asserts the FSP step is reached.
    stubExternalValidationEndpoints();

    // Build a pricing-impacting payload: change netProfitCostsAmount
    ClaimAmendmentPayload payload =
        ClaimAmendmentPayload.builder()
            .amendmentRequestedBy(JsonNullable.of(REQUESTED_BY_PROVIDER))
            .amendmentReasonCode(JsonNullable.of(REASON_PROVIDER_ERROR))
            .amendmentUserId(JsonNullable.of(VALID_USER_UUID))
            .netProfitCostsAmount(JsonNullable.of(BigDecimal.valueOf(200)))
            .build();

    // Replace only the FSP validation step with a mock so we can assert it was reached for a
    // pricing
    // change; the genuine external step still runs against MockServer.
    //
    // TODO(DSTEW-1758-1762): once AmendmentFspValidationStep makes its real outbound FSP call and
    // writes the amendment-driven calculated-fee result, drop this whitebox mock and the manual
    // pipeline assembly: stub the FSP endpoint on MockServer and assert the observable outcome
    // (outbound-call count and/or the persisted calculated-fee row) instead, mirroring
    // ClaimAmendmentPdaCallIntegrationTest.
    ClaimAmendmentValidationStep mockFsp = mock(AmendmentFspValidationStep.class);
    ClaimAmendmentService service =
        amendmentPipeline().replaceStep(AmendmentFspValidationStep.class, mockFsp).build();

    // Run inside a transaction with a freshly-loaded, managed claim (mirrors production).
    ClaimAmendmentResult result = submitInNewTransaction(service, CLAIM_1_ID, payload);

    // Amendment should be accepted for unassessed pricing change
    assertThat(result).isNotNull();
    assertThat(result.isSuccess()).isTrue();

    // FSP validation step must have been invoked (orchestrator reaches it for pricing change)
    verify(mockFsp).validate(any());
  }
}
