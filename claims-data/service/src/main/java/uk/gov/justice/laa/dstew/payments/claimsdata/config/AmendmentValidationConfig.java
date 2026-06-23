package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentValidationSteps;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimAmendmentValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimStatusValidationStep;

/**
 * Defines the order of the claim amendment validation steps in one place, instead of spreading it
 * across the step classes as {@code @Order} annotations.
 *
 * <p>{@link #STEP_ORDER} is the single source of truth for the sequence. Spring discovers every
 * {@link ClaimAmendmentValidationStep} bean; this config sorts them into that order and exposes
 * them as an {@link AmendmentValidationSteps} bean for {@code ClaimAmendmentService} to inject.
 * Startup fails fast if the discovered steps do not exactly match {@code STEP_ORDER} - i.e. a step
 * is missing from the order, or an order entry has no bean - so the order cannot silently drift
 * from the implemented steps.
 *
 * <p>The full canonical sequence (steps are added as their tickets land) is:
 *
 * <ol>
 *   <li>DSTEW-1751/1752 claim-version contract
 *   <li>DSTEW-1764 claim-status eligibility
 *   <li>DSTEW-1765 metadata validation
 *   <li>DSTEW-1766 changed-field classification
 *   <li>DSTEW-1767 amendability / assessed-claim
 *   <li>DSTEW-1768 fee-code lookup and gates
 *   <li>DSTEW-176x PDA call (external)
 *   <li>DSTEW-1769 duplicate validation
 *   <li>DSTEW-1770 validation outcome check
 *   <li>DSTEW-176x FSP trigger/call (external)
 *   <li>DSTEW-1753/1754 final version guard
 * </ol>
 */
@Configuration
public class AmendmentValidationConfig {

  /** Canonical amendment validation order; add each step here, in position, as it is built. */
  static final List<Class<? extends ClaimAmendmentValidationStep>> STEP_ORDER =
      List.of(ClaimStatusValidationStep.class);

  /**
   * Builds the ordered validation step sequence in {@link #STEP_ORDER}, for {@code
   * ClaimAmendmentService} to inject.
   *
   * @param discoveredSteps every validation step bean, in arbitrary (Spring-determined) order
   * @return the validation steps wrapped in canonical order
   */
  @Bean
  AmendmentValidationSteps amendmentValidationSteps(
      List<ClaimAmendmentValidationStep> discoveredSteps) {
    return new AmendmentValidationSteps(ordered(discoveredSteps));
  }

  private static List<ClaimAmendmentValidationStep> ordered(
      List<ClaimAmendmentValidationStep> discoveredSteps) {
    Set<Class<?>> discovered =
        discoveredSteps.stream().map(Object::getClass).collect(Collectors.toSet());
    if (!discovered.equals(Set.copyOf(STEP_ORDER))) {
      throw new IllegalStateException(
          "Claim amendment validation steps do not match the declared order in "
              + "AmendmentValidationConfig.STEP_ORDER. Declared="
              + STEP_ORDER
              + ", discovered="
              + discovered);
    }
    return discoveredSteps.stream()
        .sorted(Comparator.comparingInt(step -> STEP_ORDER.indexOf(step.getClass())))
        .toList();
  }
}
