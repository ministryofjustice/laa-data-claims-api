package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionErrorCode.V100;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_USER_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.BULK_SUBMISSION_CREATED_BY_USER_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.BULK_SUBMISSION_ID;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionErrorCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionMatterStart;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionOutcome;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CategoryCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetails;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MediationType;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Integration tests for the Bulk Submission Controller. Tests the endpoints for creating,
 * retrieving and updating bulk submissions, including file upload validation, error handling, and
 * SQS message publishing. Uses TestContainers for integration testing with actual database and SQS
 * queue.
 */
@TestInstance(Lifecycle.PER_CLASS)
public class BulkSubmissionControllerIntegrationTest extends AbstractIntegrationTest {

  private static final String FILE = "file";
  private static final String TEXT_CSV = "text/csv";
  private static final String POST_BULK_SUBMISSION_ENDPOINT = API_URI_PREFIX + "/bulk-submissions";
  private static final String BULK_SUBMISSION_ENDPOINT = API_URI_PREFIX + "/bulk-submissions/{id}";
  private static final String OUTCOMES_CSV = "test_upload_files/csv/outcomes.csv";
  private static final String OUTCOMES_2_CSV = "test_upload_files/csv/outcomes-2.csv";
  private static final String OUTCOMES_3_CSV = "test_upload_files/csv/outcomes-3.csv";
  private static final String TEST_USER = "test-user";
  private static final String USER_ID_PARAM = "userId";
  private static final String OFFICES_PARAM = "offices";
  // has to match the office in the outcomes.csv file
  private static final String TEST_OFFICE = "0U099L";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Autowired private SqsClient sqsClient;

  @Value("${aws.sqs.queue-name}")
  private String queueName;

  private String queueUrl;

  @BeforeEach
  void beforeEach() {
    clearIntegrationData();
  }

