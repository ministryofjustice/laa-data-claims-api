package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.VALID_CRIME_SCHEDULE_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.VALID_OFFICE_ACCOUNT_NUMBER;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
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

  @Autowired private SqsClient sqsClient;

  @Autowired private SnsClient snsClient;

  @Autowired private EntityManager entityManager;

  @Value("${aws.sqs.queue-name}")
  private String queueName;

  @Value("${aws.sns.topic-arn}")
  private String topicArn;

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
  private static final String PERIOD_DEC_2030 = "DEC-2030";
  private static final String OFFICE_AAAA01 = "AAAA01";
  private static final String OFFICE_AAAA02 = "aaaa02";
  private static final String PARAM_OFFICES = "offices";
  private static final String PARAM_SORT = "sort";

  // A created_on used by the live-submission duplicate tests. It sits after any realistic
  // uniqueness cutoff (the ${live_submission_uniqueness_cutoff} Flyway placeholder, which defaults
  // to the epoch locally) so the uq_submission_live_office_aol_period index covers these rows.
  private static final LocalDate POST_CUTOVER_DATE = LocalDate.of(2026, Month.SEPTEMBER, 2);

  @BeforeAll
  void initialSetup() {
    OBJECT_MAPPER.registerModule(new JavaTimeModule());

    // create the queue if it doesn't exist
    sqsClient.createQueue(builder -> builder.queueName(queueName));

    // then get its URL
    GetQueueUrlResponse queueUrlResponse =
        sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build());
    this.queueUrl = queueUrlResponse.queueUrl();

    // get queue arn
    GetQueueAttributesResponse queueAttributes =
        sqsClient.getQueueAttributes(
            GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN)
                .build());

    String queueArn = queueAttributes.attributes().get(QueueAttributeName.QUEUE_ARN);

    // create SNS topic
    snsClient.createTopic(CreateTopicRequest.builder().name("claims-events").build());

    // subscribe queue to topic
    snsClient.subscribe(
        SubscribeRequest.builder()
            .topicArn(topicArn)
            .protocol("sqs")
            .endpoint(queueArn)
            .attributes(Map.of("RawMessageDelivery", "true"))
            .build());
  }

  @BeforeEach
  void setup() {
    seedSubmissionsData();
  }

  // Scenario 1 - No pageable parameters
  // Explanation: The API defaults to page=0 and size=20 when no pageable parameters are
  // supplied. This test seeds >20 records then calls the endpoint without page/size/sort and
  // verifies the response defaults to the first page (0) with size 20 and that the returned
  // content is ordered by UUID7 (time-based) ascending.
  @Test
  @DisplayName(
      "No pageable parameters: defaults to page 0 and size 20; returns first page sorted by UUID7 asc")
  void getSubmissionsNoPageableParametersDefaultsToPageZeroSize20AndSortedByUuid7Asc()
      throws Exception {
    // start with a clean store for deterministic assertions
    submissionRepository.deleteAll();

    final String office = VALID_OFFICE_ACCOUNT_NUMBER;
    // create 25 submissions to make truncation obvious
    List<Submission> fixtures = saveManySubmissions(office, 25);

    MvcResult result =
        mockMvc
            .perform(
                get(SUBMISSIONS_ENDPOINT)
                    .param(PARAM_OFFICES, office)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    SubmissionsResultSet resultSet =
        OBJECT_MAPPER.readValue(
            result.getResponse().getContentAsString(), SubmissionsResultSet.class);

    // totalElements should reflect all persisted records but content should be limited to default
    // size
    assertThat(resultSet.getTotalElements()).isEqualTo(fixtures.size());
    assertThat(resultSet.getSize()).isEqualTo(20);
    assertThat(resultSet.getNumber()).isEqualTo(0);
    // content should be the first page (20 items)
    assertThat(resultSet.getContent()).hasSize(20);

    // verify ordering: when page is applied with no explicit sort, the service should
    // return results ordered by UUID7 (time-based) ascending. Build expected ordering
    // from fixtures using their ids (Uuid7.timeBasedUuid() was used when creating fixtures).
    var returnedIds = resultSet.getContent().stream().map(SubmissionBase::getSubmissionId).toList();

    var expectedIds =
        fixtures.stream()
            .map(Submission::getId)
            .sorted((a, b) -> a.compareTo(b)) // ascending UUID7 ordering
            .limit(20)
            .toList();

    assertThat(returnedIds).containsExactlyElementsOf(expectedIds);

    submissionRepository.deleteAll(fixtures);
  }

  // Scenario 2 - Size only
  // Explanation: A request with only '?size=10' should be treated as if no pageable params were
  // supplied; the API should default to page=0 and size=20. This test clears seeded data,
  // creates a dataset (>20), requests with only size=10 and verifies the defaults are applied
  // (page 0, size 20) and the returned content is limited to 20 items.
  @Test
  @DisplayName("Size-only request: defaults to page 0 and size 20 when only size is supplied")
  void getSubmissionsSizeOnlyDefaultsToPageZeroAndSize20() throws Exception {
    submissionRepository.deleteAll();

    final String office = VALID_OFFICE_ACCOUNT_NUMBER;
    // create 25 submissions to ensure default size truncation is observable
    List<Submission> fixtures = saveManySubmissions(office, 25);

    MvcResult result =
        mockMvc
            .perform(
                get(SUBMISSIONS_ENDPOINT)
                    .param(PARAM_OFFICES, office)
                    .param("size", "10") // only size supplied
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    SubmissionsResultSet resultSet =
        OBJECT_MAPPER.readValue(
            result.getResponse().getContentAsString(), SubmissionsResultSet.class);

    // Should default to page 0 and default size 20 even though size=10 was supplied
    assertThat(resultSet.getNumber()).isEqualTo(0);
    assertThat(resultSet.getSize()).isEqualTo(20);
    assertThat(resultSet.getContent()).hasSize(20);
    // totalElements should reflect the total persisted records
    assertThat(resultSet.getTotalElements()).isEqualTo(fixtures.size());

    submissionRepository.deleteAll(fixtures);
  }

  // Scenario 3 - Explicit page and size
  // Explanation: A request with '?page=1&size=10' should return the second page. For
  // determinism this test clears the DB, seeds 25 submissions with distinct createdOn values,
  // requests page=1,size=10 sorted by createdOn ascending and asserts the returned slice
  // matches the expected elements.
  @Test
  @DisplayName("Explicit page and size returns the expected slice of results")
  void getSubmissionsExplicitPageAndSizeReturnsExpectedSlice() throws Exception {
    submissionRepository.deleteAll();

    final String office = VALID_OFFICE_ACCOUNT_NUMBER;
    List<Submission> fixtures = saveManySubmissions(office, 25);

    // Sort by createdOn ascending so paging slice is deterministic
    MvcResult result =
        mockMvc
            .perform(
                get(SUBMISSIONS_ENDPOINT)
                    .param(PARAM_OFFICES, office)
                    .param("page", "1")
                    .param("size", "10")
                    .param(PARAM_SORT, "createdOn,asc")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    SubmissionsResultSet resultSet =
        OBJECT_MAPPER.readValue(
            result.getResponse().getContentAsString(), SubmissionsResultSet.class);

    // page index should be 1 and size 10
    assertThat(resultSet.getNumber()).isEqualTo(1);
    assertThat(resultSet.getSize()).isEqualTo(10);
    assertThat(resultSet.getContent()).hasSize(10);

    // verify returned slice corresponds to fixtures[10..19] when ordered by createdOn asc
    var returnedCreatedOns =
        resultSet.getContent().stream()
            .map(submission -> Instant.from(submission.getSubmitted()))
            .toList();
    var expectedCreatedOns =
        fixtures.stream()
            .sorted((a, b) -> a.getCreatedOn().compareTo(b.getCreatedOn()))
            .skip(10)
            .limit(10)
            .map(Submission::getCreatedOn)
            .toList();

    assertThat(returnedCreatedOns).containsExactlyElementsOf(expectedCreatedOns);

    submissionRepository.deleteAll(fixtures);
  }

  @Test
  @DisplayName("GET v1/submissions - page size limits results to requested size")
  void pageSizeLimitsResultsV1Submissions() throws Exception {
    submissionRepository.deleteAll();

    final String office = VALID_OFFICE_ACCOUNT_NUMBER;
    // create 25 submissions to make truncation obvious
    List<Submission> fixtures = saveManySubmissions(office, 25);

    MvcResult result =
        mockMvc
            .perform(
                get(SUBMISSIONS_ENDPOINT)
                    .param(PARAM_OFFICES, office)
                    .param("size", "10")
                    .param("page", "0")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    SubmissionsResultSet resultSet =
        OBJECT_MAPPER.readValue(
            result.getResponse().getContentAsString(), SubmissionsResultSet.class);

    assertThat(resultSet.getTotalElements()).isEqualTo(fixtures.size());
    assertThat(resultSet.getSize()).isEqualTo(10);
    assertThat(resultSet.getContent()).hasSize(10);

    submissionRepository.deleteAll(fixtures);
  }

  @Test
  @DisplayName("GET v1/submissions - page offset returns correct subset (items 11-20)")
  void pageOffsetReturnsCorrectSubsetV1Submissions() throws Exception {
    submissionRepository.deleteAll();

    final String office = VALID_OFFICE_ACCOUNT_NUMBER;
    List<Submission> fixtures = saveManySubmissions(office, 25);

    MvcResult page0 =
        mockMvc
            .perform(
                get(SUBMISSIONS_ENDPOINT)
                    .param(PARAM_OFFICES, office)
                    .param("page", "0")
                    .param("size", "10")
                    .param(PARAM_SORT, "createdOn,asc")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    MvcResult page1 =
        mockMvc
            .perform(
                get(SUBMISSIONS_ENDPOINT)
                    .param(PARAM_OFFICES, office)
                    .param("page", "1")
                    .param("size", "10")
                    .param(PARAM_SORT, "createdOn,asc")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    SubmissionsResultSet r0 =
        OBJECT_MAPPER.readValue(
            page0.getResponse().getContentAsString(), SubmissionsResultSet.class);
    SubmissionsResultSet r1 =
        OBJECT_MAPPER.readValue(
            page1.getResponse().getContentAsString(), SubmissionsResultSet.class);

    assertThat(r0.getContent()).hasSize(10);
    assertThat(r1.getContent()).hasSize(10);

    var returnedCreatedOns0 =
        r0.getContent().stream().map(submission -> submission.getSubmitted()).toList();
    var returnedCreatedOns1 =
        r1.getContent().stream().map(submission -> submission.getSubmitted()).toList();

    // Ensure pages don't overlap
    assertThat(returnedCreatedOns0).doesNotContainAnyElementsOf(returnedCreatedOns1);

    submissionRepository.deleteAll(fixtures);
  }

  @Test
  @DisplayName("Sort by submissionPeriod asc returns chronological order, not alphabetical")
  void getSubmissionsSortBySubmissionPeriodAscReturnsChronologicalOrder() throws Exception {
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
            .map(SubmissionBase::getSubmissionPeriod)
            .toList();

    assertThat(periods).containsExactly(PERIOD_DEC_2024, PERIOD_JAN_2025, PERIOD_APR_2025);

    deleteSubmissionPeriodSortFixtures(fixtures);
  }

  @Test
  @DisplayName("Sort by submissionPeriod desc returns reverse chronological order")
  void getSubmissionsSortBySubmissionPeriodDescReturnsReverseChronologicalOrder() throws Exception {
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
            .map(SubmissionBase::getSubmissionPeriod)
            .toList();

    assertThat(periods).containsExactly(PERIOD_APR_2025, PERIOD_JAN_2025, PERIOD_DEC_2024);

    deleteSubmissionPeriodSortFixtures(fixtures);
  }

  @Test
  @DisplayName("Sort by officeAccountNumber is case-insensitive")
  void getSubmissionsSortByOfficeAccountNumberIsCaseInsensitive() throws Exception {
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
        resultSet.getContent().stream().map(SubmissionBase::getOfficeAccountNumber).toList();

    // case-insensitive: "AAAA01" (lowercase: aaaa01) before "aaaa02"
    assertThat(officeNumbers).containsExactly(OFFICE_AAAA01, OFFICE_AAAA02);

    submissionRepository.deleteAll(List.of(submissionUpperCase, submissionLowerCase));
  }

  @Test
  @DisplayName("Invalid sort field returns 400 Bad Request")
  void getSubmissionsInvalidSortFieldReturns400() throws Exception {
    mockMvc
        .perform(
            get(SUBMISSIONS_ENDPOINT)
                .param(PARAM_OFFICES, OFFICE_ACCOUNT_NUMBER_1)
                .param(PARAM_SORT, "unknownField,asc")
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isBadRequest());
  }

  /**
   * Helper to create a number of submissions for the given office using monotonically increasing
   * createdOn values so sort order is deterministic for tests that rely on createdOn.
   */
  private List<Submission> saveManySubmissions(String officeAccountNumber, int count) {
    List<Submission> list = new java.util.ArrayList<>();
    for (int i = 0; i < count; i++) {
      Submission s =
          Submission.builder()
              .id(Uuid7.timeBasedUuid())
              .bulkSubmissionId(null)
              .officeAccountNumber(officeAccountNumber)
              .submissionPeriod(PERIOD_JAN_2025)
              .areaOfLaw(AreaOfLaw.LEGAL_HELP)
              .status(SubmissionStatus.VALIDATION_FAILED)
              .createdByUserId(USER_ID)
              .providerUserId("user-" + i)
              .createdOn(CREATED_ON.plusSeconds(i))
              .build();
      list.add(s);
    }
    submissionRepository.saveAll(list);
    return list;
  }

  @Test
  @DisplayName("Should create a submission")
  void postSubmissionShouldCreate() throws Exception {
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
  @DisplayName(
      "Should not create a submission and should return bad request when provideUserId is not set")
  void postSubmissionShouldReturnBadRequestWhenProviderUserIdIsNull() throws Exception {
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
            .isNilSubmission(false)
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
  @DisplayName(
      "Should not create a submission and should return bad request when nilFlag is not set")
  void postSubmissionShouldReturnBadRequestWhenNilFlagIdIsNull() throws Exception {
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
            .isNilSubmission(null)
            .status(SubmissionStatus.CREATED)
            .providerUserId(USER_ID)
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
  @DisplayName(
      "Should not create a submission and should return bad request when missing submission period")
  void postSubmissionShouldReturnBadRequestWhenSubmissionPeriodIsNull() throws Exception {
    final UUID submissionId = Uuid7.timeBasedUuid();
    // given: a SubmissionPost payload with null providerUserId
    submissionRepository.deleteAll();
    SubmissionPost submissionPost =
        SubmissionPost.builder()
            .submissionId(submissionId)
            .bulkSubmissionId(BULK_SUBMISSION_ID)
            .officeAccountNumber(OFFICE_ACCOUNT_NUMBER_1)
            .submissionPeriod(null)
            .areaOfLaw(AREA_OF_LAW)
            .isNilSubmission(false)
            .status(SubmissionStatus.CREATED)
            .providerUserId(USER_ID)
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
  @DisplayName(
      "Should not create a submission and should return bad request when missing area of law")
  void postSubmissionShouldReturnBadRequestWhenAreaOfLawIsNull() throws Exception {
    final UUID submissionId = Uuid7.timeBasedUuid();
    // given: a SubmissionPost payload with null providerUserId
    submissionRepository.deleteAll();
    SubmissionPost submissionPost =
        SubmissionPost.builder()
            .submissionId(submissionId)
            .bulkSubmissionId(BULK_SUBMISSION_ID)
            .officeAccountNumber(OFFICE_ACCOUNT_NUMBER_1)
            .submissionPeriod(PERIOD_APR_2025)
            .areaOfLaw(null)
            .isNilSubmission(false)
            .status(SubmissionStatus.CREATED)
            .providerUserId(USER_ID)
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
  @DisplayName("Should create a submission and not pre-validate when submission status is CREATED")
  void postSubmissionShouldCreateAndNotValidateWhenStatusIsCreated() throws Exception {
    final UUID submissionId = Uuid7.timeBasedUuid();
    // given: a SubmissionPost payload with invalid crimeLowerScheduleNumber
    submissionRepository.deleteAll();
    SubmissionPost submissionPost =
        SubmissionPost.builder()
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .submissionId(submissionId)
            .bulkSubmissionId(null)
            .createdByUserId(USER_ID)
            .numberOfClaims(1)
            .crimeLowerScheduleNumber(null)
            .isNilSubmission(false)
            .officeAccountNumber(OFFICE_ACCOUNT_NUMBER)
            .providerUserId(USER_ID)
            .status(SubmissionStatus.CREATED)
            .submissionPeriod(PERIOD_APR_2025)
            .submitted(CREATED_ON.atOffset(ZoneOffset.UTC))
            .build();

    mockMvc
        .perform(
            post(SUBMISSIONS_ENDPOINT)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(submissionPost)))
        .andExpect(status().isCreated())
        .andReturn();
  }

  @Test
  @DisplayName("Should validate and create a submission when submission status is not CREATED")
  void postSubmissionShouldCreateWhenValidNilSubmission() throws Exception {
    final UUID submissionId = Uuid7.timeBasedUuid();
    // given: a valid nil submissionSubmissionPost payload
    submissionRepository.deleteAll();
    SubmissionPost submissionPost =
        SubmissionPost.builder()
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .submissionId(submissionId)
            .bulkSubmissionId(null)
            .createdByUserId(USER_ID)
            .numberOfClaims(0)
            .crimeLowerScheduleNumber(VALID_CRIME_SCHEDULE_NUMBER)
            .isNilSubmission(true)
            .officeAccountNumber(VALID_OFFICE_ACCOUNT_NUMBER)
            .providerUserId(USER_ID)
            .status(SubmissionStatus.READY_FOR_VALIDATION)
            .submissionPeriod(PERIOD_APR_2025)
            .submitted(CREATED_ON.atOffset(ZoneOffset.UTC))
            .build();

    mockMvc
        .perform(
            post(SUBMISSIONS_ENDPOINT)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(submissionPost)))
        .andExpect(status().isCreated())
        .andReturn();

    // Added this because we publish event after successfully saving and validating
    ReceiveMessageResponse receiveResp =
        IntegrationTestUtils.receiveMessageResponse(sqsClient, this.queueUrl);
    IntegrationTestUtils.deleteMessagesFromQueue(sqsClient, this.queueUrl, receiveResp);
  }

  @ParameterizedTest
  @DisplayName(
      "Should validate and not create a submission when when status is not CREATED and missing schedule number/reference for selected are of law")
  @EnumSource(AreaOfLaw.class)
  void postNilSubmissionShouldReturnBadRequestWithValidationErrorDetailsWhenSchemaValidationFails(
      AreaOfLaw areaOfLaw) throws Exception {
    final UUID submissionId = Uuid7.timeBasedUuid();
    // given: a SubmissionPost payload with null/invalid crimeLowerScheduleNumber
    submissionRepository.deleteAll();
    SubmissionPost submissionPost =
        SubmissionPost.builder()
            .areaOfLaw(areaOfLaw)
            .submissionId(submissionId)
            .bulkSubmissionId(null)
            .createdByUserId(USER_ID)
            .numberOfClaims(0)
            .crimeLowerScheduleNumber(null)
            .legalHelpSubmissionReference(null)
            .mediationSubmissionReference(null)
            .isNilSubmission(true)
            .officeAccountNumber(OFFICE_ACCOUNT_NUMBER)
            .providerUserId(USER_ID)
            .status(SubmissionStatus.READY_FOR_VALIDATION)
            .submissionPeriod(PERIOD_APR_2025)
            .build();

    mockMvc
        .perform(
            post(SUBMISSIONS_ENDPOINT)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(submissionPost)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Bad Request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value("Submission failed validation"))
        .andExpect(jsonPath("$.issues[0].code").value("SCHEMA_VALIDATION_ERROR"))
        .andReturn();
  }

  @Test
  @DisplayName(
      "Should validate and not create a submission when when status is not CREATED and there is a duplicate existing submission")
  void postNilSubmissionShouldReturnBadRequestWithValidationErrorDetailsWhenDuplicateSubmission()
      throws Exception {
    final UUID submissionId = Uuid7.timeBasedUuid();
    Submission submission1 =
        Submission.builder()
            .id(UUID.randomUUID())
            .bulkSubmissionId(null)
            .officeAccountNumber(VALID_OFFICE_ACCOUNT_NUMBER)
            .submissionPeriod(PERIOD_APR_2025)
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .status(SubmissionStatus.VALIDATION_SUCCEEDED)
            .createdByUserId(USER_ID)
            .numberOfClaims(0)
            .createdOn(CREATED_ON)
            .crimeLowerScheduleNumber(VALID_CRIME_SCHEDULE_NUMBER)
            .providerUserId(USER_ID)
            .isNilSubmission(true)
            .build();

    submissionRepository.save(submission1);

    SubmissionPost submissionPost =
        SubmissionPost.builder()
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .officeAccountNumber(VALID_OFFICE_ACCOUNT_NUMBER)
            .submissionId(submissionId)
            .bulkSubmissionId(null)
            .createdByUserId(USER_ID)
            .numberOfClaims(0)
            .crimeLowerScheduleNumber(VALID_CRIME_SCHEDULE_NUMBER)
            .isNilSubmission(true)
            .providerUserId(USER_ID)
            .status(SubmissionStatus.READY_FOR_VALIDATION)
            .submissionPeriod(PERIOD_APR_2025)
            .build();
    mockMvc
        .perform(
            post(SUBMISSIONS_ENDPOINT)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(submissionPost)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Bad Request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value("Submission failed validation"))
        .andExpect(
            jsonPath("$.issues[0].message")
                .value(
                    "Submission already exists for Office (0AB342), Area of Law (CRIME LOWER), Period (APR-2025)"))
        .andExpect(jsonPath("$.issues[0].code").value("SUBMISSION_ALREADY_EXISTS"))
        .andReturn();
  }

  @Test
  @DisplayName(
      "Should return 409 Conflict when a second live CREATED submission duplicates the office, area of law and period")
  void postSubmissionShouldReturnConflictWhenDuplicateLiveSubmission() throws Exception {
    submissionRepository.deleteAll();

    SubmissionPost firstSubmission =
        createdSubmissionPost(Uuid7.timeBasedUuid())
            .officeAccountNumber(OFFICE_ACCOUNT_NUMBER)
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .submissionPeriod(PERIOD_APR_2025);

    mockMvc
        .perform(
            post(SUBMISSIONS_ENDPOINT)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(firstSubmission)))
        .andExpect(status().isCreated());

    SubmissionPost duplicateSubmission =
        createdSubmissionPost(Uuid7.timeBasedUuid())
            .officeAccountNumber(OFFICE_ACCOUNT_NUMBER)
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .submissionPeriod(PERIOD_APR_2025);

    mockMvc
        .perform(
            post(SUBMISSIONS_ENDPOINT)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(duplicateSubmission)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Conflict"))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(
            jsonPath("$.type")
                .value(
                    "https://claimsdata.payments.laa.justice.gov.uk/errors/duplicate-submission"))
        .andExpect(jsonPath("$.detail").value(containsString(PERIOD_APR_2025)));

    assertThat(submissionRepository.findAll()).hasSize(1);
  }

  @Test
  @DisplayName(
      "Should create a submission when an existing REPLACED submission shares the office, area of law and period")
  void postSubmissionShouldCreateWhenExistingDuplicateIsReplaced() throws Exception {
    submissionRepository.deleteAll();

    Submission replacedSubmission =
        Submission.builder()
            .id(UUID.randomUUID())
            .bulkSubmissionId(null)
            .officeAccountNumber(OFFICE_ACCOUNT_NUMBER)
            .submissionPeriod(PERIOD_APR_2025)
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .status(SubmissionStatus.REPLACED)
            .createdByUserId(USER_ID)
            .providerUserId(USER_ID)
            .createdOn(POST_CUTOVER_DATE.atStartOfDay().toInstant(ZoneOffset.UTC))
            .build();
    submissionRepository.save(replacedSubmission);

    SubmissionPost newSubmission =
        createdSubmissionPost(Uuid7.timeBasedUuid())
            .officeAccountNumber(OFFICE_ACCOUNT_NUMBER)
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .submissionPeriod(PERIOD_APR_2025);

    mockMvc
        .perform(
            post(SUBMISSIONS_ENDPOINT)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(newSubmission)))
        .andExpect(status().isCreated());

    assertThat(submissionRepository.findAll()).hasSize(2);
  }

  private SubmissionPost createdSubmissionPost(UUID submissionId) {
    return SubmissionPost.builder()
        .submissionId(submissionId)
        .bulkSubmissionId(null)
        .createdByUserId(USER_ID)
        .providerUserId(USER_ID)
        .status(SubmissionStatus.CREATED)
        .submitted(POST_CUTOVER_DATE.atStartOfDay().atOffset(ZoneOffset.UTC))
        .build();
  }

  @Test
  @DisplayName(
      "Should validate and not create a submission when when status is not CREATED and submission period is too early")
  void
      postNilSubmissionShouldReturnBadRequestWithValidationErrorDetailsWhenSubmissionPeriodMinimumFailure()
          throws Exception {
    final UUID submissionId = Uuid7.timeBasedUuid();
    // given: a SubmissionPost payload with invalid submissionPeriod
    submissionRepository.deleteAll();
    SubmissionPost submissionPost =
        SubmissionPost.builder()
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .submissionId(submissionId)
            .bulkSubmissionId(null)
            .createdByUserId(USER_ID)
            .numberOfClaims(0)
            .crimeLowerScheduleNumber(VALID_CRIME_SCHEDULE_NUMBER)
            .isNilSubmission(true)
            .officeAccountNumber(VALID_OFFICE_ACCOUNT_NUMBER)
            .providerUserId(USER_ID)
            .status(SubmissionStatus.READY_FOR_VALIDATION)
            .submissionPeriod(PERIOD_JAN_2025)
            .build();

    mockMvc
        .perform(
            post(SUBMISSIONS_ENDPOINT)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(submissionPost)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Bad Request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value("Submission failed validation"))
        .andExpect(jsonPath("$.issues[0].code").value("SUBMISSION_VALIDATION_MINIMUM_PERIOD"))
        .andReturn();
  }

  @Test
  @DisplayName(
      "Should validate and not create a submission when when status is not CREATED and submission period is in the future")
  void
      postNilSubmissionShouldReturnBadRequestWithValidationErrorDetailsWhenSubmissionPeriodFutureFailure()
          throws Exception {
    final UUID submissionId = Uuid7.timeBasedUuid();
    // given: a SubmissionPost payload with invalid submissionPeriod
    submissionRepository.deleteAll();
    SubmissionPost submissionPost =
        SubmissionPost.builder()
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .submissionId(submissionId)
            .bulkSubmissionId(null)
            .createdByUserId(USER_ID)
            .numberOfClaims(0)
            .crimeLowerScheduleNumber(VALID_CRIME_SCHEDULE_NUMBER)
            .isNilSubmission(true)
            .officeAccountNumber(VALID_OFFICE_ACCOUNT_NUMBER)
            .providerUserId(USER_ID)
            .status(SubmissionStatus.READY_FOR_VALIDATION)
            .submissionPeriod(PERIOD_DEC_2030)
            .build();

    mockMvc
        .perform(
            post(SUBMISSIONS_ENDPOINT)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(submissionPost)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Bad Request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value("Submission failed validation"))
        .andExpect(jsonPath("$.issues[0].code").value("SUBMISSION_PERIOD_FUTURE_MONTH"))
        .andReturn();
  }

  @Test
  @DisplayName(
      "Should validate and not create a submission when when status is not CREATED and the office code is invalid")
  void postNilSubmissionShouldReturnBadRequestWithValidationErrorDetailsWhenInvalidOffice()
      throws Exception {
    final UUID submissionId = Uuid7.timeBasedUuid();

    // given: a SubmissionPost payload with invalid officeAccountNumber
    submissionRepository.deleteAll();
    SubmissionPost submissionPost =
        SubmissionPost.builder()
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .submissionId(submissionId)
            .bulkSubmissionId(null)
            .createdByUserId(USER_ID)
            .numberOfClaims(0)
            .crimeLowerScheduleNumber(VALID_CRIME_SCHEDULE_NUMBER)
            .isNilSubmission(true)
            .officeAccountNumber("BAD_OFFICE_ACCOUNT_NUMBER")
            .providerUserId(USER_ID)
            .status(SubmissionStatus.READY_FOR_VALIDATION)
            .submissionPeriod(PERIOD_APR_2025)
            .build();

    mockMvc
        .perform(
            post(SUBMISSIONS_ENDPOINT)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(submissionPost)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Bad Request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value("Submission failed validation"))
        .andExpect(
            jsonPath("$.issues[0].message")
                .value(
                    "Office Account Number must be exactly 6 characters containing uppercase letters and numbers."))
        .andExpect(jsonPath("$.issues[0].code").value("SCHEMA_VALIDATION_ERROR"))
        .andReturn();
  }

  @Test
  @DisplayName(
      "Should validate and not create a submission when status is not CREATED and nil submission flag is true, but there are no related claims")
  void postNilSubmissionShouldReturnBadRequestWithValidationErrorDetailsWhenNILSubmissionSpecific()
      throws Exception {
    final UUID submissionId = Uuid7.timeBasedUuid();

    submissionRepository.deleteAll();
    SubmissionPost submissionPost =
        SubmissionPost.builder()
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .submissionId(submissionId)
            .bulkSubmissionId(null)
            .createdByUserId(USER_ID)
            .numberOfClaims(0)
            .crimeLowerScheduleNumber(VALID_CRIME_SCHEDULE_NUMBER)
            .isNilSubmission(false)
            .officeAccountNumber(VALID_OFFICE_ACCOUNT_NUMBER)
            .providerUserId(USER_ID)
            .status(SubmissionStatus.READY_FOR_VALIDATION)
            .submissionPeriod(PERIOD_APR_2025)
            .submitted(CREATED_ON.atOffset(ZoneOffset.UTC))
            .build();

    mockMvc
        .perform(
            post(SUBMISSIONS_ENDPOINT)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(submissionPost)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Bad Request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value("Submission failed validation"))
        .andExpect(jsonPath("$.issues[0].code").value("NON_NIL_SUBMISSION_CONTAINS_NO_CLAIMS"))
        .andReturn();
  }

  @Test
  @DisplayName("Should validate and not create a submission when the submission status is invalid")
  void postNilSubmissionShouldReturnBadRequestWithValidationErrorDetailsWhenStatusIsInvalid()
      throws Exception {
    final UUID submissionId = Uuid7.timeBasedUuid();

    submissionRepository.deleteAll();
    SubmissionPost submissionPost =
        SubmissionPost.builder()
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .submissionId(submissionId)
            .bulkSubmissionId(null)
            .createdByUserId(USER_ID)
            .numberOfClaims(0)
            .crimeLowerScheduleNumber("num")
            .isNilSubmission(true)
            .officeAccountNumber("0P322F")
            .providerUserId(USER_ID)
            .submissionPeriod(PERIOD_APR_2025)
            .status(SubmissionStatus.VALIDATION_FAILED)
            .build();

    mockMvc
        .perform(
            post(SUBMISSIONS_ENDPOINT)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(submissionPost)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Bad Request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value("Submission failed validation"))
        .andExpect(
            jsonPath("$.issues[0].message")
                .value("Submission cannot be validated in state VALIDATION_FAILED"))
        .andExpect(jsonPath("$.issues[0].code").value("INCORRECT_SUBMISSION_STATUS_FOR_VALIDATION"))
        .andReturn();
  }

  @Test
  @DisplayName("POST without valid payload returns Bad Request")
  void postSubmissionShouldReturnBadRequest() throws Exception {
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
  @DisplayName("GET submission by id returns 200 and correct payload")
  void getSubmissionReturns200() throws Exception {
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
  @DisplayName("POST submission with error messages creates submission and persists messages")
  void postSubmissionShouldCreateWithErrorMessages() throws Exception {
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
  @DisplayName("GET submission returns persisted error messages")
  void getSubmissionShouldReturnErrorMessages() throws Exception {
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
  @DisplayName("GET submission with unknown id returns 404 Not Found")
  void getSubmissionReturnsNotFound() throws Exception {
    // when: calling get endpoint without a valid ID, should return not found
    mockMvc
        .perform(
            get(SUBMISSION_BY_ID_ENDPOINT, UUID.randomUUID())
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isNotFound())
        .andReturn();
  }

  @Test
  @DisplayName("GET submissions for office returns 200 and expected first result")
  void getSubmissionsReturns200() throws Exception {
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
  @DisplayName("GET submissions without offices parameter returns Bad Request")
  void getSubmissionsNoOfficesBadRequest() throws Exception {
    // when: calling get all submissions endpoint without an office, should return bad request
    mockMvc
        .perform(get(SUBMISSIONS_ENDPOINT).header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isBadRequest())
        .andReturn();
  }

  @Test
  @DisplayName("PATCH submission updates fields and logs suspicious SQL-like patterns")
  void updateSubmissionShouldUpdate() throws Exception {
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
  @DisplayName("PATCH submission to READY_FOR_VALIDATION publishes validation event and updates")
  void updateSubmissionShouldPublishValidationEventAndUpdate() throws Exception {
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
  @DisplayName("PATCH submission with validation messages persists messages and keeps submission")
  void updateSubmissionShouldHandleValidationMessages() throws Exception {
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
  @DisplayName("PATCH submission updates error messages on the submission")
  void updateSubmissionShouldUpdateErrorMessages() throws Exception {
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
  @DisplayName("PATCH unknown submission id returns 404 Not Found")
  void updateSubmissionShouldReturnNotFound() throws Exception {
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
  @DisplayName("PATCH submission with invalid payload returns Bad Request")
  void updateSubmissionShouldReturnBadRequest() throws Exception {
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
  @DisplayName("When submission set to VALIDATION_FAILED all related claims are marked INVALID")
  void updateSubmissionShouldUpdateAllClaimsAsInvalidWhenSubmissionIsInvalid() throws Exception {
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
    submissionRepository.findById(submission1.getId()).orElseThrow();
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
  @Transactional
  @DisplayName("Submission JSON contract shape remains unchanged for amended claims")
  void submissionJsonContractIsUnchangedForAmendedClaims() throws Exception {
    // Setup an Amended Submission (1 Claim, 2 Fee Rows)
    Submission amendedSub = createIsolatedSubmission();
    Claim amendedClaim = createClaimForSubmission(amendedSub, 1);
    createFeeDetail(
        amendedClaim, BigDecimal.valueOf(100.00), OffsetDateTime.now().minusDays(1), null);
    createFeeDetail(amendedClaim, BigDecimal.valueOf(250.00), OffsetDateTime.now(), null);

    entityManager.flush();
    entityManager.clear();

    mockMvc
        .perform(
            get("/api/v1/submissions/{id}", amendedSub.getId())
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())

        // 1. Prove the calculated total is the ONLY thing reflecting the amendment
        .andExpect(jsonPath("$.calculated_total_amount").value(250.00))

        // 2. Prove the shape/size of the nested collections is intact
        .andExpect(jsonPath("$.claims", hasSize(1)))
        .andExpect(jsonPath("$.claims[0].status").value("VALID"))

        // 3. Prove NO amendment-specific fields were added to the payload shape
        .andExpect(jsonPath("$.amendment_flags").doesNotExist())
        .andExpect(jsonPath("$.is_amended").doesNotExist())
        .andExpect(jsonPath("$.claims[0].claim_amendment_id").doesNotExist());
  }
}
