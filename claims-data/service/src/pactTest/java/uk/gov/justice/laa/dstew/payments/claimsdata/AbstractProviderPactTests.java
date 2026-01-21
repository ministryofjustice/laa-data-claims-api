package uk.gov.justice.laa.dstew.payments.claimsdata;

import jakarta.activation.DataSource;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.javers.spring.boot.sql.JaversSqlAutoConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.actuate.endpoint.web.PathMappedEndpoints;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.sqs.SqsClient;
import uk.gov.justice.laa.dstew.payments.claimsdata.aop.JaversAuditingAspect;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.BulkSubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimCaseRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClientRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.MatterStartRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationMessageLogRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.BulkSubmissionService;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.ClaimService;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.MatterStartService;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.SubmissionEventPublisherService;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.SubmissionService;
import uk.gov.laa.springboot.auth.TokenDetailsManager;

@EnableAutoConfiguration(
    exclude = {
      DataSourceAutoConfiguration.class,
      JaversSqlAutoConfiguration.class,
    })
@TestPropertySource(
    properties = {
      "laa.springboot.starter.auth.authentication-header=Authorization",
    })
public class AbstractProviderPactTests {

  @BeforeAll
  static void setupPactProperties() {
    System.setProperty("pactbroker.host", "localhost");
    System.setProperty("pactbroker.port", "9292");
    System.setProperty("pactbroker.scheme", "http");
  }

  @MockitoBean protected TokenDetailsManager tokenDetailsManager;

  @MockitoBean protected SecurityFilterChain securityFilterChain;

  @MockitoBean protected JaversAuditingAspect javersAuditingAspect;

  @MockitoBean protected BulkSubmissionRepository bulkSubmissionRepository;

  @MockitoBean protected CalculatedFeeDetailRepository calculatedFeeDetailRepository;

  @MockitoBean protected ClaimCaseRepository claimCaseRepository;

  @MockitoBean protected ClaimRepository claimRepository;

  @MockitoBean protected ClaimSummaryFeeRepository claimSummaryFeeRepository;

  @MockitoBean protected ClientRepository clientRepository;

  @MockitoBean protected MatterStartRepository matterStartRepository;

  @MockitoBean protected SubmissionRepository submissionRepository;

  @MockitoBean protected ValidationMessageLogRepository validationMessageLogRepository;

  @MockitoBean protected SqsClient sqsClient;

  @MockitoBean protected SubmissionEventPublisherService submissionEventPublisherService;

  @MockitoBean protected BulkSubmissionService bulkSubmissionService;

  @MockitoBean protected ClaimService claimService;

  @MockitoBean protected MatterStartService matterStartService;

  @MockitoBean protected SubmissionService submissionService;

  // Mocked various DB beans to allow application to run properly without dependencies
  @MockitoBean protected Flyway flyway;
  @MockitoBean protected EntityManagerFactory entityManagerFactory;
  @MockitoBean protected PathMappedEndpoints pathMappedEndpoints;
  @MockitoBean protected DataSource dataSource;
}
