package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import java.util.List;

/**
 * The claim amendment validation steps in their canonical execution order, as assembled by {@code
 * AmendmentValidationConfig}.
 *
 * <p>A dedicated wrapper type (rather than a raw {@code List}) so {@code ClaimAmendmentService} can
 * inject the ordered sequence unambiguously, without colliding with Spring's collection injection
 * of the individual {@link ClaimAmendmentValidationStep} beans.
 *
 * @param steps the validation steps, in canonical order
 */
public record AmendmentValidationSteps(List<ClaimAmendmentValidationStep> steps) {}
