package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;

/**
 * Feature-flag gate for the synchronous claim amendment flow.
 *
 * <p>Evaluates the {@value #FEATURE_FLAG_PROPERTY} property (typically defined in {@code
 * application.yml}). When the property is absent it defaults to {@code false}, so amendments are
 * <b>off by default</b> and a deliberate configuration change is required to enable them.
 *
 * <ul>
 *   <li>flag {@code true} -&gt; the step passes and amendment processing continues;
 *   <li>flag {@code false} or missing -&gt; the step fails with a fatal {@link
 *       ClaimAmendmentValidationCode#INVALID_AMENDMENTS_FEATURE_DISABLED}, halting the flow so no
 *       later step runs and nothing is saved.
 * </ul>
 *
 * <p>It is placed first in {@code ClaimAmendmentService.STEP_ORDER} so a disabled feature
 * short-circuits the pipeline before any other work is done. The lookup is performed through the
 * Spring {@link Environment}, which is null-safe and lets this step distinguish an explicitly
 * configured value from the default; it can later be swapped for a dedicated configuration-
 * properties class without changing the step's contract.
 */
@Slf4j
@Component
public class AmendmentFeatureFlagValidationStep implements ClaimAmendmentValidationStep {

  /** Configuration property that enables (or disables) the Amendments feature. */
  static final String FEATURE_FLAG_PROPERTY = "claims.api.amendments.enabled";

  /** Value used when {@link #FEATURE_FLAG_PROPERTY} is not present in the configuration. */
  static final boolean DEFAULT_ENABLED = false;

  private final Environment environment;

  public AmendmentFeatureFlagValidationStep(Environment environment) {
    this.environment = environment;
  }

  @Override
  public List<ClaimAmendmentValidationError> validate(ClaimAmendmentState state) {
    boolean explicitlySet = environment.containsProperty(FEATURE_FLAG_PROPERTY);
    boolean enabled =
        environment.getProperty(FEATURE_FLAG_PROPERTY, Boolean.class, DEFAULT_ENABLED);

    if (explicitlySet) {
      log.debug(
          "Amendments feature flag '{}' explicitly set to {}.", FEATURE_FLAG_PROPERTY, enabled);
    } else {
      log.debug(
          "Amendments feature flag '{}' is not set; defaulting to {}.",
          FEATURE_FLAG_PROPERTY,
          DEFAULT_ENABLED);
    }

    if (enabled) {
      return List.of();
    }

    log.warn("Amendments feature is disabled; rejecting amendment request.");
    return List.of(
        ClaimAmendmentValidationError.of(
            ClaimAmendmentValidationCode.INVALID_AMENDMENTS_FEATURE_DISABLED));
  }
}
