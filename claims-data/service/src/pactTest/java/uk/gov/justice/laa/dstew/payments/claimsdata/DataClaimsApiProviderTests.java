package uk.gov.justice.laa.dstew.payments.claimsdata;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.getCalculatedFeeDetailBuilder;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.getClaimBuilder;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.getClaimCaseBuilder;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.getClaimSummaryFeeBuilder;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.getClientBuilder;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.getSubmission;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.TargetRequestFilter;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import java.util.Arrays;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@Slf4j
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Provider(value = "laa-data-claims-api")
@PactBroker
public class DataClaimsApiProviderTests extends AbstractProviderPactTests {


  @LocalServerPort
  private int port;

  @BeforeEach
  void setUp(PactVerificationContext context) {
    HttpTestTarget target = new HttpTestTarget("localhost", port);
    context.setTarget(target);
  }

  // TODO: Not working, come back as it's probably the most complicated one
  @State("the system is ready to process a valid bulk submission")
  public void setupBulkSubmissionState() {
    log.info("Setting up state: the system is ready to process a valid bulk submission");
  }

  @State("no submission exists")
  public void noSubmissionExists() {
    log.info("Setting up state: no submission exists");
    when(submissionRepository.findById(any())).thenReturn(Optional.empty());
    when(claimRepository.findById(any())).thenReturn(Optional.empty());
    when(matterStartRepository.findBySubmissionIdAndId(any(), any())).thenReturn(Optional.empty());
  }

  @State("no claim exists")
  public void noClaimExists() {
    log.info("Setting up state: no claim exists");
    when(claimRepository.findById(any())).thenReturn(Optional.empty());
  }

  @State("no matter starts exists")
  public void noMatterStarts() {
    log.info("Setting up state: no matter starts exists");
    when(matterStartRepository.findBySubmissionIdAndId(any(), any())).thenReturn(Optional.empty());
  }

  @State("a submission exists")
  public void aSubmissionExists() {
    log.info("Setting up state: a submission exists");
    when(submissionRepository.findById(any())).thenReturn(Optional.of(getSubmission()));
    when(claimRepository.findBySubmissionId(any())).thenReturn(
        Arrays.asList(getClaimBuilder().build()));
  }

  @State("a claim exists")
  public void aClaimExists() {
    log.info("Setting up state: a claim exists");
    when(claimRepository.findByIdAndSubmissionId(any(), any())).thenReturn(
        Optional.ofNullable(getClaimBuilder().build()));
    when(clientRepository.findByClaimId(any())).thenReturn(
        Optional.ofNullable(getClientBuilder().build()));
    when(claimSummaryFeeRepository.findByClaimId(any()))
        .thenReturn(Optional.of(getClaimSummaryFeeBuilder().build()));
    when(calculatedFeeDetailRepository.findByClaimId(any()))
        .thenReturn(Optional.of(getCalculatedFeeDetailBuilder().build()));
    when(claimCaseRepository.findByClaimId(any())).thenReturn(
        Optional.ofNullable(getClaimCaseBuilder().build()));
  }

  @TargetRequestFilter
  public void requestFilter(HttpRequest request) {
    request.addHeader("Authorization", "00000000-0000-0000-0000-000000000000");
  }

  @TestTemplate
  @ExtendWith(PactVerificationInvocationContextProvider.class)
  void pactVerificationTestTemplate(PactVerificationContext context) {
    context.verifyInteraction();
  }
}
