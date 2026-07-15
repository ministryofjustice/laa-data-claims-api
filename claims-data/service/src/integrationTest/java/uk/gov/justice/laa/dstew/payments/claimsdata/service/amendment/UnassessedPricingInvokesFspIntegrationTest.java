package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.AmendmentTestFixtures.*;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.helper.MockServerIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentExternalValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentFspValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimAmendmentValidationStep;

@DisplayName("Unassessed pricing amendment invokes FSP step (integration)")
class UnassessedPricingInvokesFspIntegrationTest extends MockServerIntegrationTest {

  @Autowired private List<ClaimAmendmentValidationStep> discoveredSteps;
  @Autowired private ClaimAmendmentPreparationService preparationService;
  @Autowired private ClaimAmendmentCommitService commitService;
  @Autowired private PlatformTransactionManager transactionManager;

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
  void unassessedPricingInvokesFspStep() {
    // Ensure claims exist but no assessment data is present for the claim (unassessed case).
    // seedClaimsData creates the claim fixtures; we intentionally do NOT call seedAssessmentsData()
    // so the claim remains unassessed. This is committed (test is not @Transactional).
    seedClaimsData();

    // The claim must be in an amendable state (ClaimStatusValidationStep only allows VALID),
    // otherwise the pipeline short-circuits before reaching the FSP step.
    Claim amendable = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    amendable.setStatus(ClaimStatus.VALID);
    claimRepository.saveAndFlush(amendable);

    // Build a pricing-impacting payload: change netProfitCostsAmount
    ClaimAmendmentPayload payload =
        ClaimAmendmentPayload.builder()
            .amendmentRequestedBy(JsonNullable.of(REQUESTED_BY_PROVIDER))
            .amendmentReasonCode(JsonNullable.of(REASON_PROVIDER_ERROR))
            .amendmentUserId(JsonNullable.of(VALID_USER_UUID))
            .netProfitCostsAmount(JsonNullable.of(BigDecimal.valueOf(200)))
            .build();

    // Replace the FSP validation step with a mock so we can assert it was invoked. Also replace the
    // external validation step with a mock so the pipeline never makes real fee-scheme/PDA HTTP
    // calls - this test asserts only that the FSP step is reached for a pricing change.
    Map<Class<?>, ClaimAmendmentValidationStep> beanByClass =
        discoveredSteps.stream()
            .collect(
                Collectors.toMap(
                    AopUtils::getTargetClass, step -> step, (existing, ignored) -> existing));

    ClaimAmendmentValidationStep mockFsp = mock(AmendmentFspValidationStep.class);
    beanByClass.put(AmendmentFspValidationStep.class, mockFsp);
    beanByClass.put(
        AmendmentExternalValidationStep.class, mock(AmendmentExternalValidationStep.class));

    ClaimAmendmentValidationStep[] steps =
        ClaimAmendmentValidationService.STEP_ORDER.stream()
            .map(beanByClass::get)
            .toArray(ClaimAmendmentValidationStep[]::new);

    ClaimAmendmentService service =
        new ClaimAmendmentService(
            preparationService, new ClaimAmendmentValidationService(steps), commitService);

    // Run inside a transaction with a freshly-loaded, managed claim (mirrors production).
    ClaimAmendmentResult result =
        new TransactionTemplate(transactionManager)
            .execute(
                status ->
                    service.submitAmendment(
                        claimRepository.findById(CLAIM_1_ID).orElseThrow(), payload));

    // Amendment should be accepted for unassessed pricing change
    assertThat(result).isNotNull();
    assertThat(result.isSuccess()).isTrue();

    // FSP validation step must have been invoked (orchestrator reaches it for pricing change)
    verify(mockFsp).validate(any());
  }
}
