package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.*;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_3_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.USER_ID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.BulkSubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationMessageLogRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ValidationMessagesControllerIntegrationTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private SubmissionRepository submissionRepository;

  @Autowired private BulkSubmissionRepository bulkSubmissionRepository;

  @Autowired private ValidationMessageLogRepository validationMessageLogRepository;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String AUTHORIZATION_HEADER = "Authorization";

  // must match application-test.yml for test-runner token
  private static final String AUTHORIZATION_TOKEN = "f67f968e-b479-4e61-b66e-f57984931e56";

  private Submission submission;

  @BeforeAll
  void initialSetup() {
    OBJECT_MAPPER.registerModule(new JavaTimeModule());
  }

  @BeforeEach()
  void setup() {

    // creating some data on DB
    BulkSubmission bulkSubmission =
        BulkSubmission.builder()
            .id(BULK_SUBMISSION_ID)
            .data(new GetBulkSubmission200ResponseDetails())
            .status(BulkSubmissionStatus.READY_FOR_PARSING)
            .createdByUserId(USER_ID)
            .createdOn(Instant.now())
            .updatedOn(Instant.now())
            .build();
    bulkSubmission = bulkSubmissionRepository.save(bulkSubmission);

    submission =
        Submission.builder()
            .id(SUBMISSION_3_ID)
            .bulkSubmissionId(bulkSubmission.getId())
            .officeAccountNumber("office3")
            .submissionPeriod("JAN-25")
            .areaOfLaw("CIVIL")
            .status(SubmissionStatus.CREATED)
            .scheduleNumber("office3/CIVIL")
            .previousSubmissionId(SUBMISSION_3_ID)
            .isNilSubmission(false)
            .numberOfClaims(0)
            .createdByUserId(USER_ID)
            .build();
    submission = submissionRepository.save(submission);
  }

  @AfterEach
  void cleanup() {
    validationMessageLogRepository.deleteAll();
    submissionRepository.deleteAll();
    bulkSubmissionRepository.deleteAll();
  }

  @Test
  void getValidationMessages_shouldReturn200() throws Exception {
    ValidationMessageLog log = new ValidationMessageLog();
    log.setId(Uuid7.timeBasedUuid());
    log.setSubmissionId(submission.getId());
    log.setType(ValidationMessageType.ERROR);
    log.setSource("SOURCE1");
    log.setDisplayMessage("MESSAGE1");
    log.setCreatedOn(Instant.now());
    validationMessageLogRepository.save(log);

    MvcResult mvcResult =
        mockMvc
            .perform(
                get(API_URI_PREFIX + "/validation-messages")
                    .param("submission-id", submission.getId().toString())
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    ValidationMessagesResponse response =
        OBJECT_MAPPER.readValue(
            mvcResult.getResponse().getContentAsString(), ValidationMessagesResponse.class);
    assertThat(response.getTotalElements()).isEqualTo(1);
    ValidationMessageBase msg = response.getContent().getFirst();
    assertThat(msg.getId()).isEqualTo(log.getId());
    assertThat(msg.getType()).isEqualTo(ValidationMessageType.ERROR);
    assertThat(msg.getSource()).isEqualTo("SOURCE1");
    assertThat(msg.getDisplayMessage()).isEqualTo("MESSAGE1");
  }
}
