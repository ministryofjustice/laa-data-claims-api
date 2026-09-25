package uk.gov.justice.laa.dstew.payments.claimsdata.service.coercion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimAmendmentPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;

/**
 * Wiring tests for the TEMPORARY (DSTEW-2173) {@link StatusCoercer}. Confirms the
 * validated-pending-approval lifecycle is opt-in and legacy coercion remains the safe default. When
 * the feature is removed, delete this test too.
 */
@DisplayName("StatusCoercer configuration")
class StatusCoercerConfigTest {

  private static final String PROPERTY = "laa.claims.api.validated-pending-approval.enabled";

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(StatusCoercerConfig.class);

  @Test
  @DisplayName("uses legacy coercion when the property is absent")
  void usesLegacyCoercerWhenPropertyAbsent() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(StatusCoercer.class);
          assertThat(context.getBean(StatusCoercer.class)).isInstanceOf(LegacyStatusCoercer.class);
        });
  }

  @Test
  @DisplayName("uses legacy coercion when the property is explicitly false")
  void usesLegacyCoercerWhenPropertyFalse() {
    contextRunner
        .withPropertyValues(PROPERTY + "=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(StatusCoercer.class);
              assertThat(context.getBean(StatusCoercer.class))
                  .isInstanceOf(LegacyStatusCoercer.class);
            });
  }

  @Test
  @DisplayName("uses NO_OP when the property is true")
  void usesNoOpWhenPropertyTrue() {
    contextRunner
        .withPropertyValues(PROPERTY + "=true")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(LegacyStatusCoercer.class);
              assertThat(context.getBean(StatusCoercer.class)).isSameAs(StatusCoercer.NO_OP);
            });
  }

  @Test
  @DisplayName("uses legacy coercion when the property is invalid")
  void usesLegacyCoercerWhenPropertyInvalid() {
    contextRunner
        .withPropertyValues(PROPERTY + "=not-a-boolean")
        .run(
            context -> {
              assertThat(context).hasSingleBean(StatusCoercer.class);
              assertThat(context.getBean(StatusCoercer.class))
                  .isInstanceOf(LegacyStatusCoercer.class);
            });
  }

  @Test
  @DisplayName("NO_OP leaves every carrier untouched when the lifecycle is enabled")
  void noOpLeavesEverythingUntouched() {
    SubmissionPatch submissionPatch =
        new SubmissionPatch().status(SubmissionStatus.VALIDATED_PENDING_APPROVAL);
    BulkSubmissionPatch bulkPatch =
        new BulkSubmissionPatch().status(BulkSubmissionStatus.VALIDATED_PENDING_APPROVAL);
    ClaimAmendmentPatch claimPatch =
        new ClaimAmendmentPatch().status(ClaimStatus.VALIDATED_PENDING_APPROVAL);
    Submission submission =
        Submission.builder().status(SubmissionStatus.VALIDATED_PENDING_APPROVAL).build();

    StatusCoercer.NO_OP.coerce(submissionPatch);
    StatusCoercer.NO_OP.coerce(bulkPatch);
    StatusCoercer.NO_OP.coerce(claimPatch);
    StatusCoercer.NO_OP.coerce(submission);

    assertThat(submissionPatch.getStatus()).isEqualTo(SubmissionStatus.VALIDATED_PENDING_APPROVAL);
    assertThat(bulkPatch.getStatus()).isEqualTo(BulkSubmissionStatus.VALIDATED_PENDING_APPROVAL);
    assertThat(claimPatch.getStatus()).isEqualTo(ClaimStatus.VALIDATED_PENDING_APPROVAL);
    assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.VALIDATED_PENDING_APPROVAL);
  }
}
