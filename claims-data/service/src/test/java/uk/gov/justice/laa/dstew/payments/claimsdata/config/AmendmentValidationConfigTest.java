package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentValidationSteps;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimAmendmentValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimStatusValidationStep;

/**
 * Guards the centrally-declared amendment validation order in {@link AmendmentValidationConfig}.
 *
 * <p>Verifies that {@code STEP_ORDER} has no duplicates, lists exactly the step components present
 * on the classpath (so a new step cannot be forgotten and a removed one cannot linger), produces
 * the steps in the declared order, and fails fast when the discovered steps do not match.
 */
@DisplayName("Amendment validation config")
class AmendmentValidationConfigTest {

  private static final String STEP_PACKAGE =
      "uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation";

  private final AmendmentValidationConfig config = new AmendmentValidationConfig();

  @Test
  @DisplayName("declared order has no duplicates")
  void declaredOrderHasNoDuplicates() {
    assertThat(AmendmentValidationConfig.STEP_ORDER).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("declared order lists exactly the step components on the classpath")
  void declaredOrderMatchesStepComponents() throws ClassNotFoundException {
    assertThat(discoverStepClasses())
        .containsExactlyInAnyOrderElementsOf(AmendmentValidationConfig.STEP_ORDER);
  }

  @Test
  @DisplayName("orders the discovered steps into the declared sequence")
  void ordersStepsIntoDeclaredSequence() {
    AmendmentValidationSteps result =
        config.amendmentValidationSteps(List.of(new ClaimStatusValidationStep()));

    assertThat(result.steps())
        .extracting(Object::getClass)
        .containsExactlyElementsOf(AmendmentValidationConfig.STEP_ORDER);
  }

  @Test
  @DisplayName("builds the steps when the discovered set matches the declared order")
  void buildsStepsForMatchingDiscoveredSet() {
    assertThatCode(() -> config.amendmentValidationSteps(List.of(new ClaimStatusValidationStep())))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("fails fast when the discovered steps do not match the declared order")
  void failsFastWhenStepsDoNotMatch() {
    assertThatThrownBy(() -> config.amendmentValidationSteps(List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("do not match the declared order");
  }

  private static List<Class<?>> discoverStepClasses() throws ClassNotFoundException {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AssignableTypeFilter(ClaimAmendmentValidationStep.class));

    List<Class<?>> classes = new ArrayList<>();
    for (BeanDefinition candidate : scanner.findCandidateComponents(STEP_PACKAGE)) {
      classes.add(Class.forName(candidate.getBeanClassName()));
    }
    return classes;
  }
}
