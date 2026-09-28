package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ClientHttpConnector;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.config.ClientHttpConnectorCustomizer;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.audit.AuditingClientHttpConnector;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.audit.ExternalApiCallAuditService;

/**
 * Unit tests for audit wiring in {@link WebClientConfiguration}.
 *
 * <p>The customizer is what the validation library applied to the transport of each client it
 * builds, so these tests pin which clients are decorated and what the kill switch does. The
 * decision is made once, when the clients are built, which is why it is testable within a spring
 * context.
 */
@DisplayName("WebClientConfiguration audit wiring")
public class WebClientConfigurationTest {
  private WebClientConfiguration configuration;
  private ClaimsApiProperties properties;
  private ExternalApiCallAuditService auditService;
  private ObjectMapper objectMapper;
  private ClientHttpConnector delegate;

  @BeforeEach
  void setUp() {
    configuration = new WebClientConfiguration();
    properties = new ClaimsApiProperties();
    auditService = mock(ExternalApiCallAuditService.class);
    objectMapper = new ObjectMapper();
    delegate = mock(ClientHttpConnector.class);
  }

  @Nested
  @DisplayName("when audit is enabled")
  class WhenEnabled {
    @BeforeEach
    void setUp() {
      properties.getExternalApiAudit().setEnabled("true");
    }

    @Test
    @DisplayName("Decorates the fee-scheme client's transport")
    void decoratesFeeSchemeClient() {
      assertThat(customizer().customize(ClientHttpConnectorCustomizer.FEE_SCHEME, delegate))
          .isInstanceOf(AuditingClientHttpConnector.class);
    }

    @Test
    @DisplayName("Decorates the provider-details client's transport")
    void decoratesProviderDetailsClient() {
      assertThat(customizer().customize(ClientHttpConnectorCustomizer.PROVIDER_DETAILS, delegate))
          .isInstanceOf(AuditingClientHttpConnector.class);
    }

    @Test
    @DisplayName("Leaves the data claims client alone (no audit)")
    void leavesDataClaimsClientAlone() {
      assertThat(customizer().customize(ClientHttpConnectorCustomizer.DATA_CLAIMS, delegate))
          .isSameAs(delegate);
    }

    @Test
    @DisplayName("Leaves unknown clients alone (no audit)")
    void leavesUnknownClientsAlone() {
      assertThat(customizer().customize("unknown", delegate)).isSameAs(delegate);
    }
  }

  @Nested
  @DisplayName("When auditing is disabled")
  class WhenDisabled {
    @BeforeEach
    void disableAuditing() {
      properties.getExternalApiAudit().setEnabled("false");
    }

    @Test
    @DisplayName("Leaves the fee scheme client's transport untouched")
    void leavesFeeSchemeClientUntouched() {
      assertThat(customizer().customize(ClientHttpConnectorCustomizer.FEE_SCHEME, delegate))
          .isSameAs(delegate);
    }

    @Test
    @DisplayName("Leaves the provider details client's transport untouched")
    void leavesProviderDetailsClientUntouched() {
      assertThat(customizer().customize(ClientHttpConnectorCustomizer.FEE_SCHEME, delegate))
          .isSameAs(delegate);
    }
  }

  @Test
  @DisplayName("Auditing is on by default, so a missing setting still records")
  void auditingIsOnByDefault() {
    assertThat(new ClaimsApiProperties().getExternalApiAudit().isEnabled()).isTrue();
    assertThat(customizer().customize(ClientHttpConnectorCustomizer.FEE_SCHEME, delegate))
        .isInstanceOf(AuditingClientHttpConnector.class);
  }

  private ClientHttpConnectorCustomizer customizer() {
    return configuration.auditingConnectorCustomizer(properties, auditService, objectMapper);
  }
}
