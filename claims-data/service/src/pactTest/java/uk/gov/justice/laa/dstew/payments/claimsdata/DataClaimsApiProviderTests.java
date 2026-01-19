package uk.gov.justice.laa.dstew.payments.claimsdata;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.BULK_SUBMISSION_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.getCalculatedFeeDetail;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.getClaim;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.getClaimCase;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.getClaimSummaryFee;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.getClient;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.getMatterStart;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.getSubmission;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.getValidationMessage;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.TargetRequestFilter;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionValidationException;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CreateBulkSubmission201Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;

@Slf4j
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Provider(value = "laa-data-claims-api")
@PactBroker
public class DataClaimsApiProviderTests extends AbstractProviderPactTests {

  @LocalServerPort private int port;

  @BeforeEach
  void setUp(PactVerificationContext context) {
    HttpTestTarget target = new HttpTestTarget("localhost", port);
    context.setTarget(target);
  }

  @State("the system is ready to process a valid bulk submission")
  public void setupBulkSubmissionState() {
    log.info("Setting up state: the system is ready to process a valid bulk submission");
    when(bulkSubmissionService.submitBulkSubmissionFile(any(), any(), any()))
        .thenReturn(
            CreateBulkSubmission201Response.builder()
                .bulkSubmissionId(BULK_SUBMISSION_ID)
                .submissionIds(Arrays.asList(SUBMISSION_ID))
                .build());
  }

  @State("the submission file contains invalid data")
  public void theSubmissionFileContainsInvalidData() {
    log.info("Setting up state: the submission file contains invalid data");
    doThrow(new BulkSubmissionValidationException("Error found"))
        .when(bulkSubmissionService)
        .submitBulkSubmissionFile(any(), any(), any());
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

  @State("a matter start exists")
  public void aMatterStartExists() {
    log.info("Setting up state: a matter start exists");
    when(matterStartRepository.findBySubmissionIdAndId(any(), any()))
        .thenReturn(Optional.of(getMatterStart()));
  }

  @State("no matter starts exists")
  public void noMatterStarts() {
    log.info("Setting up state: no matter starts exists");
    when(matterStartRepository.findBySubmissionIdAndId(any(), any())).thenReturn(Optional.empty());
    when(matterStartRepository.findBySubmissionId(any())).thenReturn(Collections.emptyList());
  }

  @State("a submission exists")
  public void aSubmissionExists() {
    log.info("Setting up state: a submission exists");
    when(submissionRepository.findById(any())).thenReturn(Optional.of(getSubmission()));
    when(claimRepository.findBySubmissionId(any())).thenReturn(Arrays.asList(getClaim()));
  }

  @State("a claim exists")
  public void aClaimExists() {
    log.info("Setting up state: a claim exists");
    when(claimRepository.findByIdAndSubmissionId(any(), any()))
        .thenReturn(Optional.ofNullable(getClaim()));
    when(clientRepository.findByClaimId(any())).thenReturn(Optional.ofNullable(getClient()));
    when(claimSummaryFeeRepository.findByClaimId(any()))
        .thenReturn(Optional.of(getClaimSummaryFee()));
    when(calculatedFeeDetailRepository.findByClaimId(any()))
        .thenReturn(Optional.of(getCalculatedFeeDetail()));
    when(claimCaseRepository.findByClaimId(any())).thenReturn(Optional.ofNullable(getClaimCase()));
  }

  @State("claims exist for the search criteria")
  public void aClaimExistsForSearchCriteria() {
    log.info("Setting up state: claim exist for the search criteria");
    when(claimRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl(Arrays.asList(getClaim(), getClaim())));
    when(claimRepository.findByIdAndSubmissionId(any(), any()))
        .thenReturn(Optional.ofNullable(getClaim()));
    when(clientRepository.findByClaimId(any())).thenReturn(Optional.ofNullable(getClient()));
    when(claimSummaryFeeRepository.findByClaimId(any()))
        .thenReturn(Optional.of(getClaimSummaryFee()));
    when(calculatedFeeDetailRepository.findByClaimId(any()))
        .thenReturn(Optional.of(getCalculatedFeeDetail()));
    when(claimCaseRepository.findByClaimId(any())).thenReturn(Optional.ofNullable(getClaimCase()));
  }

  @State("no claims exist for the search criteria")
  public void noClaimsExistForTheSearchCriteria() {
    log.info("Setting up state: no claims exist for the search criteria");
    when(claimRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl(Collections.emptyList()));
  }

  @State("no validation messages exist for the search criteria")
  public void noValidationMessagesExistForTheSearchCriteria() {
    log.info("Setting up state: no validation messages exist for the search criteria");
    when(validationMessageLogRepository.findAll(any(Example.class), any(Pageable.class)))
        .thenReturn(new PageImpl(Collections.emptyList()));
  }

  @State("validation messages exist for the search criteria")
  public void validationMessagesExistForTheSearchCriteria() {
    log.info("Setting up state: validation messages exist for the search criteria");
    when(validationMessageLogRepository.findAll(any(Example.class), any(Pageable.class)))
        .thenReturn(
            new PageImpl(
                Arrays.asList(
                    getValidationMessage(ValidationMessageType.WARNING),
                    getValidationMessage(ValidationMessageType.ERROR))));
  }

  @State("a submission exists for the search criteria")
  public void aSubmissionExistsForSearchCriteria() {
    log.info("Setting up state: a submission exist for the search criteria");
    when(submissionRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl(Arrays.asList(getSubmission(), getSubmission())));
  }

  @State("no submissions exist for the search criteria")
  public void noSubmissionExistForTheSearchCriteria() {
    log.info("Setting up state: no submissions exist for the search criteria");
    when(submissionRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl(Collections.emptyList()));
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
