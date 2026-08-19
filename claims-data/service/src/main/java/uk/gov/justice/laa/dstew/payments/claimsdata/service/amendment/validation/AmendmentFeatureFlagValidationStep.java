package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.ClaimsApiProperties;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;

/**
 * Feature-flag gate for the synchronous claim amendment flow.
 *
 * <p>Reads the typed {@link ClaimsApiProperties#getAmendments()} setting (bound from {@code
 * laa.claims.api.amendments.enabled}). The default and binding rules live in {@link
 * ClaimsApiProperties}, keeping all Claims API configuration in one discoverable place.
 *
 * <ul>
 *   <li>enabled {@code true} -&gt; the step passes and amendment processing continues;
 *   <li>enabled {@code false} -&gt; the step fails with a fatal {@link
 *       ClaimAmendmentValidationCode#INVALID_AMENDMENTS_FEATURE_DISABLED}, halting the flow so no
 *       later step runs and nothing is saved.
 * </ul>
 *
 * <p>It is placed first in {@code ClaimAmendmentValidationService.STEP_ORDER} so a disabled feature
 * short-circuits the pipeline before any other work is done.
 */
@Slf4j
@Component
public class AmendmentFeatureFlagValidationStep implements ClaimAmendmentValidationStep {

  private final ClaimsApiProperties claimsApiProperties;

  public AmendmentFeatureFlagValidationStep(ClaimsApiProperties claimsApiProperties) {
    this.claimsApiProperties = claimsApiProperties;
  }

  @Override
  public List<ClaimAmendmentValidationError> validate(ClaimAmendmentState state) {
    if (claimsApiProperties.getAmendments().isEnabled()) {
      log.debug("Amendments feature is enabled; continuing.");
      return List.of();
    }

    log.warn("Amendments feature is disabled; rejecting amendment request.");
    return List.of(
        ClaimAmendmentValidationError.of(
            ClaimAmendmentValidationCode.INVALID_AMENDMENTS_FEATURE_DISABLED));
  }
}
