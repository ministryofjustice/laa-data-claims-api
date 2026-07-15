package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.helper.MockServerIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimAmendmentValidationStep;

/**
 * Shared scaffolding for amendment-pipeline integration tests that need to assemble a {@link
 * ClaimAmendmentService} from the real, ordered validation-step beans with one or more slots
 * substituted (a spy to assert an internal step was reached, or a stub to force an error).
 *
 * <p>Extends {@link MockServerIntegrationTest} so the external fee-scheme/PDA calls made by the
 * genuine {@code AmendmentExternalValidationStep} run against the shared MockServer (via {@code
 * stubExternalValidationEndpoints()}) rather than being mocked out - keeping these tests aligned
 * with {@code ClaimAmendmentPdaCallIntegrationTest}.
 *
 * <p>The {@link Pipeline} builder consolidates the previously duplicated {@code discoveredSteps ->
 * beanByClass -> STEP_ORDER -> new ClaimAmendmentService} wiring that each amendment-pipeline test
 * was repeating.
 */
abstract class AbstractAmendmentPipelineIntegrationTest extends MockServerIntegrationTest {

  @Autowired private List<ClaimAmendmentValidationStep> discoveredSteps;
  @Autowired private ClaimAmendmentPreparationService preparationService;
  @Autowired private ClaimAmendmentCommitService commitService;
  @Autowired private PlatformTransactionManager transactionManager;

  /**
   * Starts a fresh pipeline from the real, discovered step beans. Substitute slots with {@link
   * Pipeline#replaceStep} and finish with {@link Pipeline#build}.
   *
   * @return a new pipeline builder seeded with the genuine step beans
   */
  protected Pipeline amendmentPipeline() {
    return new Pipeline();
  }

  /**
   * Runs {@code service.submitAmendment} inside a fresh transaction with a freshly-loaded, managed
   * claim - mirroring the production {@code ClaimService.updateClaim @Transactional} boundary so
   * the prepare step can navigate the claim's lazy associations while the commit phase persists in
   * its own {@code REQUIRES_NEW} transaction.
   *
   * @param service the assembled amendment service
   * @param claimId the claim to amend
   * @param payload the amendment payload
   * @return the amendment result
   */
  protected ClaimAmendmentResult submitInNewTransaction(
      ClaimAmendmentService service, UUID claimId, ClaimAmendmentPayload payload) {
    return new TransactionTemplate(transactionManager)
        .execute(
            status ->
                service.submitAmendment(claimRepository.findById(claimId).orElseThrow(), payload));
  }

  /** Builder that assembles a {@link ClaimAmendmentService} from substitutable step slots. */
  protected final class Pipeline {

    // Beans keyed by their target class - AopUtils unwraps any CGLIB/JDK proxy so lookups by the
    // concrete step type resolve correctly.
    private final Map<Class<?>, ClaimAmendmentValidationStep> stepsByClass =
        discoveredSteps.stream()
            .collect(
                Collectors.toMap(
                    AopUtils::getTargetClass, step -> step, (existing, ignored) -> existing));

    /**
     * Returns the genuine bean for the given step type (e.g. to wrap it in a spy).
     *
     * @param type the step type
     * @return the real step bean
     */
    public ClaimAmendmentValidationStep realStep(
        Class<? extends ClaimAmendmentValidationStep> type) {
      return stepsByClass.get(type);
    }

    /**
     * Substitutes the given step type's slot with the supplied replacement (mock, spy or stub).
     *
     * @param type the step type to replace
     * @param replacement the substitute step
     * @return this builder
     */
    public Pipeline replaceStep(
        Class<? extends ClaimAmendmentValidationStep> type,
        ClaimAmendmentValidationStep replacement) {
      stepsByClass.put(type, replacement);
      return this;
    }

    /**
     * Assembles the service with the steps in {@link ClaimAmendmentValidationService#STEP_ORDER},
     * honouring any substitutions.
     *
     * @return the assembled amendment service
     */
    public ClaimAmendmentService build() {
      ClaimAmendmentValidationStep[] ordered =
          ClaimAmendmentValidationService.STEP_ORDER.stream()
              .map(stepsByClass::get)
              .toArray(ClaimAmendmentValidationStep[]::new);
      return new ClaimAmendmentService(
          preparationService, new ClaimAmendmentValidationService(ordered), commitService);
    }
  }
}
