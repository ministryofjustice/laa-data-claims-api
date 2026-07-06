package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.AmendmentTestFixtures.AMENDED_FEE_CODE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.AmendmentTestFixtures.validPayload;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimAmendmentValidationStep;

/**
 * Proves the "no write when validation fails" guarantee for <b>every</b> validation step, one at a
 * time, against a real database.
 *
 * <p>The amendment flow validates with <b>no held transaction</b> - only {@link
 * ClaimAmendmentCommitService#commit} is {@code @Transactional}. When any step reports an error the
 * orchestrator returns a rejection and never calls commit, so there is no open transaction and
 * nothing is ever written (there is nothing to "roll back" - the write is simply not attempted).
 * The only true DB rollback is the {@code @Version} optimistic-lock failure at commit time, which
 * is a separate concern.
 *
 * <p>This test drives that invariant automatically across the whole {@link
 * ClaimAmendmentValidationService#STEP_ORDER}: for each step it substitutes just that step's slot
 * with a stub that forces a validation error, while every other slot runs the genuine step bean
 * against a valid payload (so the neighbours pass). It then asserts that no {@code claim_amendment}
 * row was written and the claim was not mutated. Because the parameters come from {@code
 * STEP_ORDER} itself, adding a new step automatically extends the coverage.
 */
@DisplayName("Amendment persists nothing when any single validation step fails")
class AmendmentValidationGateIntegrationTest extends AbstractIntegrationTest {

  // A distinct, non-fatal error forced onto whichever step is under test. Non-fatal so the loop
  // still runs every other (genuine) step, maximising the exercised surface.
  private static final ClaimAmendmentValidationError FORCED_ERROR =
      ClaimAmendmentValidationError.of(
          ClaimAmendmentValidationCode.INVALID_USER_IDENTIFIER_MISSING);

  @Autowired private List<ClaimAmendmentValidationStep> discoveredSteps;
  @Autowired private ClaimAmendmentPreparationService preparationService;
  @Autowired private ClaimAmendmentCommitService commitService;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    seedClaimsData();
    claimAmendmentRepository.deleteAll();
  }

  private static Stream<Arguments> everyValidationStep() {
    return ClaimAmendmentValidationService.STEP_ORDER.stream()
        .map(stepClass -> arguments(stepClass.getSimpleName(), stepClass));
  }

  @ParameterizedTest(name = "{0} failing -> nothing persisted")
  @MethodSource("everyValidationStep")
  @Transactional
  @DisplayName("a failure in any single validation step persists nothing")
  void persistsNothingWhenStepFails(
      String stepName, Class<? extends ClaimAmendmentValidationStep> failingStep) {
    // Put the claim in an amendable state so every genuine neighbour step passes for a valid
    // payload.
    Claim claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    claim.setStatus(ClaimStatus.VALID);
    long amendmentsBefore = claimAmendmentRepository.count();

    ClaimAmendmentService service = serviceWithFailingStep(failingStep);

    ClaimAmendmentResult result = service.submitAmendment(CLAIM_1_ID, validPayload());

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.errors()).contains(FORCED_ERROR);

    // Force any buffered inserts/updates out, then confirm nothing amendment-related was written.
    entityManager.flush();
    entityManager.clear();

    assertThat(claimAmendmentRepository.count()).isEqualTo(amendmentsBefore);
    Claim reloaded = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    assertThat(reloaded.isAmended()).isFalse();
    assertThat(reloaded.getFeeCode()).isNotEqualTo(AMENDED_FEE_CODE);
  }

  /**
   * Builds a {@link ClaimAmendmentService} whose validation sequence is the real, ordered set of
   * step beans with exactly one slot - {@code failingStep} - replaced by a stub that forces an
   * error. All other slots run their genuine bean.
   */
  private ClaimAmendmentService serviceWithFailingStep(
      Class<? extends ClaimAmendmentValidationStep> failingStep) {
    Map<Class<?>, ClaimAmendmentValidationStep> beanByClass =
        discoveredSteps.stream().collect(Collectors.toMap(Object::getClass, step -> step));

    ClaimAmendmentValidationStep[] steps =
        ClaimAmendmentValidationService.STEP_ORDER.stream()
            .map(
                stepClass ->
                    stepClass.equals(failingStep)
                        ? (ClaimAmendmentValidationStep) (state -> List.of(FORCED_ERROR))
                        : beanByClass.get(stepClass))
            .toArray(ClaimAmendmentValidationStep[]::new);

    return new ClaimAmendmentService(
        preparationService, new ClaimAmendmentValidationService(steps), commitService);
  }
}
