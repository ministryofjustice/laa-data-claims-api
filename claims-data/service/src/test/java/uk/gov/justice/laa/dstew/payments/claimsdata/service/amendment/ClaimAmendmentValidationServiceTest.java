package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.service.ValidationService;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.ClaimsApiProperties;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ValidationClaimMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.provider.AmendmentReferenceDataProvider;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence.AmendmentChangeDetector;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence.AmendmentDiffAssembler;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentExternalValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentFeatureFlagValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentFspValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentReferenceValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentUserIdValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AssessedClaimPricingValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.BeforeStatePresenceValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimAmendmentValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimStatusValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.FieldAmendabilityValidationStep;

/**
 * Tests for {@link ClaimAmendmentValidationService}.
 *
 * <p>Exercises the orchestration mechanics with the validation steps mocked: the orchestrator runs
 * each step in order, returns the collected errors, and short-circuits on a fatal error without
 * running later steps. It also covers how the discovered step beans are sorted into {@code
 * STEP_ORDER}. Each step rule is covered by that step's own test (e.g. the test for {@link
 * ClaimStatusValidationStep}).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClaimAmendmentValidationService Tests")
class ClaimAmendmentValidationServiceTest {

  @Mock private ClaimStatusValidationStep claimStatusValidationStep;
  @Mock private ClaimAmendmentValidationStep laterStep;
  @Mock private AmendmentReferenceDataProvider amendmentReferenceDataProvider;
  @Mock private ValidationService validationService;
  @Mock private AmendmentDiffAssembler diffAssembler;
  @Mock private ValidationClaimMapper validationClaimMapper;

  private static ClaimAmendmentValidationService orchestratorWith(
      ClaimAmendmentValidationStep... steps) {
    return new ClaimAmendmentValidationService(steps);
  }

  private static ClaimAmendmentState anyState() {
    return ClaimAmendmentState.builder().beforeState(ClaimStateSnapshot.builder().build()).build();
  }

  @Test
  @DisplayName("returns no errors when every step passes")
  void passesWhenAllStepsPass() {
    when(claimStatusValidationStep.validate(any())).thenReturn(List.of());

    assertThat(orchestratorWith(claimStatusValidationStep).validateAmendmentRequest(anyState()))
        .isEmpty();
  }

  @Test
  @DisplayName("returns the fatal error and stops when a step rejects")
  void shortCircuitsOnFatalError() {
    ClaimAmendmentValidationError fatal =
        ClaimAmendmentValidationError.of(
            ClaimAmendmentValidationCode.INVALID_VOIDED_CLAIM_NOT_AMENDABLE);
    when(claimStatusValidationStep.validate(any())).thenReturn(List.of(fatal));

    assertThat(orchestratorWith(claimStatusValidationStep).validateAmendmentRequest(anyState()))
        .containsExactly(fatal);
  }

  @Test
  @DisplayName("does not run later steps after a fatal error")
  void stopsRunningLaterStepsAfterFatal() {
    ClaimAmendmentValidationError fatal =
        ClaimAmendmentValidationError.of(
            ClaimAmendmentValidationCode.INVALID_VOIDED_CLAIM_NOT_AMENDABLE);
    when(claimStatusValidationStep.validate(any())).thenReturn(List.of(fatal));

    orchestratorWith(claimStatusValidationStep, laterStep).validateAmendmentRequest(anyState());

    verify(laterStep, never()).validate(any());
  }

  @Test
  @DisplayName("declared step order has no duplicates")
  void declaredOrderHasNoDuplicates() {
    assertThat(ClaimAmendmentValidationService.STEP_ORDER).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("sorts the discovered step beans into the declared order, ignoring extras")
  void sortsDiscoveredStepsIntoDeclaredOrder() {
    ClaimAmendmentValidationStep extraStep = state -> List.of();

    // Enable the amendments feature so the feature-flag step (first in STEP_ORDER) passes rather
    // than short-circuiting at step one.
    ClaimsApiProperties claimsApiProperties = new ClaimsApiProperties();
    claimsApiProperties.getAmendments().setEnabled("true");

    // Provide a bean for every declared step so ordered() can resolve STEP_ORDER. The status step
    // returns a fatal error for the empty state below, so the orchestrator short-circuits before
    // the later steps run.
    ClaimAmendmentValidationService service =
        new ClaimAmendmentValidationService(
            List.of(
                extraStep,
                new AmendmentFeatureFlagValidationStep(claimsApiProperties),
                new BeforeStatePresenceValidationStep(),
                new ClaimStatusValidationStep(),
                new AssessedClaimPricingValidationStep(new AmendmentChangeDetector()),
                new FieldAmendabilityValidationStep(diffAssembler),
                new AmendmentUserIdValidationStep(),
                new AmendmentReferenceValidationStep(amendmentReferenceDataProvider),
                new AmendmentExternalValidationStep(
                    validationService, diffAssembler, validationClaimMapper),
                new AmendmentFspValidationStep()));

    assertThatCode(() -> service.validateAmendmentRequest(anyState())).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("fails fast when a declared step has no matching bean")
  void failsFastWhenDeclaredStepHasNoBean() {
    assertThatThrownBy(() -> new ClaimAmendmentValidationService(List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No bean found for declared amendment validation step");
  }

  @Test
  @DisplayName("assessed-claim pricing rejection occurs before any FSP call")
  void assessedPricingRejectionPreventsFspCall() {
    // Build a state with an assessed claim and a pricing-impacting change (netProfitCostsAmount).
    ClaimStateSnapshot before =
        ClaimStateSnapshot.builder()
            .hasAssessment(true)
            .status(ClaimStatus.VALID)
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .netProfitCostsAmount(BigDecimal.valueOf(100))
            .build();

    ClaimStateSnapshot after =
        ClaimStateSnapshot.builder()
            .hasAssessment(true)
            .status(ClaimStatus.VALID)
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .netProfitCostsAmount(BigDecimal.valueOf(200))
            .build();

    var state = ClaimAmendmentState.builder().beforeState(before).postAmendmentState(after).build();

    // Arrange ordered steps: feature-flag (enabled), status, assessed-pricing (real), then a mocked
    // FSP.
    ClaimsApiProperties props = new ClaimsApiProperties();
    props.getAmendments().setEnabled("true");

    ClaimAmendmentValidationStep feature = new AmendmentFeatureFlagValidationStep(props);
    ClaimAmendmentValidationStep status = new ClaimStatusValidationStep();
    ClaimAmendmentValidationStep assessedPricing =
        new AssessedClaimPricingValidationStep(new AmendmentChangeDetector());
    ClaimAmendmentValidationStep fspMock = mock(ClaimAmendmentValidationStep.class);

    ClaimAmendmentValidationService service =
        new ClaimAmendmentValidationService(feature, status, assessedPricing, fspMock);

    var errors = service.validateAmendmentRequest(state);

    // The assessed-pricing step must return the pricing-specific fatal error.
    assertThat(errors)
        .extracting(ClaimAmendmentValidationError::getCode)
        .contains(
            ClaimAmendmentValidationCode.INVALID_PRICING_AMENDMENT_ON_ASSESSED_CLAIM.toString());

    // Ensure the FSP step (later in the sequence) was never invoked.
    verify(fspMock, never()).validate(any());
  }
}
