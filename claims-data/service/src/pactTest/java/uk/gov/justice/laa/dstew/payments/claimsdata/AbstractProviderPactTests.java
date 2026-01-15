package uk.gov.justice.laa.dstew.payments.claimsdata;

import org.javers.spring.boot.sql.JaversSqlAutoConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
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
import uk.gov.justice.laa.dstew.payments.claimsdata.service.SubmissionEventPublisherService;
import uk.gov.laa.springboot.auth.TokenDetailsManager;

@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    FlywayAutoConfiguration.class,
    JaversSqlAutoConfiguration.class,
})
@TestPropertySource(properties = {
    "laa.springboot.starter.auth.authentication-header=Authorization",
})
public class AbstractProviderPactTests {

  @BeforeAll
  static void setupPactProperties() {
    System.setProperty("pactbroker.host", "localhost");
    System.setProperty("pactbroker.port", "9292");
    System.setProperty("pactbroker.scheme", "http");
  }

  @MockitoBean
  TokenDetailsManager tokenDetailsManager;

  @MockitoBean
  SecurityFilterChain securityFilterChain;

  @MockitoBean
  JaversAuditingAspect javersAuditingAspect;

  @MockitoBean
  BulkSubmissionRepository bulkSubmissionRepository;

  @MockitoBean
  CalculatedFeeDetailRepository calculatedFeeDetailRepository;

  @MockitoBean
  ClaimCaseRepository claimCaseRepository;

  @MockitoBean
  ClaimRepository claimRepository;

  @MockitoBean
  ClaimSummaryFeeRepository claimSummaryFeeRepository;

  @MockitoBean
  ClientRepository clientRepository;

  @MockitoBean
  MatterStartRepository matterStartRepository;

  @MockitoBean
  SubmissionRepository submissionRepository;

  @MockitoBean
  ValidationMessageLogRepository validationMessageLogRepository;

  @MockitoBean
  SqsClient sqsClient;

  @MockitoBean
  SubmissionEventPublisherService submissionEventPublisherService;
}
