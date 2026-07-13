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

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentFspValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimAmendmentValidationStep;

@DisplayName("Assessed non-pricing amendment produces no FSP pricing outcome (integration)")
class AssessedNonPricingFspIntegrationTest extends AbstractIntegrationTest {

  @Autowired private java.util.List<ClaimAmendmentValidationStep> discoveredSteps;
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
  void assessedNonPricingProducesNoFspPricingOutcome() {
    // Seed an assessed claim (claim1 has assessment data) and put it into an amendable state.
    // ClaimStatusValidationStep only allows VALID, so without this the pipeline would short-circuit
    // before reaching the assessed-pricing/FSP steps. This is committed (test is not
    // @Transactional).
    seedAssessmentsData();
    Claim amendable = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    amendable.setStatus(ClaimStatus.VALID);
    claimRepository.saveAndFlush(amendable);

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

    // Replace the real FSP validation step with a spy. The orchestrator runs every step in
    // STEP_ORDER, so validate() is still invoked; the "skip" of the external FSP call for a
    // non-pricing amendment is internal to the step (covered by its own ticket/tests). Here we
    // assert the observable outcome: no new calculated-fee (pricing) row is written.
    Map<Class<?>, ClaimAmendmentValidationStep> beanByClass =
        discoveredSteps.stream().collect(Collectors.toMap(Object::getClass, step -> step));

    ClaimAmendmentValidationStep fspStep = beanByClass.get(AmendmentFspValidationStep.class);
    ClaimAmendmentValidationStep spiedStep = spy(fspStep);
    beanByClass.put(AmendmentFspValidationStep.class, spiedStep);

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

    // Assessed claim + non-pricing-only change is accepted (not gated by the assessed-pricing
    // rule).
    assertThat(result).isNotNull();
    assertThat(result.isSuccess()).isTrue();

    // The FSP step runs as part of the ordered pipeline...
    verify(spiedStep, times(1)).validate(any());

    // ...but performs no pricing work for a non-pricing amendment: no new calculated-fee row.
    Optional<UUID> afterCalcId =
        calculatedFeeDetailRepository
            .findFirstByClaimIdOrderByCreatedOnDescIdDesc(CLAIM_1_ID)
            .map(CalculatedFeeDetail::getId);
    assertThat(afterCalcId).isEqualTo(beforeCalcId);
  }
}
