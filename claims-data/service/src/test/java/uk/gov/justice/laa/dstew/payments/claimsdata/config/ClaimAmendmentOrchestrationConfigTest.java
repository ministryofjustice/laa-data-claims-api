package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentErrorCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.ClaimAmendmentValidationPipeline;

/**
 * Wiring tests for {@link ClaimAmendmentOrchestrationConfig}.
 *
 * <p>Verifies the {@code @ConditionalOnProperty} gating and that the assembled {@link
 * ClaimAmendmentValidationPipeline} bean has the real {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.EligibilityValidationStep} wired
 * in ahead of the placeholder steps - a VOID claim is rejected and a VALID claim passes. Uses
 * {@link ApplicationContextRunner} so no database or full application context is needed (the
 * pipeline is pure and in-memory).
 */
@DisplayName("ClaimAmendmentOrchestrationConfig Tests")
class ClaimAmendmentOrchestrationConfigTest {

  private static final String ENABLED_PROPERTY = "claim.amendment.orchestration.enabled";

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(ClaimAmendmentOrchestrationConfig.class);

  private static ClaimAmendmentState stateWithStatus(ClaimStatus status) {
    return ClaimAmendmentState.builder()
        .beforeState(ClaimStateSnapshot.builder().status(status).build())
        .build();
  }

  @Test
  @DisplayName("Pipeline bean is created when orchestration is enabled")
  void pipelineBeanCreatedWhenEnabled() {
    contextRunner
        .withPropertyValues(ENABLED_PROPERTY + "=true")
        .run(context -> assertThat(context).hasSingleBean(ClaimAmendmentValidationPipeline.class));
  }

  @Test
  @DisplayName("Pipeline bean is absent when the property is missing")
  void pipelineBeanAbsentWhenPropertyMissing() {
    contextRunner.run(
        context -> assertThat(context).doesNotHaveBean(ClaimAmendmentValidationPipeline.class));
  }

  @Test
  @DisplayName("Pipeline bean is absent when orchestration is explicitly disabled")
  void pipelineBeanAbsentWhenDisabled() {
    contextRunner
        .withPropertyValues(ENABLED_PROPERTY + "=false")
        .run(
            context -> assertThat(context).doesNotHaveBean(ClaimAmendmentValidationPipeline.class));
  }

  @Test
  @DisplayName("Assembled pipeline rejects a VOID claim via the wired eligibility step")
  void assembledPipelineRejectsVoidClaim() {
    contextRunner
        .withPropertyValues(ENABLED_PROPERTY + "=true")
        .run(
            context -> {
              ClaimAmendmentValidationPipeline pipeline =
                  context.getBean(ClaimAmendmentValidationPipeline.class);

              List<ClaimAmendmentValidationError> result =
                  pipeline.validate(stateWithStatus(ClaimStatus.VOID));

              assertThat(result)
                  .singleElement()
                  .extracting(ClaimAmendmentValidationError::getCode)
                  .isEqualTo(ClaimAmendmentErrorCode.INVALID_VOIDED_CLAIM_NOT_AMENDABLE);
            });
  }

  @Test
  @DisplayName("Assembled pipeline lets a VALID claim through every step")
  void assembledPipelineAllowsValidClaim() {
    contextRunner
        .withPropertyValues(ENABLED_PROPERTY + "=true")
        .run(
            context -> {
              ClaimAmendmentValidationPipeline pipeline =
                  context.getBean(ClaimAmendmentValidationPipeline.class);

              assertThat(pipeline.validate(stateWithStatus(ClaimStatus.VALID))).isEmpty();
            });
  }
}
