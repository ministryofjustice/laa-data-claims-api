package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.ClaimAmendmentEligibilityValidator;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.ClaimAmendmentValidationPipeline;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.ClaimAmendmentValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.EligibilityValidationStep;

/**
 * Assembles the synchronous claim amendment validation pipeline in the agreed sequence.
 *
 * <p>The bean is only created when {@code claim.amendment.orchestration.enabled=true}. PDA, FSP,
 * the transaction boundary and the atomic save are orchestrator-level concerns and are not
 * represented here; their insertion points are noted inline below.
 */
@Configuration
@ConditionalOnProperty(name = "claim.amendment.orchestration.enabled", havingValue = "true")
public class ClaimAmendmentOrchestrationConfig {

  /**
   * Builds the ordered validation pipeline. Retrieval runs before the pipeline; PDA and FSP are
   * invoked by the orchestrator at the interleave points noted below.
   *
   * @param eligibilityValidator the claim-eligibility gate
   * @return the assembled validation pipeline
   */
  @Bean
  public ClaimAmendmentValidationPipeline claimAmendmentValidationPipeline(
      ClaimAmendmentEligibilityValidator eligibilityValidator) {
    return new ClaimAmendmentValidationPipeline(
        List.of(
            ClaimAmendmentValidationStep.noop(
                "DSTEW-1751/1752 claim-version contract and early gate"),
            new EligibilityValidationStep(eligibilityValidator),
            ClaimAmendmentValidationStep.noop("DSTEW-1765 metadata validation"),
            ClaimAmendmentValidationStep.noop("DSTEW-1766 changed-field classification"),
            ClaimAmendmentValidationStep.noop("DSTEW-1767 amendability and assessed-claim gates"),
            ClaimAmendmentValidationStep.noop(
                "DSTEW-1768 fee-code lookup and fee-code-enriched gates"),
            // --- orchestrator interleave: PDA call/skip ---
            ClaimAmendmentValidationStep.noop("DSTEW-1769 duplicate validation"),
            ClaimAmendmentValidationStep.noop("DSTEW-1770 validation outcome check"),
            // --- orchestrator interleave: FSP trigger/call/outcome ---
            ClaimAmendmentValidationStep.noop(
                "DSTEW-1753/1754 final version guard and conflict response")));
    // --- orchestrator: atomic save runs after the pipeline passes ---
  }
}
