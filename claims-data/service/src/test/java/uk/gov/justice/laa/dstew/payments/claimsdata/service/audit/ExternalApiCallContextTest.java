package uk.gov.justice.laa.dstew.payments.claimsdata.service.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

public class ExternalApiCallContextTest {
  private final ExternalApiCallContext context = new ExternalApiCallContext();

  @AfterEach
  void reset() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  @DisplayName("Resolves both ids from the amendment route variables")
  void resolvesAmendmentRouteIds() {
    UUID submissionId = UUID.randomUUID();
    UUID claimId = UUID.randomUUID();
    bind(Map.of("submission-id", submissionId.toString(), "claim-id", claimId.toString()));

    assertThat(context.get()).isEqualTo(new ExternalApiCallContext.Ids(claimId, submissionId));
  }

  @Test
  @DisplayName("Resolves the camel-case claimId spelling")
  void resolvesCamelCaseClaimId() {
    UUID claimId = UUID.randomUUID();
    bind(Map.of("claimId", claimId.toString()));

    assertThat(context.get()).isEqualTo(new ExternalApiCallContext.Ids(claimId, null));
  }

  @Test
  @DisplayName("Prefers the kebab-case spelling when both are present")
  void prefersKebabCaseWhenBothArePresent() {
    UUID kebab = UUID.randomUUID();
    UUID camel = UUID.randomUUID();
    bind(Map.of("claim-id", kebab.toString(), "claimId", camel.toString()));

    assertThat(context.get()).isEqualTo(new ExternalApiCallContext.Ids(kebab, null));
  }

  @Test
  @DisplayName("Ignores a value that is not a UUID")
  void ignoresNonUuid() {
    bind(Map.of("claim-id", "not-a-uuid", "submission-id", "also-not"));

    assertThat(context.get()).isEqualTo(ExternalApiCallContext.Ids.NONE);
  }

  @Test
  @DisplayName("Returns NONE when no request is bound to the thread")
  void noneOutsideRequest() {
    assertThat(context.get()).isEqualTo(ExternalApiCallContext.Ids.NONE);
  }

  @Test
  @DisplayName("Returns NONE when the bound request has no path variables")
  void noneWithoutVariables() {
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));

    assertThat(context.get()).isEqualTo(ExternalApiCallContext.Ids.NONE);
  }

  @Test
  @DisplayName("Returns NONE when the variables attribute is not a map")
  void noneWhenAttributesHasUnexpectedType() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, "not a map");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    assertThat(context.get()).isEqualTo(ExternalApiCallContext.Ids.NONE);
  }

  private static void bind(Map<String, String> vars) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, vars);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }
}
