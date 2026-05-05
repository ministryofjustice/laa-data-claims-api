package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.SubmissionService.DECIMAL_PLACES;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_USER_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AREA_OF_LAW;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.BULK_SUBMISSION_CREATED_BY_USER_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.BULK_SUBMISSION_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.OFFICE_ACCOUNT_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.USER_ID;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionBase;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionsResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.BulkSubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.MatterStartRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationMessageLogRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.IntegrationTestUtils;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@TestInstance(Lifecycle.PER_CLASS)
public class SubmissionControllerIntegrationTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private SubmissionRepository submissionRepository;

  @Autowired private BulkSubmissionRepository bulkSubmissionRepository;

  @Autowired private ValidationMessageLogRepository validationMessageLogRepository;

  @Autowired private ClaimRepository claimRepository;

  @Autowired private MatterStartRepository matterStartRepository;

  @Autowired private SqsClient sqsClient;

  @Value("${aws.sqs.queue-name}")
  private String queueName;

  private String queueUrl;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String AUTHORIZATION_HEADER = "Authorization";

  // must match application-test.yml for test-runner token
  private static final String AUTHORIZATION_TOKEN = "f67f968e-b479-4e61-b66e-f57984931e56";

  private static final String TEST_ERROR_MESSAGE =
      "Concatenated test error messages from integration test";

  private static final String SUBMISSIONS_ENDPOINT = API_URI_PREFIX + "/submissions";
  private static final String SUBMISSION_BY_ID_ENDPOINT = API_URI_PREFIX + "/submissions/{id}";
  private static final String SUSPICIOUS_SQL_PATTERN_LOG_MSG = "Suspicious SQL-like pattern";
  private static final String PERIOD_JAN_25 = "JAN-25";
  private static final String PERIOD_JAN_2025 = "JAN-2025";
  private static final String PERIOD_DEC_2024 = "DEC-2024";
  private static final String PERIOD_APR_2025 = "APR-2025";
  private static final String OFFICE_AAAA01 = "AAAA01";
  private static final String OFFICE_AAAA02 = "aaaa02";
  private static final String PARAM_OFFICES = "offices";
  private static final String PARAM_SORT = "sort";

  @BeforeAll
  void initialSetup() {
    OBJECT_MAPPER.registerModule(new JavaTimeModule());

    // create the queue if it doesn't exist
    sqsClient.createQueue(builder -> builder.queueName(queueName));

    // then get its URL
    GetQueueUrlResponse queueUrlResponse =
        sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build());
    this.queueUrl = queueUrlResponse.queueUrl();
  }

  @BeforeEach
  void setup() {
    seedSubmissionsData();
  }

  @Test
  void postSubmission_shouldCreate() throws Exception {
    final UUID submissionId = Uuid7.timeBasedUuid();
    // given: a SubmissionPost payload
    submissionRepository.deleteAll();
    SubmissionPost submissionPost =
        SubmissionPost.builder()
            .submissionId(submissionId)
            .bulkSubmissionId(BULK_SUBMISSION_ID)
            .officeAccountNumber(OFFICE_ACCOUNT_NUMBER)
            .submissionPeriod(PERIOD_JAN_25)
            .areaOfLaw(AREA_OF_LAW)
            .status(SubmissionStatus.CREATED)
            .providerUserId(BULK_SUBMISSION_CREATED_BY_USER_ID)
            .createdByUserId(API_USER_ID)
            .submitted(CREATED_ON.atOffset(ZoneOffset.UTC))
            .build();
    // Get the logger used by the class under test
    ListAppender<ILoggingEvent> listAppender = getILoggingEventListAppender();

    // when: calling POST endpoint for submissions
    mockMvc
        .perform(
            post(SUBMISSIONS_ENDPOINT)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(submissionPost)))
        .andExpect(status().isCreated());

    Submission createdSubmission = submissionRepository.findById(submissionId).orElseThrow();

    // then: submission is correctly created
    assertThat(createdSubmission.getOfficeAccountNumber()).isEqualTo(OFFICE_ACCOUNT_NUMBER);
    assertThat(createdSubmission.getAreaOfLaw()).isEqualTo(AREA_OF_LAW);

    assertThat(createdSubmission.getProviderUserId()).isEqualTo(BULK_SUBMISSION_CREATED_BY_USER_ID);
    assertThat(createdSubmission.getCreatedByUserId()).isEqualTo(API_USER_ID);
    assertThat(
            listAppender.list.stream()
                .filter(
                    event -> event.getFormattedMessage().contains(SUSPICIOUS_SQL_PATTERN_LOG_MSG))
                .count())
        .isEqualTo(0);
  }

  @ParameterizedTest
  @DisplayName("Should log warning with SQL-like patterns in string fields")
  @ValueSource(
      strings = {
        // ---- Basic conditions that are always true ----
        "' OR '1'='1' --",
        "' AND '1'='1' --",

        // ---- Classic DROP attacks ----
        "Robert'); DROP TABLE Students;--",
        "'; DROP TABLE users; --",

        // ---- UNION attacks ----
        "' UNION SELECT username, password FROM users--",

        // ---- Comment-based termination ----
        "test' --",
        "abc'/*",

        // ---- Time-based attacks ----
        "'; WAITFOR DELAY '0:0:5'--", // MSSQL
        "' OR SLEEP(5)--", // MySQL
        "1; SELECT pg_sleep(5); --", // PostgreSQL

        // ---- Stacked queries ----
        "'; INSERT INTO audit_log(message) VALUES('hacked'); --",
        "'; UPDATE users SET role='admin' WHERE username='user'; --",
        "'; DELETE FROM users WHERE '1'='1'; --",

        // ---- Stored procedure & command execution ----
        "'; EXEC xp_cmdshell('dir'); --", // SQL Server
        "'; CALL system('ls'); --", // MySQL/Unix external command
        "'; EXECUTE IMMEDIATE 'DROP TABLE accounts'; --", // Oracle

        // ---- Schema enumeration ----
        "' UNION SELECT table_name, column_name FROM information_schema.columns--",
        "'; SELECT * FROM information_schema.tables; --",

        // ---- Obfuscation variants ----
        "' OR 1=1 /*comment*/--",
        "'/**/OR/**/1=1--",
        "%27%20OR%201=1--", // URL encoded

        // ---- Nested/injected string termination ----
        "O'Brien'); DROP TABLE contacts; --",
        "abc123'); DELETE FROM logs WHERE 1=1; --",

        // ---- NoSQL / hybrid-like payloads ----
        "\"}; DROP TABLE logs; --",

        // ---- Vendor-specific distinct patterns ----
        "' OR sleep(5)#", // MySQL # comment
        "'); COPY (SELECT '') TO PROGRAM 'ls'; --", // PostgreSQL
        "'; SHUTDOWN; --", // SQL Server

        // ---- LIKE/wildcard injection ----
        "' OR name LIKE '%'",

        // ---- JSON-style injection ----
        "']}'; DROP TABLE products; --"
      })
  void shouldLogAWarningWhenSqlLikePatternIsDetectedInStringFields(String maliciousString)
      throws Exception {
    final UUID submissionId = Uuid7.timeBasedUuid();
    // given: a SubmissionPost payload
    submissionRepository.deleteAll();
    SubmissionPost submissionPost =
        SubmissionPost.builder()
            .submissionId(submissionId)
            .bulkSubmissionId(BULK_SUBMISSION_ID)
            .officeAccountNumber(OFFICE_ACCOUNT_NUMBER)
            .submissionPeriod(PERIOD_JAN_25)
            .areaOfLaw(AREA_OF_LAW)
            .status(SubmissionStatus.CREATED)
            .providerUserId(maliciousString)
            .createdByUserId(API_USER_ID)
            .submitted(CREATED_ON.atOffset(ZoneOffset.UTC))
            .build();
    // Get the logger used by the class under test
    ListAppender<ILoggingEvent> listAppender = getILoggingEventListAppender();

    // when: calling POST endpoint for submissions
    mockMvc
        .perform(
            post(SUBMISSIONS_ENDPOINT)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(submissionPost)))
        .andExpect(status().isCreated());

    Submission createdSubmission = submissionRepository.findById(submissionId).orElseThrow();

    // then: submission is correctly created with a warning logged
    assertThat(createdSubmission.getOfficeAccountNumber()).isEqualTo(OFFICE_ACCOUNT_NUMBER);
    assertThat(createdSubmission.getAreaOfLaw()).isEqualTo(AREA_OF_LAW);

    assertThat(
            listAppender.list.stream()
                .filter(
                    event -> event.getFormattedMessage().contains(SUSPICIOUS_SQL_PATTERN_LOG_MSG))
                .count())
        .isEqualTo(1);
  }

  @Test
  void postSubmission_shouldReturnBadRequest_WhenProviderUserIdIsNull() throws Exception {
    final UUID submissionId = Uuid7.timeBasedUuid();
    // given: a SubmissionPost payload with null providerUserId
    submissionRepository.deleteAll();
    SubmissionPost submissionPost =
        SubmissionPost.builder()
            .submissionId(submissionId)
            .bulkSubmissionId(BULK_SUBMISSION_ID)
            .officeAccountNumber(OFFICE_ACCOUNT_NUMBER_1)
            .submissionPeriod(PERIOD_JAN_25)
            .areaOfLaw(AREA_OF_LAW)
            .status(SubmissionStatus.CREATED)
            .providerUserId(null)
            .build();

    // when: calling POST endpoint for submissions, should return a bad request.
    mockMvc
        .perform(
            post(SUBMISSIONS_ENDPOINT)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(submissionPost)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void postSubmission_shouldReturnBadRequest() throws Exception {
    // when: calling post endpoint without a valid payload, should return bad request
    mockMvc
        .perform(
            post(SUBMISSIONS_ENDPOINT)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(new SubmissionPost())))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getSubmission_Returns200() throws Exception {
    createClaimsTestData();

    // when: calling get endpoint with and ID
    MvcResult result =
        mockMvc
            .perform(
                get(SUBMISSION_BY_ID_ENDPOINT, SUBMISSION_1_ID)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    var submissionResult =
        OBJECT_MAPPER.readValue(
            result.getResponse().getContentAsString(), SubmissionResponse.class);

    // then: submission is correctly retrieved
    assertThat(submissionResult.getSubmissionId()).isEqualTo(SUBMISSION_1_ID);
    assertThat(submissionResult.getCalculatedTotalAmount())
        .isEqualTo(
            calculatedFeeDetail1
                .getTotalAmount()
                .add(calculatedFeeDetail2.getTotalAmount())
                .setScale(DECIMAL_PLACES, RoundingMode.HALF_UP));
    assertThat(submissionResult.getNumberOfClaims()).isEqualTo(0);
    assertThat(submissionResult.getMatterStarts().size()).isEqualTo(0);

    assertThat(submissionResult.getProviderUserId()).isEqualTo(BULK_SUBMISSION_CREATED_BY_USER_ID);
  }

  @Test
  void postSubmission_shouldCreateWithErrorMessages() throws Exception {
    final UUID submissionId = Uuid7.timeBasedUuid();
    // given: a SubmissionPost payload with errorMessages
    submissionRepository.deleteAll();
    SubmissionPost submissionPost =
        SubmissionPost.builder()
            .submissionId(submissionId)
            .bulkSubmissionId(BULK_SUBMISSION_ID)
            .officeAccountNumber(OFFICE_ACCOUNT_NUMBER)
            .submissionPeriod(PERIOD_JAN_25)
            .areaOfLaw(AREA_OF_LAW)
            .status(SubmissionStatus.CREATED)
            .providerUserId(BULK_SUBMISSION_CREATED_BY_USER_ID)
            .createdByUserId(API_USER_ID)
            .submitted(CREATED_ON.atOffset(ZoneOffset.UTC))
            .errorMessages(TEST_ERROR_MESSAGE)
            .build();

    // when: calling POST endpoint for submissions
    mockMvc
        .perform(
            post(SUBMISSIONS_ENDPOINT)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(submissionPost)))
        .andExpect(status().isCreated());

    Submission createdSubmission = submissionRepository.findById(submissionId).orElseThrow();

    // then: submission is correctly created with all expected fields
    assertThat(createdSubmission.getId()).isEqualTo(submissionId);
    assertThat(createdSubmission.getBulkSubmissionId()).isEqualTo(BULK_SUBMISSION_ID);
    assertThat(createdSubmission.getOfficeAccountNumber()).isEqualTo(OFFICE_ACCOUNT_NUMBER);
    assertThat(createdSubmission.getAreaOfLaw()).isEqualTo(AREA_OF_LAW);
    assertThat(createdSubmission.getStatus()).isEqualTo(SubmissionStatus.CREATED);
    assertThat(createdSubmission.getErrorMessages()).isEqualTo(TEST_ERROR_MESSAGE);
  }

  @Test
  void getSubmission_shouldReturnErrorMessages() throws Exception {
    // given: a submission with errorMessages
    Submission submissionWithError = submissionRepository.findById(SUBMISSION_1_ID).orElseThrow();
    submissionWithError.setErrorMessages("Error message for GET test");
    submissionRepository.save(submissionWithError);

    // when: calling get endpoint with the ID
    MvcResult result =
        mockMvc
            .perform(
                get(SUBMISSION_BY_ID_ENDPOINT, SUBMISSION_1_ID)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    var submissionResult =
        OBJECT_MAPPER.readValue(
            result.getResponse().getContentAsString(), SubmissionResponse.class);

    // then: submission is correctly returned with errorMessages
    assertThat(submissionResult.getSubmissionId()).isEqualTo(SUBMISSION_1_ID);
    assertThat(submissionResult.getStatus()).isEqualTo(SubmissionStatus.CREATED);
    assertThat(submissionResult.getErrorMessages()).isEqualTo("Error message for GET test");
  }

  @Test
  void getSubmission_ReturnsNotFound() throws Exception {
    // when: calling get endpoint without a valid ID, should return not found
    mockMvc
        .perform(
            get(SUBMISSION_BY_ID_ENDPOINT, UUID.randomUUID())
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isNotFound())
        .andReturn();
  }

  @Test
  void getSubmissions_Returns200() throws Exception {
    // when: calling get all submissions endpoint with a valid office
    MvcResult result =
        mockMvc
            .perform(
                get(SUBMISSIONS_ENDPOINT)
                    .param(PARAM_OFFICES, OFFICE_ACCOUNT_NUMBER_1)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    String responseBody = result.getResponse().getContentAsString();

    // then: submissions are correctly retrieved
    var submissionsResultSet = OBJECT_MAPPER.readValue(responseBody, SubmissionsResultSet.class);
    SubmissionBase submissionBase = submissionsResultSet.getContent().getFirst();
    assertThat(submissionBase.getSubmissionId()).isEqualTo(SUBMISSION_1_ID);
    assertThat(submissionBase.getStatus()).isEqualTo(SubmissionStatus.CREATED);
    assertThat(submissionBase.getProviderUserId()).isEqualTo(BULK_SUBMISSION_CREATED_BY_USER_ID);
  }

  @Test
  void getSubmissions_NoOffices_BadRequest() throws Exception {
    // when: calling get all submissions endpoint without an office, should return bad request
    mockMvc
        .perform(get(SUBMISSIONS_ENDPOINT).header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isBadRequest())
        .andReturn();
  }

  @Test
  void updateSubmission_shouldUpdate() throws Exception {
    // given: a Submission patch payload with the changes to make
    String sqlInjectionString = "'; SELECT * FROM information_schema.tables; --";
    SubmissionPatch patch =
        SubmissionPatch.builder()
            .areaOfLaw(AREA_OF_LAW)
            .legalHelpSubmissionReference(sqlInjectionString)
            .validationMessages(
                List.of(
                    new ValidationMessagePatch()
                        .type(ValidationMessageType.ERROR)
                        .displayMessage(sqlInjectionString + "is not allowed")
                        .source("test")))
            .build();
    // Get the logger used by the class under test
    ListAppender<ILoggingEvent> listAppender = getILoggingEventListAppender();

    // when: calling the patch endpoint
    mockMvc
        .perform(
            patch(SUBMISSION_BY_ID_ENDPOINT, SUBMISSION_1_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patch)))
        .andExpect(status().isNoContent())
        .andReturn();

    // then: should update the submission
    Submission updated = submissionRepository.findById(SUBMISSION_1_ID).orElseThrow();
    assertThat(updated.getId()).isEqualTo(SUBMISSION_1_ID);
    assertThat(updated.getStatus()).isEqualTo(SubmissionStatus.CREATED);
    assertThat(updated.getAreaOfLaw()).isEqualTo(AREA_OF_LAW);

    assertThat(
            listAppender.list.stream()
                .filter(
                    event -> event.getFormattedMessage().contains(SUSPICIOUS_SQL_PATTERN_LOG_MSG))
                .count())
        .isEqualTo(1);
  }

  @Test
  void updateSubmission_shouldPublishValidationEventAndUpdate() throws Exception {
    // given: a submission patch payload changing the status to READY_FOR_VALIDATION
    SubmissionPatch patch =
        SubmissionPatch.builder().status(SubmissionStatus.READY_FOR_VALIDATION).build();

    // when: calling the patch endpoint
    mockMvc
        .perform(
            patch(SUBMISSION_BY_ID_ENDPOINT, SUBMISSION_1_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patch)))
        .andExpect(status().isNoContent())
        .andReturn();

    // then: should update and send validation event
    Submission updated = submissionRepository.findById(SUBMISSION_1_ID).orElseThrow();
    assertThat(updated.getId()).isEqualTo(SUBMISSION_1_ID);
    assertThat(updated.getAreaOfLaw()).isEqualTo(AREA_OF_LAW);
    assertThat(updated.getStatus()).isEqualTo(SubmissionStatus.READY_FOR_VALIDATION);

    ReceiveMessageResponse receiveResp =
        IntegrationTestUtils.receiveMessageResponse(sqsClient, this.queueUrl);
    assertThat(receiveResp.messages()).hasSize(1);
    assertThat(receiveResp.messages().getFirst().body()).contains(updated.getId().toString());
    // Delete the message from the queue.
    IntegrationTestUtils.deleteMessagesFromQueue(sqsClient, this.queueUrl, receiveResp);
  }

  @Test
  void updateSubmission_shouldHandleValidationMessages() throws Exception {
    // given: a submission patch with validation messages
    ValidationMessagePatch messagePatch =
        ValidationMessagePatch.builder()
            .type(ValidationMessageType.WARNING)
            .source("SOURCE")
            .displayMessage("DISPLAY_MESSAGE")
            .build();

    SubmissionPatch patch =
        SubmissionPatch.builder().validationMessages(List.of(messagePatch)).build();

    // when: calling the patch endpoint
    mockMvc
        .perform(
            patch(SUBMISSION_BY_ID_ENDPOINT, SUBMISSION_1_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patch)))
        .andExpect(status().isNoContent())
        .andReturn();

    // then: should save new validation messages and submission remains intact
    Submission updated = submissionRepository.findById(SUBMISSION_1_ID).orElseThrow();
    assertThat(updated.getId()).isEqualTo(SUBMISSION_1_ID);
    assertThat(updated.getStatus()).isEqualTo(SubmissionStatus.CREATED);

    List<ValidationMessageLog> logs = validationMessageLogRepository.findAll();
    assertThat(logs).hasSize(1);
    assertThat(logs.getFirst().getDisplayMessage()).isEqualTo("DISPLAY_MESSAGE");
    assertThat(logs.getFirst().getType()).isEqualTo(ValidationMessageType.WARNING);
    assertThat(logs.getFirst().getSource()).isEqualTo("SOURCE");
    validationMessageLogRepository.deleteAll();
  }

  @Test
  void updateSubmission_shouldUpdateErrorMessages() throws Exception {
    // given: a submission patch payload with errorMessages
    String errorMessage = "Updated error message from integration test";
    SubmissionPatch patch = SubmissionPatch.builder().errorMessages(errorMessage).build();

    // when: calling the patch endpoint
    mockMvc
        .perform(
            patch(SUBMISSION_BY_ID_ENDPOINT, SUBMISSION_1_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patch)))
        .andExpect(status().isNoContent())
        .andReturn();

    // then: should update the submission with errorMessages
    Submission updated = submissionRepository.findById(SUBMISSION_1_ID).orElseThrow();
    assertThat(updated.getId()).isEqualTo(SUBMISSION_1_ID);
    assertThat(updated.getStatus()).isEqualTo(SubmissionStatus.CREATED);
    assertThat(updated.getErrorMessages()).isEqualTo(errorMessage);
  }

  @Test
  void updateSubmission_shouldReturnNotFound() throws Exception {
    // given: a submission patch payload
    SubmissionPatch patch =
        SubmissionPatch.builder().status(SubmissionStatus.READY_FOR_VALIDATION).build();

    // when: calling patch endpoint without a valid submission ID, should return Not Found
    mockMvc
        .perform(
            patch(SUBMISSION_BY_ID_ENDPOINT, UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patch)))
        .andExpect(status().isNotFound())
        .andReturn();
  }

  @Test
  void updateSubmission_shouldReturnBadRequest() throws Exception {
    // given: and invalid JSON payload
    String invalidJson = "{ \"status\": \"INVALID_ENUM\" }";

    // when: calling the patch endpoint, should return Bad Request
    mockMvc
        .perform(
            patch(SUBMISSION_BY_ID_ENDPOINT, SUBMISSION_1_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(invalidJson)))
        .andExpect(status().isBadRequest())
        .andReturn();
  }

  @DisplayName("Should return result with area of law and submission period")
  @Test
  void shouldReturnResultWithAreaOfLawAndSubmissionPeriod() throws Exception {
    final String officeAccountNumber = submission1.getOfficeAccountNumber();
    final AreaOfLaw areaOfLaw = submission1.getAreaOfLaw();
    final String submissionPeriod = submission1.getSubmissionPeriod();
    final SubmissionStatus status = submission1.getStatus();

    MvcResult result =
        mockMvc
            .perform(
                get(SUBMISSIONS_ENDPOINT)
                    .param(PARAM_OFFICES, officeAccountNumber)
                    .param("areaOfLaw", String.valueOf(areaOfLaw))
                    .param("submissionPeriod", submissionPeriod)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    String responseBody = result.getResponse().getContentAsString();

    // then: submissions are correctly retrieved
    var submissionsResultSet = OBJECT_MAPPER.readValue(responseBody, SubmissionsResultSet.class);

    assertThat(submissionsResultSet.getContent().getFirst().getAreaOfLaw()).isEqualTo(areaOfLaw);
    assertThat(submissionsResultSet.getContent().getFirst().getSubmissionPeriod())
        .isEqualTo(submissionPeriod);
    assertThat(submissionsResultSet.getContent().getFirst().getStatus()).isEqualTo(status);
  }

  @Test
  void updateSubmission_shouldUpdateAllClaimsAsInvalid_WhenSubmissionIsInvalid() throws Exception {
    // given: a Submission patch payload with the changes to make
    SubmissionPatch patch =
        SubmissionPatch.builder()
            .areaOfLaw(AREA_OF_LAW)
            .status(SubmissionStatus.VALIDATION_FAILED)
            .build();
    // Verify that Claims are not invalid before patching
    claimRepository
        .findBySubmissionId(submission1.getId())
        .forEach(claim -> assertThat(claim.getStatus()).isNotEqualTo(ClaimStatus.INVALID));

    // when: calling the patch endpoint
    mockMvc
        .perform(
            patch(SUBMISSION_BY_ID_ENDPOINT, submission1.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .content(OBJECT_MAPPER.writeValueAsString(patch)))
        .andExpect(status().isNoContent())
        .andReturn();

    // then: should update the submission and all claims as invalid
    Submission updated = submissionRepository.findById(submission1.getId()).orElseThrow();
    claimRepository
        .findBySubmissionId(submission1.getId())
        .forEach(claim -> assertThat(claim.getStatus()).isEqualTo(ClaimStatus.INVALID));
  }

  // ---- Sorting integration tests ----

  /**
   * Saves two extra submissions for {@code OFFICE_ACCOUNT_NUMBER_1} with periods {@code "DEC-2024"}
   * and {@code "APR-2025"}. Together with the seeded {@code submission1} ({@code "JAN-2025"}),
   * these three periods are chosen so that alphabetical and chronological sort order diverge:
   *
   * <ul>
   *   <li>Alphabetical asc: APR-2025, DEC-2024, JAN-2025 (A &lt; D &lt; J)
   *   <li>Chronological asc: DEC-2024, JAN-2025, APR-2025
   * </ul>
   *
   * @return the two saved submissions, to be passed to {@link
   *     #deleteSubmissionPeriodSortFixtures(List)} after the test.
   */
  private List<Submission> saveSubmissionPeriodSortFixtures() {
    Submission submissionDec2024 =
        Submission.builder()
            .id(UUID.randomUUID())
            .bulkSubmissionId(bulkSubmission.getId())
            .officeAccountNumber(OFFICE_ACCOUNT_NUMBER_1)
            .submissionPeriod(PERIOD_DEC_2024)
            .areaOfLaw(AreaOfLaw.LEGAL_HELP)
            .status(SubmissionStatus.CREATED)
            .createdByUserId(USER_ID)
            .providerUserId(bulkSubmission.getCreatedByUserId())
            .createdOn(CREATED_ON)
            .build();
    Submission submissionApr2025 =
        Submission.builder()
            .id(UUID.randomUUID())
            .bulkSubmissionId(bulkSubmission.getId())
            .officeAccountNumber(OFFICE_ACCOUNT_NUMBER_1)
            .submissionPeriod(PERIOD_APR_2025)
            .areaOfLaw(AreaOfLaw.LEGAL_HELP)
            .status(SubmissionStatus.CREATED)
            .createdByUserId(USER_ID)
            .providerUserId(bulkSubmission.getCreatedByUserId())
            .createdOn(CREATED_ON)
            .build();
    submissionRepository.saveAll(List.of(submissionDec2024, submissionApr2025));
    return List.of(submissionDec2024, submissionApr2025);
  }

  private void deleteSubmissionPeriodSortFixtures(List<Submission> fixtures) {
    submissionRepository.deleteAll(fixtures);
  }

  @Test
  @DisplayName("Sort by submissionPeriod asc returns chronological order, not alphabetical")
  void getSubmissions_sortBySubmissionPeriodAsc_returnsChronologicalOrder() throws Exception {
    // given: DEC-2024 and APR-2025 added alongside seeded submission1 (JAN-2025).
    // Alphabetical asc would give: APR-2025, DEC-2024, JAN-2025
    // Chronological asc should give: DEC-2024, JAN-2025, APR-2025
    List<Submission> fixtures = saveSubmissionPeriodSortFixtures();

    MvcResult result =
        mockMvc
            .perform(
                get(SUBMISSIONS_ENDPOINT)
                    .param(PARAM_OFFICES, OFFICE_ACCOUNT_NUMBER_1)
                    .param(PARAM_SORT, "submissionPeriod,asc")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    var periods =
        OBJECT_MAPPER
            .readValue(result.getResponse().getContentAsString(), SubmissionsResultSet.class)
            .getContent()
            .stream()
            .map(
                uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionBase
                    ::getSubmissionPeriod)
            .toList();

    assertThat(periods).containsExactly(PERIOD_DEC_2024, PERIOD_JAN_2025, PERIOD_APR_2025);

    deleteSubmissionPeriodSortFixtures(fixtures);
  }

  @Test
  @DisplayName("Sort by submissionPeriod desc returns reverse chronological order")
  void getSubmissions_sortBySubmissionPeriodDesc_returnsReverseChronologicalOrder()
      throws Exception {
    // given: DEC-2024 and APR-2025 added alongside seeded submission1 (JAN-2025).
    // Alphabetical desc would give: JAN-2025, DEC-2024, APR-2025
    // Chronological desc should give: APR-2025, JAN-2025, DEC-2024
    List<Submission> fixtures = saveSubmissionPeriodSortFixtures();

    MvcResult result =
        mockMvc
            .perform(
                get(SUBMISSIONS_ENDPOINT)
                    .param(PARAM_OFFICES, OFFICE_ACCOUNT_NUMBER_1)
                    .param(PARAM_SORT, "submissionPeriod,desc")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    var periods =
        OBJECT_MAPPER
            .readValue(result.getResponse().getContentAsString(), SubmissionsResultSet.class)
            .getContent()
            .stream()
            .map(
                uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionBase
                    ::getSubmissionPeriod)
            .toList();

    assertThat(periods).containsExactly(PERIOD_APR_2025, PERIOD_JAN_2025, PERIOD_DEC_2024);

    deleteSubmissionPeriodSortFixtures(fixtures);
  }

  @Test
  @DisplayName("Sort by officeAccountNumber is case-insensitive")
  void getSubmissions_sortByOfficeAccountNumber_isCaseInsensitive() throws Exception {
    // given: two submissions for office1 with mixed-case office numbers
    Submission submissionUpperCase =
        Submission.builder()
            .id(UUID.randomUUID())
            .bulkSubmissionId(bulkSubmission.getId())
            .officeAccountNumber(OFFICE_AAAA01)
            .submissionPeriod(PERIOD_JAN_2025)
            .areaOfLaw(AreaOfLaw.LEGAL_HELP)
            .status(SubmissionStatus.CREATED)
            .createdByUserId(USER_ID)
            .providerUserId(bulkSubmission.getCreatedByUserId())
            .createdOn(CREATED_ON)
            .build();
    Submission submissionLowerCase =
        Submission.builder()
            .id(UUID.randomUUID())
            .bulkSubmissionId(bulkSubmission.getId())
            .officeAccountNumber(OFFICE_AAAA02)
            .submissionPeriod(PERIOD_JAN_2025)
            .areaOfLaw(AreaOfLaw.LEGAL_HELP)
            .status(SubmissionStatus.CREATED)
            .createdByUserId(USER_ID)
            .providerUserId(bulkSubmission.getCreatedByUserId())
            .createdOn(CREATED_ON)
            .build();
    submissionRepository.saveAll(List.of(submissionUpperCase, submissionLowerCase));

    MvcResult result =
        mockMvc
            .perform(
                get(SUBMISSIONS_ENDPOINT)
                    .param(PARAM_OFFICES, OFFICE_AAAA01, OFFICE_AAAA02)
                    .param(PARAM_SORT, "officeAccountNumber,asc")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    var resultSet =
        OBJECT_MAPPER.readValue(
            result.getResponse().getContentAsString(), SubmissionsResultSet.class);
    var officeNumbers =
        resultSet.getContent().stream()
            .map(
                uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionBase
                    ::getOfficeAccountNumber)
            .toList();

    // case-insensitive: "AAAA01" (lowercase: aaaa01) before "aaaa02"
    assertThat(officeNumbers).containsExactly(OFFICE_AAAA01, OFFICE_AAAA02);

    submissionRepository.deleteAll(List.of(submissionUpperCase, submissionLowerCase));
  }

  @Test
  @DisplayName("Invalid sort field returns 400 Bad Request")
  void getSubmissions_invalidSortField_returns400() throws Exception {
    mockMvc
        .perform(
            get(SUBMISSIONS_ENDPOINT)
                .param(PARAM_OFFICES, OFFICE_ACCOUNT_NUMBER_1)
                .param(PARAM_SORT, "unknownField,asc")
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isBadRequest());
  }
}
