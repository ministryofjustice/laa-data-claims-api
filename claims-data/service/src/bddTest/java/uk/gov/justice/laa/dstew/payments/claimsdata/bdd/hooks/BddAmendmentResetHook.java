package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.hooks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

import io.cucumber.java.Before;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ClaimValidationResult;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationResult;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.service.ValidationService;
import uk.gov.justice.laa.dstew.payments.claimsdata.client.FeeSchemePlatformRestClient;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationResponse;
import uk.gov.justice.laa.fee.scheme.model.FeeDetailsResponseV2;

/**
 * Cucumber {@code @Before} glue that resets the amendment-harness mocks ({@link
 * FeeSchemePlatformRestClient} and {@link ValidationService}) and reapplies safe defaults before
 * every scenario.
 *
 * <p>Runs at {@code order = 0} — the earliest slot — so scenario-specific arming steps (in {@code
 * AmendmentHarnessCommonSteps}, T4) layer on top of a known baseline.
 *
 * <p><b>Why this exists</b>: {@link
 * org.springframework.test.context.bean.override.mockito.MockitoBean} fields declared in {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.bdd.BddAmendmentHarnessConfiguration} are injected
 * AFTER the {@code @Configuration}'s own lifecycle hooks fire, so defaults cannot be applied via
 * {@code @PostConstruct} in that class. This glue class runs when cucumber invokes it, by which
 * time the mock beans are guaranteed to be present.
 *
 * <p>Ticket: DSTEW-2301.
 */
@Slf4j
@RequiredArgsConstructor
public class BddAmendmentResetHook {

  private final FeeSchemePlatformRestClient feeSchemePlatformRestClient;
  private final ValidationService validationService;

  /**
   * Resets both mocks and reapplies default answers. Runs before every cucumber scenario.
   *
   * <p>Order zero — the harness reset must precede any scenario arming ({@code Given the FSP
   * service will …}) so those steps overwrite our defaults rather than being overwritten by them.
   */
  @Before(order = 0)
  public void resetAmendmentHarnessMocks() {
    reset(feeSchemePlatformRestClient, validationService);
    applyDefaults();
    log.debug("[DSTEW-2301] Amendment harness mocks reset + defaults applied");
  }

  private void applyDefaults() {
    doReturn(validSubmissionResult()).when(validationService).validateSubmission(any());
    doReturn(validSubmissionResult()).when(validationService).validateSubmission(any(), any());
    doReturn(validClaimResult()).when(validationService).validateClaim(any());
    doReturn(validClaimResult()).when(validationService).validateClaim(any(), any());
    doReturn(validClaimResult()).when(validationService).validateClaim(any(), any(), any());

    doReturn(ResponseEntity.ok(new FeeCalculationResponse()))
        .when(feeSchemePlatformRestClient)
        .calculateFee(any());
    doReturn(ResponseEntity.ok(new FeeDetailsResponseV2()))
        .when(feeSchemePlatformRestClient)
        .getFeeDetails(any());
  }

  /**
   * A {@link ValidationResult} carrying {@code valid=true} and no issues — the "happy" baseline.
   * The no-arg constructor leaves {@code valid=false} (Java default), which would cause every
   * submission BDD scenario that calls into {@link ValidationService} to 400 with an empty issues
   * list. Explicit {@code setValid(true)} keeps the pre-DSTEW-2301 non-amendment scenarios green.
   */
  private static ValidationResult validSubmissionResult() {
    ValidationResult result = new ValidationResult();
    result.setValid(true);
    return result;
  }

  /**
   * A {@link ClaimValidationResult} carrying {@code valid=true} and no issues — the "happy"
   * baseline for the amendment external-validation step and any other caller.
   */
  private static ClaimValidationResult validClaimResult() {
    ClaimValidationResult result = ClaimValidationResult.builder().build();
    result.setValid(true);
    return result;
  }
}