  @BeforeAll
  void setup() {
    // create the queue if it doesn't exist
    sqsClient.createQueue(builder -> builder.queueName(queueName));

    // then get its URL
    GetQueueUrlResponse queueUrlResponse =
        sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build());
    this.queueUrl = queueUrlResponse.queueUrl();
  }

  @ParameterizedTest
  @CsvSource({
    "test_upload_files/csv/outcomes.csv,false,text/csv",
    "test_upload_files/txt/outcomes_with_matter_starts.txt,true,text/csv",
    "test_upload_files/xml/outcomes_with_matter_starts.xml,true,text/xml"
  })
  void shouldSaveSubmissionToDatabaseAndPublishMessage(
      String filePath, boolean hasMatterStarts, String contentType) throws Exception {
    // Given:
    // Below fields are set to "Y" in both files
    // CLIENT_LEGALLY_AIDED=Y,DUTY_SOLICITOR=Y,IRC_SURGERY=Y,YOUTH_COURT=Y,CLIENT2_LEGALLY_AIDED=Y,ELIGIBLE_CLIENT_INDICATOR=Y,
    // NATIONAL_REF_MECHANISM_ADVICE=Y,CLIENT2_POSTAL_APPL_ACCP=Y
    ClassPathResource resource = new ClassPathResource(filePath);

    MockMultipartFile file =
        new MockMultipartFile(FILE, resource.getFilename(), contentType, resource.getInputStream());

    // when: calling the POST endpoint with the file
    MvcResult result =
        mockMvc
            .perform(
                multipart(POST_BULK_SUBMISSION_ENDPOINT)
                    .file(file)
                    .param(USER_ID_PARAM, TEST_USER)
                    .param(OFFICES_PARAM, TEST_OFFICE)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isCreated())
            .andReturn();

    // then: response body contains IDs
    String responseBody = result.getResponse().getContentAsString();
    assertThat(responseBody).contains("bulk_submission_id");
    assertThat(responseBody).contains("submission_ids");

    // then: the database has a persisted entity with values as "true" for fields set to "Y"
    List<BulkSubmission> submissions = bulkSubmissionRepository.findAll();
    assertThat(submissions).hasSize(1);
    BulkSubmission savedBulkSubmission = submissions.getFirst();
    assertThat(savedBulkSubmission.getCreatedByUserId()).isEqualTo(TEST_USER);
    assertThat(savedBulkSubmission.getStatus()).isEqualTo(BulkSubmissionStatus.READY_FOR_PARSING);
    BulkSubmissionOutcome bulkSubmissionOutcome =
        savedBulkSubmission.getData().getOutcomes().getFirst();
    assertThat(bulkSubmissionOutcome.getClientLegallyAided()).isTrue();
    assertThat(bulkSubmissionOutcome.getClient2PostalApplAccp()).isTrue();
    assertThat(bulkSubmissionOutcome.getMatterType()).isEqualTo("IALB:IFRA");
    assertThat(bulkSubmissionOutcome.getDutySolicitor()).isTrue();
    assertThat(bulkSubmissionOutcome.getNationalRefMechanismAdvice()).isTrue();
    assertThat(bulkSubmissionOutcome.getIrcSurgery()).isTrue();
    assertThat(bulkSubmissionOutcome.getClient2LegallyAided()).isTrue();
    assertThat(bulkSubmissionOutcome.getEligibleClient()).isTrue();
    assertThat(bulkSubmissionOutcome.getYouthCourt()).isTrue();

    if (hasMatterStarts) {
      verifyBulkSubmissionMatterStarts(savedBulkSubmission);
    }

    // then: SQS has received a message
    verifyIfSqsMessageIsReceived(savedBulkSubmission);
  }

  @ParameterizedTest
  @CsvSource({
    "test_upload_files/csv/outcomes.csv",
    "test_upload_files/csv/outcomes_with_empty_bottom_rows.csv",
    "test_upload_files/csv/outcomes_with_empty_sparse_rows.csv"
  })
  void shouldSaveSubmissionToDatabaseAndPublishMessage(String filePath) throws Exception {
    // Given:
    // Below fields are set to "Y" in all files
    // CLIENT_LEGALLY_AIDED=Y,DUTY_SOLICITOR=Y,IRC_SURGERY=Y,YOUTH_COURT=Y,CLIENT2_LEGALLY_AIDED=Y,ELIGIBLE_CLIENT_INDICATOR=Y,
    // NATIONAL_REF_MECHANISM_ADVICE=Y,CLIENT2_POSTAL_APPL_ACCP=Y
    ClassPathResource resource = new ClassPathResource(filePath);

    MockMultipartFile file =
        new MockMultipartFile(FILE, resource.getFilename(), TEXT_CSV, resource.getInputStream());

    // when: calling the POST endpoint with the file
    MvcResult result =
        mockMvc
            .perform(
                multipart(POST_BULK_SUBMISSION_ENDPOINT)
                    .file(file)
                    .param(USER_ID_PARAM, TEST_USER)
                    .param(OFFICES_PARAM, TEST_OFFICE)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isCreated())
            .andReturn();

    // then: response body contains IDs
    String responseBody = result.getResponse().getContentAsString();
    assertThat(responseBody).contains("bulk_submission_id");
    assertThat(responseBody).contains("submission_ids");

    // then: the database has a persisted entity with values as "true" for fields set to "Y"
    List<BulkSubmission> submissions = bulkSubmissionRepository.findAll();
    assertThat(submissions).hasSize(1);
    BulkSubmission savedBulkSubmission = submissions.getFirst();
    assertThat(savedBulkSubmission.getCreatedByUserId()).isEqualTo(TEST_USER);
    assertThat(savedBulkSubmission.getStatus()).isEqualTo(BulkSubmissionStatus.READY_FOR_PARSING);
    List<BulkSubmissionOutcome> bulkSubmissionOutcomes =
        savedBulkSubmission.getData().getOutcomes();
    assertThat(bulkSubmissionOutcomes).hasSize(1);

    BulkSubmissionOutcome bulkSubmissionOutcome = bulkSubmissionOutcomes.getFirst();
    assertThat(bulkSubmissionOutcome.getClientLegallyAided()).isTrue();
    assertThat(bulkSubmissionOutcome.getClient2PostalApplAccp()).isTrue();
    assertThat(bulkSubmissionOutcome.getMatterType()).isEqualTo("IALB:IFRA");
    assertThat(bulkSubmissionOutcome.getDutySolicitor()).isTrue();
    assertThat(bulkSubmissionOutcome.getNationalRefMechanismAdvice()).isTrue();
    assertThat(bulkSubmissionOutcome.getIrcSurgery()).isTrue();
    assertThat(bulkSubmissionOutcome.getClient2LegallyAided()).isTrue();
    assertThat(bulkSubmissionOutcome.getEligibleClient()).isTrue();
    assertThat(bulkSubmissionOutcome.getYouthCourt()).isTrue();

    // then: SQS has received a message
    verifyIfSqsMessageIsReceived(savedBulkSubmission);
  }

  @Test
  void shouldSaveSubmissionToDatabaseWhenFileHasOutcomesWithHeadersOnlyAndPublishMessage()
      throws Exception {
    ClassPathResource resource =
        new ClassPathResource("test_upload_files/csv/outcomes_with_headers_only_rows.csv");

    MockMultipartFile file =
        new MockMultipartFile(FILE, resource.getFilename(), TEXT_CSV, resource.getInputStream());

    // when: calling the POST endpoint with the file
    MvcResult result =
        mockMvc
            .perform(
                multipart(POST_BULK_SUBMISSION_ENDPOINT)
                    .file(file)
                    .param(USER_ID_PARAM, TEST_USER)
                    .param(OFFICES_PARAM, TEST_OFFICE)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isCreated())
            .andReturn();

    // then: response body contains IDs
    String responseBody = result.getResponse().getContentAsString();
    assertThat(responseBody).contains("bulk_submission_id");
    assertThat(responseBody).contains("submission_ids");

    // then: the database has a persisted entity with values as "true" for fields set to "Y"
    List<BulkSubmission> submissions = bulkSubmissionRepository.findAll();
    assertThat(submissions).hasSize(1);
    BulkSubmission savedBulkSubmission = submissions.getFirst();
    assertThat(savedBulkSubmission.getCreatedByUserId()).isEqualTo(TEST_USER);
    assertThat(savedBulkSubmission.getStatus()).isEqualTo(BulkSubmissionStatus.READY_FOR_PARSING);
    List<BulkSubmissionOutcome> bulkSubmissionOutcomes =
        savedBulkSubmission.getData().getOutcomes();
    assertThat(bulkSubmissionOutcomes).hasSize(3);

    BulkSubmissionOutcome bulkSubmissionOutcome = bulkSubmissionOutcomes.getFirst();
    assertThat(bulkSubmissionOutcome.getClientLegallyAided()).isTrue();
    assertThat(bulkSubmissionOutcome.getClient2PostalApplAccp()).isTrue();
    assertThat(bulkSubmissionOutcome.getMatterType()).isEqualTo("IALB:IFRA");
    assertThat(bulkSubmissionOutcome.getDutySolicitor()).isTrue();
    assertThat(bulkSubmissionOutcome.getNationalRefMechanismAdvice()).isTrue();
    assertThat(bulkSubmissionOutcome.getIrcSurgery()).isTrue();
    assertThat(bulkSubmissionOutcome.getClient2LegallyAided()).isTrue();
    assertThat(bulkSubmissionOutcome.getEligibleClient()).isTrue();
    assertThat(bulkSubmissionOutcome.getYouthCourt()).isTrue();

    assertThat(bulkSubmissionOutcomes.get(1)).isEqualTo(bulkSubmissionOutcomes.get(2));

    // then: SQS has received a message
    verifyIfSqsMessageIsReceived(savedBulkSubmission);
  }

  @ParameterizedTest
  @CsvSource({
    "test_upload_files/xml/missing_outcomes_double.xml",
    "test_upload_files/xml/missing_outcomes_single.xml"
  })
  void shouldSaveSubmissionToDatabaseWhenFileHasOutcomesWithHeadersOnlyAndPublishMessage(
      String filePath) throws Exception {
    ClassPathResource resource = new ClassPathResource(filePath);

    MockMultipartFile file =
        new MockMultipartFile(FILE, resource.getFilename(), "text/xml", resource.getInputStream());

    // when: calling the POST endpoint with the file
    MvcResult result =
        mockMvc
            .perform(
                multipart(POST_BULK_SUBMISSION_ENDPOINT)
                    .file(file)
                    .param(USER_ID_PARAM, TEST_USER)
                    .param(OFFICES_PARAM, TEST_OFFICE)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isCreated())
            .andReturn();

    // then: response body contains IDs
    String responseBody = result.getResponse().getContentAsString();
    assertThat(responseBody).contains("bulk_submission_id");
    assertThat(responseBody).contains("submission_ids");

    // then: the database has a persisted entity with values as "true" for fields set to "Y"
    List<BulkSubmission> submissions = bulkSubmissionRepository.findAll();
    assertThat(submissions).hasSize(1);
    BulkSubmission savedBulkSubmission = submissions.getFirst();
    assertThat(savedBulkSubmission.getCreatedByUserId()).isEqualTo(TEST_USER);
    assertThat(savedBulkSubmission.getStatus()).isEqualTo(BulkSubmissionStatus.READY_FOR_PARSING);
    List<BulkSubmissionOutcome> bulkSubmissionOutcomes =
        savedBulkSubmission.getData().getOutcomes();
    assertThat(bulkSubmissionOutcomes).hasSize(1);

    BulkSubmissionOutcome bulkSubmissionOutcome = bulkSubmissionOutcomes.getFirst();
    assertThat(bulkSubmissionOutcome.getMatterType()).isNull();
    assertThat(bulkSubmissionOutcome.getFeeCode()).isNull();
    assertThat(bulkSubmissionOutcome.getCaseRefNumber()).isNull();
    assertThat(bulkSubmissionOutcome.getCaseStartDate()).isNull();
    assertThat(bulkSubmissionOutcome.getCaseId()).isNull();
    assertThat(bulkSubmissionOutcome.getCaseStageLevel()).isNull();
    assertThat(bulkSubmissionOutcome.getUfn()).isNull();
    assertThat(bulkSubmissionOutcome.getProcurementArea()).isNull();
    assertThat(bulkSubmissionOutcome.getAccessPoint()).isNull();
    assertThat(bulkSubmissionOutcome.getClientForename()).isNull();
    assertThat(bulkSubmissionOutcome.getClientSurname()).isNull();
    assertThat(bulkSubmissionOutcome.getClientDateOfBirth()).isNull();
    assertThat(bulkSubmissionOutcome.getUcn()).isNull();
    assertThat(bulkSubmissionOutcome.getClaRefNumber()).isNull();
    assertThat(bulkSubmissionOutcome.getClaExemption()).isNull();
    assertThat(bulkSubmissionOutcome.getGender()).isNull();
    assertThat(bulkSubmissionOutcome.getEthnicity()).isNull();
    assertThat(bulkSubmissionOutcome.getDisability()).isNull();
    assertThat(bulkSubmissionOutcome.getClientPostCode()).isNull();
    assertThat(bulkSubmissionOutcome.getWorkConcludedDate()).isNull();
    assertThat(bulkSubmissionOutcome.getAdviceTime()).isNull();
    assertThat(bulkSubmissionOutcome.getTravelTime()).isNull();
    assertThat(bulkSubmissionOutcome.getWaitingTime()).isNull();
    assertThat(bulkSubmissionOutcome.getProfitCost()).isNull();
    assertThat(bulkSubmissionOutcome.getValueOfCosts()).isNull();
    assertThat(bulkSubmissionOutcome.getDisbursementsAmount()).isNull();
    assertThat(bulkSubmissionOutcome.getCounselCost()).isNull();
    assertThat(bulkSubmissionOutcome.getDisbursementsVat()).isNull();
    assertThat(bulkSubmissionOutcome.getTravelWaitingCosts()).isNull();
    assertThat(bulkSubmissionOutcome.getVatIndicator()).isNull();
    assertThat(bulkSubmissionOutcome.getLondonNonlondonRate()).isNull();
    assertThat(bulkSubmissionOutcome.getClientType()).isNull();
    assertThat(bulkSubmissionOutcome.getToleranceIndicator()).isNull();
    assertThat(bulkSubmissionOutcome.getTravelCosts()).isNull();
    assertThat(bulkSubmissionOutcome.getOutcomeCode()).isNull();
    assertThat(bulkSubmissionOutcome.getLegacyCase()).isNull();
    assertThat(bulkSubmissionOutcome.getClaimType()).isNull();
    assertThat(bulkSubmissionOutcome.getAdjournedHearingFee()).isNull();
    assertThat(bulkSubmissionOutcome.getTypeOfAdvice()).isNull();
    assertThat(bulkSubmissionOutcome.getPostalApplAccp()).isNull();
    assertThat(bulkSubmissionOutcome.getScheduleRef()).isNull();
    assertThat(bulkSubmissionOutcome.getCmrhOral()).isNull();
    assertThat(bulkSubmissionOutcome.getCmrhTelephone()).isNull();
    assertThat(bulkSubmissionOutcome.getAitHearingCentre()).isNull();
    assertThat(bulkSubmissionOutcome.getSubstantiveHearing()).isNull();
    assertThat(bulkSubmissionOutcome.getHoInterview()).isNull();
    assertThat(bulkSubmissionOutcome.getHoUcn()).isNull();
    assertThat(bulkSubmissionOutcome.getTransferDate()).isNull();
    assertThat(bulkSubmissionOutcome.getDetentionTravelWaitingCosts()).isNull();
    assertThat(bulkSubmissionOutcome.getDeliveryLocation()).isNull();
    assertThat(bulkSubmissionOutcome.getPriorAuthorityRef()).isNull();
    assertThat(bulkSubmissionOutcome.getJrFormFilling()).isNull();
    assertThat(bulkSubmissionOutcome.getAdditionalTravelPayment()).isNull();
    assertThat(bulkSubmissionOutcome.getMeetingsAttended()).isNull();
    assertThat(bulkSubmissionOutcome.getMedicalReportsClaimed()).isNull();
    assertThat(bulkSubmissionOutcome.getDesiAccRep()).isNull();
    assertThat(bulkSubmissionOutcome.getMhtRefNumber()).isNull();
    assertThat(bulkSubmissionOutcome.getStageReached()).isNull();
    assertThat(bulkSubmissionOutcome.getFollowOnWork()).isNull();
    assertThat(bulkSubmissionOutcome.getNationalRefMechanismAdvice()).isNull();
    assertThat(bulkSubmissionOutcome.getExemptionCriteriaSatisfied()).isNull();
    assertThat(bulkSubmissionOutcome.getExclCaseFundingRef()).isNull();
    assertThat(bulkSubmissionOutcome.getNoOfClients()).isNull();
    assertThat(bulkSubmissionOutcome.getNoOfSurgeryClients()).isNull();
    assertThat(bulkSubmissionOutcome.getIrcSurgery()).isNull();
    assertThat(bulkSubmissionOutcome.getSurgeryDate()).isNull();
    assertThat(bulkSubmissionOutcome.getLineNumber()).isNull();
    assertThat(bulkSubmissionOutcome.getCrimeMatterType()).isNull();
    assertThat(bulkSubmissionOutcome.getFeeScheme()).isNull();
    assertThat(bulkSubmissionOutcome.getRepOrderDate()).isNull();
    assertThat(bulkSubmissionOutcome.getNoOfSuspects()).isNull();
    assertThat(bulkSubmissionOutcome.getNoOfPoliceStation()).isNull();
    assertThat(bulkSubmissionOutcome.getPoliceStation()).isNull();
    assertThat(bulkSubmissionOutcome.getDsccNumber()).isNull();
    assertThat(bulkSubmissionOutcome.getMaatId()).isNull();
    assertThat(bulkSubmissionOutcome.getPrisonLawPriorApproval()).isNull();
    assertThat(bulkSubmissionOutcome.getDutySolicitor()).isNull();
    assertThat(bulkSubmissionOutcome.getYouthCourt()).isNull();
    assertThat(bulkSubmissionOutcome.getSchemeId()).isNull();
    assertThat(bulkSubmissionOutcome.getNumberOfMediationSessions()).isNull();
    assertThat(bulkSubmissionOutcome.getMediationTime()).isNull();
    assertThat(bulkSubmissionOutcome.getOutreach()).isNull();
    assertThat(bulkSubmissionOutcome.getReferral()).isNull();
    assertThat(bulkSubmissionOutcome.getClientLegallyAided()).isNull();
    assertThat(bulkSubmissionOutcome.getClient2Forename()).isNull();
    assertThat(bulkSubmissionOutcome.getClient2Surname()).isNull();
    assertThat(bulkSubmissionOutcome.getClient2DateOfBirth()).isNull();
    assertThat(bulkSubmissionOutcome.getClient2Ucn()).isNull();
    assertThat(bulkSubmissionOutcome.getClient2PostCode()).isNull();
    assertThat(bulkSubmissionOutcome.getClient2Gender()).isNull();
    assertThat(bulkSubmissionOutcome.getClient2Ethnicity()).isNull();
    assertThat(bulkSubmissionOutcome.getClient2Disability()).isNull();
    assertThat(bulkSubmissionOutcome.getClient2LegallyAided()).isNull();
    assertThat(bulkSubmissionOutcome.getUniqueCaseId()).isNull();
    assertThat(bulkSubmissionOutcome.getStandardFeeCat()).isNull();
    assertThat(bulkSubmissionOutcome.getClient2PostalApplAccp()).isNull();
    assertThat(bulkSubmissionOutcome.getCostsDamagesRecovered()).isNull();
    assertThat(bulkSubmissionOutcome.getEligibleClient()).isNull();
    assertThat(bulkSubmissionOutcome.getCourtLocationHpcds()).isNull();
    assertThat(bulkSubmissionOutcome.getLocalAuthorityNumber()).isNull();
    assertThat(bulkSubmissionOutcome.getPaNumber()).isNull();
    assertThat(bulkSubmissionOutcome.getExcessTravelCosts()).isNull();
    assertThat(bulkSubmissionOutcome.getMedConcludedDate()).isNull();

    // then: SQS has received a message
    verifyIfSqsMessageIsReceived(savedBulkSubmission);
  }

  private static void verifyBulkSubmissionMatterStarts(BulkSubmission savedBulkSubmission) {
    Stream.of(
            new Object[] {
              0, "2A300G/2010/01", "PA00100", "LONDON", "AP00000", CategoryCode.PI, null, 15
            },
            new Object[] {
              1, "2A300G/2010/01", "PA00100", "LONDON", "AP00000", CategoryCode.PUB, null, 16
            },
            new Object[] {
              2, "2A300G/2010/01", "PA00100", "LONDON", "AP00000", CategoryCode.WB, null, 17
            },
            new Object[] {
              3, "2A300G/2010/01", "PA00100", "LONDON", "AP00000", CategoryCode.DISC, null, 18
            },
            new Object[] {4, null, null, null, null, null, MediationType.MDCS_CHILD_ONLY_SOLE, 1},
            new Object[] {5, null, null, null, null, null, MediationType.MDCC_CHILD_ONLY_CO, 2},
            new Object[] {
              6, null, null, null, null, null, MediationType.MDPS_PROPERTY_FINANCE_SOLE, 3
            },
            new Object[] {
              7, null, null, null, null, null, MediationType.MDPC_PROPERTY_FINANCE_CO, 4
            },
            new Object[] {8, null, null, null, null, null, MediationType.MDAS_ALL_ISSUES_SOLE, 5},
            new Object[] {9, null, null, null, null, null, MediationType.MDAC_ALL_ISSUES_CO, 6})
        .forEach(
            params ->
                verifyBulkSubmissionMatterStart(
                    (int) params[0],
                    savedBulkSubmission,
                    (String) params[1],
                    (String) params[2],
                    (String) params[3],
                    (String) params[4],
                    (CategoryCode) params[5],
                    (MediationType) params[6],
                    (int) params[7]));
  }

  private static void verifyBulkSubmissionMatterStart(
      int index,
      BulkSubmission savedBulkSubmission,
      String scheduleRef,
      String procurementArea,
      String deliveryLocation,
      String accessPoint,
      CategoryCode categoryCode,
      MediationType mediationType,
      int numberOfMatterStarts) {
    BulkSubmissionMatterStart bulkSubmissionMatterStart =
        savedBulkSubmission.getData().getMatterStarts().get(index);
    assertThat(bulkSubmissionMatterStart.getScheduleRef()).isEqualTo(scheduleRef);
    assertThat(bulkSubmissionMatterStart.getProcurementArea()).isEqualTo(procurementArea);
    assertThat(bulkSubmissionMatterStart.getDeliveryLocation()).isEqualTo(deliveryLocation);
    assertThat(bulkSubmissionMatterStart.getAccessPoint()).isEqualTo(accessPoint);
    assertThat(bulkSubmissionMatterStart.getMediationType()).isEqualTo(mediationType);
    assertThat(bulkSubmissionMatterStart.getNumberOfMatterStarts()).isEqualTo(numberOfMatterStarts);
    assertThat(bulkSubmissionMatterStart.getCategoryCode()).isEqualTo(categoryCode);
  }

  @Test
  void shouldParseTheBooleanFieldsCorrectly() throws Exception {
    // Given:
    // Below fields are set to "N" in outcomes-2.csv
    // CLIENT_LEGALLY_AIDED=N,DUTY_SOLICITOR=N,IRC_SURGERY=N,YOUTH_COURT=N,CLIENT2_LEGALLY_AIDED=N,ELIGIBLE_CLIENT_INDICATOR=N,
    // NATIONAL_REF_MECHANISM_ADVICE=N,CLIENT2_POSTAL_APPL_ACCP=N
    ClassPathResource resource = new ClassPathResource(OUTCOMES_2_CSV);

    MockMultipartFile file =
        new MockMultipartFile(FILE, resource.getFilename(), TEXT_CSV, resource.getInputStream());

    // when: calling the POST endpoint with the file
    MvcResult result =
        mockMvc
            .perform(
                multipart(POST_BULK_SUBMISSION_ENDPOINT)
                    .file(file)
                    .param(USER_ID_PARAM, TEST_USER)
                    .param(OFFICES_PARAM, TEST_OFFICE)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isCreated())
            .andReturn();

    // then: response body contains IDs
    String responseBody = result.getResponse().getContentAsString();
    assertThat(responseBody).contains("bulk_submission_id");
    assertThat(responseBody).contains("submission_ids");

    // then: the database has a persisted entity with values as "false" for fields set to "N"
    List<BulkSubmission> submissions = bulkSubmissionRepository.findAll();
    assertThat(submissions).hasSize(1);
    BulkSubmission savedBulkSubmission = submissions.getFirst();
    assertThat(savedBulkSubmission.getCreatedByUserId()).isEqualTo(TEST_USER);
    assertThat(savedBulkSubmission.getStatus()).isEqualTo(BulkSubmissionStatus.READY_FOR_PARSING);
    BulkSubmissionOutcome bulkSubmissionOutcome =
        savedBulkSubmission.getData().getOutcomes().getFirst();
    assertThat(bulkSubmissionOutcome.getClientLegallyAided()).isFalse();
    assertThat(bulkSubmissionOutcome.getClient2PostalApplAccp()).isFalse();
    assertThat(bulkSubmissionOutcome.getDutySolicitor()).isFalse();
    assertThat(bulkSubmissionOutcome.getNationalRefMechanismAdvice()).isFalse();
    assertThat(bulkSubmissionOutcome.getIrcSurgery()).isFalse();
    assertThat(bulkSubmissionOutcome.getClient2LegallyAided()).isFalse();
    assertThat(bulkSubmissionOutcome.getEligibleClient()).isFalse();
    assertThat(bulkSubmissionOutcome.getYouthCourt()).isFalse();
    assertThat(bulkSubmissionOutcome.getMatterType()).isEqualTo("IALB:IFRA");

    verifyIfSqsMessageIsReceived(savedBulkSubmission);
  }

  @Test
  void shouldHandleNullValuesForBooleanFields() throws Exception {
    // Given:
    // Below fields are missing in outcomes-3.csv
    // CLIENT_LEGALLY_AIDED,DUTY_SOLICITOR,IRC_SURGERY,YOUTH_COURT,CLIENT2_LEGALLY_AIDED,ELIGIBLE_CLIENT_INDICATOR,
    // NATIONAL_REF_MECHANISM_ADVICE,CLIENT2_POSTAL_APPL_ACCP
    ClassPathResource resource = new ClassPathResource(OUTCOMES_3_CSV);

    MockMultipartFile file =
        new MockMultipartFile(FILE, resource.getFilename(), TEXT_CSV, resource.getInputStream());

    // when: calling the POST endpoint with the file
    MvcResult result =
        mockMvc
            .perform(
                multipart(POST_BULK_SUBMISSION_ENDPOINT)
                    .file(file)
                    .param(USER_ID_PARAM, TEST_USER)
                    .param(OFFICES_PARAM, TEST_OFFICE)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isCreated())
            .andReturn();

    // then: response body contains IDs
    String responseBody = result.getResponse().getContentAsString();
    assertThat(responseBody).contains("bulk_submission_id");
    assertThat(responseBody).contains("submission_ids");

    // then: the database has a persisted entity with null values for missing fields
    List<BulkSubmission> submissions = bulkSubmissionRepository.findAll();
    assertThat(submissions).hasSize(1);
    BulkSubmission savedBulkSubmission = submissions.getFirst();
    assertThat(savedBulkSubmission.getCreatedByUserId()).isEqualTo(TEST_USER);
    assertThat(savedBulkSubmission.getStatus()).isEqualTo(BulkSubmissionStatus.READY_FOR_PARSING);
    BulkSubmissionOutcome bulkSubmissionOutcome =
        savedBulkSubmission.getData().getOutcomes().getFirst();
    assertThat(bulkSubmissionOutcome.getClientLegallyAided()).isNull();
    assertThat(bulkSubmissionOutcome.getClient2PostalApplAccp()).isNull();
    assertThat(bulkSubmissionOutcome.getDutySolicitor()).isNull();
    assertThat(bulkSubmissionOutcome.getNationalRefMechanismAdvice()).isNull();
    assertThat(bulkSubmissionOutcome.getIrcSurgery()).isNull();
    assertThat(bulkSubmissionOutcome.getClient2LegallyAided()).isNull();
    assertThat(bulkSubmissionOutcome.getEligibleClient()).isNull();
    assertThat(bulkSubmissionOutcome.getYouthCourt()).isNull();
    assertThat(bulkSubmissionOutcome.getMatterType()).isEqualTo("IALB:IFRA");

    verifyIfSqsMessageIsReceived(savedBulkSubmission);
  }

  private void verifyIfSqsMessageIsReceived(BulkSubmission saved) {
    // then: SQS has received a message
    ReceiveMessageResponse receiveResp =
        sqsClient.receiveMessage(
            ReceiveMessageRequest.builder()
                .queueUrl(this.queueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(2)
                .build());
    assertThat(receiveResp.messages()).hasSize(1);
    assertThat(receiveResp.messages().getFirst().body()).contains(saved.getId().toString());
    // Delete the message from the queue.
    sqsClient.deleteMessage(
        DeleteMessageRequest.builder()
            .queueUrl(this.queueUrl)
            .receiptHandle(receiveResp.messages().getFirst().receiptHandle())
            .build());
  }

  @Test
  void shouldReturnUnauthorizedForCreateSubmissionWhenAuthHeaderIsInvalid() throws Exception {
    // given: a fake file
    ClassPathResource resource = new ClassPathResource(OUTCOMES_CSV);

    MockMultipartFile file =
        new MockMultipartFile(FILE, resource.getFilename(), TEXT_CSV, resource.getInputStream());

    // when: calling the POST endpoint with an invalid auth token, then: it should return an
    // unauthorized status.
    mockMvc
        .perform(
            multipart(POST_BULK_SUBMISSION_ENDPOINT)
                .file(file)
                .param(USER_ID_PARAM, TEST_USER)
                .param(OFFICES_PARAM, TEST_OFFICE)
                .header(AUTHORIZATION_HEADER, INVALID_AUTH_TOKEN))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldReturnUnsupportedMediaTypeForCreateSubmissionWhenTheFileExtensionIsNotSupported()
      throws Exception {
    // given: an unsupported media type
    ClassPathResource resource = new ClassPathResource("test_upload_files/invalid/unsupported.doc");

    MockMultipartFile file =
        new MockMultipartFile(FILE, resource.getFilename(), TEXT_CSV, resource.getInputStream());

    // when: calling the POST endpoint, then: it should return an unsupported media type status.
    mockMvc
        .perform(
            multipart(POST_BULK_SUBMISSION_ENDPOINT)
                .file(file)
                .param(USER_ID_PARAM, TEST_USER)
                .param(OFFICES_PARAM, TEST_OFFICE)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void shouldReturnUnsupportedMediaTypeForCreateSubmissionWhenTheContentTypeIsNotSupported()
      throws Exception {
    // given: a file with an unsupported content type
    ClassPathResource resource = new ClassPathResource(OUTCOMES_CSV);

    MockMultipartFile file =
        new MockMultipartFile(
            FILE, resource.getFilename(), "application/json", resource.getInputStream());

    // when: calling the POST endpoint, then: it should return an unsupported media type status.
    mockMvc
        .perform(
            multipart(POST_BULK_SUBMISSION_ENDPOINT)
                .file(file)
                .param(USER_ID_PARAM, TEST_USER)
                .param(OFFICES_PARAM, TEST_OFFICE)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void shouldReturnBadRequestForCreateSubmissionWhenTheFileIsEmpty() throws Exception {
    // given: an empty file
    MockMultipartFile file = new MockMultipartFile(FILE, "empty-file.csv", TEXT_CSV, new byte[0]);

    // when: calling the POST endpoint, then: it should return a bad request.
    mockMvc
        .perform(
            multipart(POST_BULK_SUBMISSION_ENDPOINT)
                .file(file)
                .param(USER_ID_PARAM, TEST_USER)
                .param(OFFICES_PARAM, TEST_OFFICE)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturnErrorForCreateSubmissionWhenTheCsvHasAnUnexpectedColumn() throws Exception {
    // given: a file with an incorrect column name
    ClassPathResource resource =
        new ClassPathResource("test_upload_files/invalid/outcomes-incorrect-column-name.csv");

    MockMultipartFile file =
        new MockMultipartFile(FILE, resource.getFilename(), TEXT_CSV, resource.getInputStream());

    // when: calling the POST endpoint, then: it should return a bad request.
    MvcResult result =
        mockMvc
            .perform(
                multipart(POST_BULK_SUBMISSION_ENDPOINT)
                    .file(file)
                    .param(USER_ID_PARAM, TEST_USER)
                    .param(OFFICES_PARAM, TEST_OFFICE)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isBadRequest())
            .andReturn();

    var json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
    assertThat(json.get("errorMessage").asText())
        .isEqualTo("Failed to parse csv bulk submission file");
    assertThat(json.get("httpStatus").asInt()).isEqualTo(400);
  }

  @Test
  void shouldReturnErrorForCreateSubmissionWhenTheCsvIsMissingOfficeHeader() throws Exception {
    // given: a file with a missing Office header
    ClassPathResource resource =
        new ClassPathResource("test_upload_files/invalid/outcomes-missing-office.csv");

    MockMultipartFile file =
        new MockMultipartFile(FILE, resource.getFilename(), TEXT_CSV, resource.getInputStream());

    // when: calling the POST endpoint, then: it should return a bad request.
    MvcResult result =
        mockMvc
            .perform(
                multipart(POST_BULK_SUBMISSION_ENDPOINT)
                    .file(file)
                    .param(USER_ID_PARAM, TEST_USER)
                    .param(OFFICES_PARAM, TEST_OFFICE)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isBadRequest())
            .andReturn();

    var json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
    assertThat(json.get("errorMessage").asText())
        .isEqualTo("Office missing from csv bulk submission file");
    assertThat(json.get("httpStatus").asInt()).isEqualTo(400);
  }

  @Test
  void shouldReturnErrorForCreateSubmissionWhenTheCsvIsMalformedWithInconsistentNoOfColumns()
      throws Exception {
    // given: a file with an inconsistent no of columns
    ClassPathResource resource = new ClassPathResource("test_upload_files/invalid/malformed.csv");

    MockMultipartFile file =
        new MockMultipartFile(FILE, resource.getFilename(), TEXT_CSV, resource.getInputStream());

    // when: calling the POST endpoint, then: it should return a bad request.
    MvcResult result =
        mockMvc
            .perform(
                multipart(POST_BULK_SUBMISSION_ENDPOINT)
                    .file(file)
                    .param(USER_ID_PARAM, TEST_USER)
                    .param(OFFICES_PARAM, TEST_OFFICE)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isBadRequest())
            .andReturn();

    var json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
    assertThat(json.get("errorMessage").asText())
        .isEqualTo("Failed to parse bulk submission file header: OFFICE;account=");
    assertThat(json.get("httpStatus").asInt()).isEqualTo(400);
  }

  @Test
  void shouldGetBulkSubmissionById() throws Exception {
    // given: a bulk submission is saved to the database
    var bulkSubmission200ResponseDetails =
        new GetBulkSubmission200ResponseDetails()
            .addMatterStartsItem(ClaimsDataTestUtil.getBulkSubmissionMatterStart())
            .addOutcomesItem(ClaimsDataTestUtil.getBulkSubmissionOutcome(Boolean.TRUE))
            .office(ClaimsDataTestUtil.getBulkSubmissionOffice())
            .schedule(ClaimsDataTestUtil.getBulkSubmissionSchedule());
    var bulkSubmission =
        BulkSubmission.builder()
            .id(Uuid7.timeBasedUuid())
            .data(bulkSubmission200ResponseDetails)
            .status(BulkSubmissionStatus.READY_FOR_PARSING)
            .createdByUserId(BULK_SUBMISSION_CREATED_BY_USER_ID)
            .createdOn(Instant.now())
            .build();
    BulkSubmission savedBulkSubmission = bulkSubmissionRepository.save(bulkSubmission);

    // when: calling the GET endpoint with the ID
    MvcResult result =
        mockMvc
            .perform(
                get(BULK_SUBMISSION_ENDPOINT, savedBulkSubmission.getId().toString())
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    // then: response body contains bulk_submission_id, status and details
    String responseBody = result.getResponse().getContentAsString();

    var getBulkSubmission200Response =
        OBJECT_MAPPER.readValue(responseBody, GetBulkSubmission200Response.class);
    assertThat(getBulkSubmission200Response.getBulkSubmissionId())
        .isEqualTo(savedBulkSubmission.getId());
    assertThat(getBulkSubmission200Response.getStatus())
        .isEqualTo(BulkSubmissionStatus.READY_FOR_PARSING);
    assertThat(getBulkSubmission200Response.getDetails())
        .isEqualTo(bulkSubmission200ResponseDetails);
    assertThat(getBulkSubmission200Response.getCreatedByUserId())
        .isEqualTo(BULK_SUBMISSION_CREATED_BY_USER_ID);

    // clean up the test-data
    bulkSubmissionRepository.delete(bulkSubmission);
  }

  @Test
  void shouldReturnUnauthorizedForGetBulkSubmissionWhenAuthHeaderIsInvalid() throws Exception {
    // when: calling the GET endpoint with an invalid auth token, it should return unauthorized
    // status.
    mockMvc
        .perform(
            get(BULK_SUBMISSION_ENDPOINT, Uuid7.timeBasedUuid())
                .header(AUTHORIZATION_HEADER, INVALID_AUTH_TOKEN))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldReturnNotFoundForGetBulkSubmissionWhenItDoesNotExist() throws Exception {
    // when: calling the GET endpoint with a random ID, it should return not found.
    MvcResult result =
        mockMvc
            .perform(
                get(BULK_SUBMISSION_ENDPOINT, BULK_SUBMISSION_ID)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isNotFound())
            .andReturn();

    var json = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
    assertThat(json.get("errorMessage").asText())
        .isEqualTo(String.format("No entity found with id: %s", BULK_SUBMISSION_ID));
    assertThat(json.get("httpStatus").asInt()).isEqualTo(404);
  }

  @Test
  void shouldStoreUnauthorisedSubmissionWhenOfficeCodeDoesNotMatch() throws Exception {
    // given: a CSV with office code that doesn't match the provided param
    ClassPathResource resource = new ClassPathResource(OUTCOMES_CSV);
    MockMultipartFile file =
        new MockMultipartFile(FILE, resource.getFilename(), TEXT_CSV, resource.getInputStream());

    // when: submitting the file with mismatched authorised office
    mockMvc
        .perform(
            multipart(POST_BULK_SUBMISSION_ENDPOINT)
                .file(file)
                .param(USER_ID_PARAM, TEST_USER)
                .param(OFFICES_PARAM, "n/a")
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isForbidden());

    // then: data is still persisted in DB with UNAUTHORISED status
    List<BulkSubmission> submissions = bulkSubmissionRepository.findAll();
    assertThat(submissions).hasSize(1);

    BulkSubmission saved = submissions.getFirst();
    assertThat(saved.getStatus()).isEqualTo(BulkSubmissionStatus.UNAUTHORISED);
    assertThat(saved.getErrorCode()).isEqualTo(BulkSubmissionErrorCode.E100);
    assertThat(saved.getErrorDescription()).contains("User does not have authorisation");
    assertThat(saved.getCreatedByUserId()).isEqualTo(TEST_USER);

    // clean up the test-data
    bulkSubmissionRepository.deleteAll();
  }

  @Test
  void shouldReturnUnauthorizedForPatchBulkSubmissionWhenAuthHeaderIsInvalid() throws Exception {
    // when: calling the PATCH endpoint with an invalid auth token, it should return unauthorized
    // status.
    mockMvc
        .perform(
            patch(BULK_SUBMISSION_ENDPOINT, Uuid7.timeBasedUuid())
                .header(AUTHORIZATION_HEADER, INVALID_AUTH_TOKEN))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldUpdateBulkSubmissionToDatabase() throws Exception {
    createBulkSubmission();

    // given: a Bulk Submission patch payload with the changes to make
    BulkSubmissionPatch patch =
        new BulkSubmissionPatch()
            .status(BulkSubmissionStatus.VALIDATION_FAILED)
            .errorCode(BulkSubmissionErrorCode.V100)
            .errorDescription("This is the error message")
            .updatedByUserId(API_USER_ID);

    // when: calling the patch endpoint
    mockMvc
        .perform(
            patch(BULK_SUBMISSION_ENDPOINT, BULK_SUBMISSION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patch)))
        .andExpect(status().isNoContent())
        .andReturn();

    MvcResult result =
        mockMvc
            .perform(
                get(BULK_SUBMISSION_ENDPOINT, BULK_SUBMISSION_ID)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    // then: response body contains bulk_submission_id, status and details
    String responseBody = result.getResponse().getContentAsString();

    var getBulkSubmission200Response =
        OBJECT_MAPPER.readValue(responseBody, GetBulkSubmission200Response.class);

    assertThat(getBulkSubmission200Response.getBulkSubmissionId()).isEqualTo(BULK_SUBMISSION_ID);
    assertThat(getBulkSubmission200Response.getStatus())
        .isEqualTo(BulkSubmissionStatus.VALIDATION_FAILED);
    assertThat(getBulkSubmission200Response.getErrorCode()).isEqualTo(V100);
    assertThat(getBulkSubmission200Response.getUpdatedByUserId()).isEqualTo(API_USER_ID);
    assertThat(getBulkSubmission200Response.getErrorDescription())
        .isEqualTo("This is the error message");
    // clean up the test-data
    bulkSubmissionRepository.deleteAll();
  }

  @Test
  void shouldReturnNotFoundWhenUpdatingNonExistingBulkSubmission() throws Exception {
    // given: a Bulk Submission patch payload with the changes to make
    BulkSubmissionPatch patch =
        new BulkSubmissionPatch().status(BulkSubmissionStatus.VALIDATION_FAILED);

    // when: calling the patch endpoint on a random bulk submission
    mockMvc
        .perform(
            patch(BULK_SUBMISSION_ENDPOINT, UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patch)))
        .andExpect(status().isNotFound())
        .andReturn();
  }

  @Test
  void shouldOnlyUpdateFieldsIncludedInPatch() throws Exception {
    // Given: create initial bulk submission
    createBulkSubmissionWithErrorFields();

    GetBulkSubmission200Response beforeUpdate =
        OBJECT_MAPPER.readValue(
            mockMvc
                .perform(
                    get(BULK_SUBMISSION_ENDPOINT, BULK_SUBMISSION_ID)
                        .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            GetBulkSubmission200Response.class);

    // When: patch with only status update and updatedByUserId
    BulkSubmissionPatch patch =
        new BulkSubmissionPatch()
            .status(BulkSubmissionStatus.VALIDATION_FAILED)
            .updatedByUserId("updated-by-user");

    mockMvc
        .perform(
            patch(BULK_SUBMISSION_ENDPOINT, BULK_SUBMISSION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patch)))
        .andExpect(status().isNoContent());

    // Then: verify only status and updatedByUserId were updated
    GetBulkSubmission200Response afterUpdate =
        OBJECT_MAPPER.readValue(
            mockMvc
                .perform(
                    get(BULK_SUBMISSION_ENDPOINT, BULK_SUBMISSION_ID)
                        .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            GetBulkSubmission200Response.class);

    assertThat(afterUpdate.getStatus()).isEqualTo(BulkSubmissionStatus.VALIDATION_FAILED);
    assertThat(afterUpdate.getUpdatedByUserId()).isEqualTo("updated-by-user");

    // Rest of the values should be the same as before
    assertThat(afterUpdate.getErrorCode()).isEqualTo(beforeUpdate.getErrorCode());
    assertThat(afterUpdate.getErrorDescription()).isEqualTo(beforeUpdate.getErrorDescription());
    assertThat(afterUpdate.getDetails()).isEqualTo(beforeUpdate.getDetails());
    assertThat(afterUpdate.getCreatedByUserId()).isEqualTo(beforeUpdate.getCreatedByUserId());

    // clean up the test-data
    bulkSubmissionRepository.deleteAll();
  }

  private void createBulkSubmissionWithErrorFields() {
    var bulkSubmission =
        BulkSubmission.builder()
            .id(BULK_SUBMISSION_ID)
            .data(new GetBulkSubmission200ResponseDetails())
            .status(BulkSubmissionStatus.READY_FOR_PARSING)
            .createdByUserId(BULK_SUBMISSION_CREATED_BY_USER_ID)
            .errorCode(BulkSubmissionErrorCode.E100)
            .errorDescription("Initial error description")
            .updatedByUserId("initial-updater")
            .createdOn(CREATED_ON)
            .updatedOn(CREATED_ON)
            .build();
    bulkSubmissionRepository.save(bulkSubmission);
  }
}
