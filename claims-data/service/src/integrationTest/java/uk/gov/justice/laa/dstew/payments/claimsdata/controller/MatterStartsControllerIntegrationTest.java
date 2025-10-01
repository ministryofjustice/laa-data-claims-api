package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.MatterStart;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.BulkSubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.MatterStartRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@TestInstance(Lifecycle.PER_CLASS)
public class MatterStartsControllerIntegrationTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private SubmissionRepository submissionRepository;

  @Autowired private BulkSubmissionRepository bulkSubmissionRepository;

  @Autowired private MatterStartRepository matterStartRepository;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String AUTHORIZATION_HEADER = "Authorization";

  // must match application-test.yml for test-runner token
  private static final String AUTHORIZATION_TOKEN = "f67f968e-b479-4e61-b66e-f57984931e56";

  public static final String POST_MATTER_START_URI = "/submissions/{submissionId}/matter-starts";

  public static final String GET_MATTER_STARTS_URI =
      "/submissions/{submissionId}/matter-starts/{msId}";

  private Submission submission;

  @BeforeAll
  void initialSetup() {
    OBJECT_MAPPER.registerModule(new JavaTimeModule());
  }

  @BeforeEach
  void setup() {
    matterStartRepository.deleteAll();
    submissionRepository.deleteAll();
    bulkSubmissionRepository.deleteAll();

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
  void close() {
    matterStartRepository.deleteAll();
    submissionRepository.deleteAll();
    bulkSubmissionRepository.deleteAll();
  }

  @Test
  void postMatterStart_shouldCreate() throws Exception {
    // given: a MatterStart Post payload
    MatterStartPost matterStartPost = MatterStartPost.builder().categoryCode("CAT1").build();

    // when: calling POST endpoint for matter starts
    mockMvc
        .perform(
            post(API_URI_PREFIX + POST_MATTER_START_URI, submission.getId())
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(matterStartPost)))
        .andExpect(status().isCreated())
        .andReturn();

    // then: matter starts is correctly created
    List<MatterStart> savedMatterStarts =
        matterStartRepository.findBySubmissionId(submission.getId());
    assertThat(savedMatterStarts.size()).isEqualTo(1);
    assertThat(savedMatterStarts.getFirst().getCategoryCode()).isEqualTo("CAT1");
  }

  @Test
  void postMatterStart_shouldReturnNotFound() throws Exception {
    // when: calling POST endpoint with invalid submission ID, should return Not Found
    MatterStartPost matterStartPost = MatterStartPost.builder().categoryCode("CAT1").build();
    mockMvc
        .perform(
            post(API_URI_PREFIX + POST_MATTER_START_URI, UUID.randomUUID())
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(matterStartPost)))
        .andExpect(status().isNotFound())
        .andReturn();
  }

  @Test
  void postMatterStart_shouldReturnBadRequest() throws Exception {
    // when: calling POST endpoint with invalid payload, should return Bad Request
    String invalidJson = "{ \"status\": \"INVALID_ENUM\" }";
    mockMvc
        .perform(
            post(API_URI_PREFIX + POST_MATTER_START_URI, submission.getId())
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
        .andExpect(status().isBadRequest())
        .andReturn();
  }

  @Test
  @Transactional
  void getMatterStart_shouldReturn200() throws Exception {
    MatterStart matterStart = new MatterStart();
    matterStart.setId(Uuid7.timeBasedUuid());
    matterStart.setSubmission(submissionRepository.findById(submission.getId()).orElseThrow());
    matterStart.setScheduleReference("REF1");
    matterStart.setCategoryCode("CAT1");
    matterStart.setProcurementAreaCode("AREA1");
    matterStart.setAccessPointCode("ACCESS1");
    matterStart.setDeliveryLocation("LONDON");
    matterStart.setCreatedByUserId("user1");
    matterStart.setCreatedOn(Instant.now());
    matterStart.setUpdatedOn(Instant.now());

    matterStartRepository.save(matterStart);
    MvcResult mvcResult =
        mockMvc
            .perform(
                get(API_URI_PREFIX + GET_MATTER_STARTS_URI, submission.getId(), matterStart.getId())
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    MatterStartGet result =
        OBJECT_MAPPER.readValue(mvcResult.getResponse().getContentAsString(), MatterStartGet.class);
    assertThat(result.getScheduleReference()).isEqualTo(matterStart.getScheduleReference());
    assertThat(result.getCategoryCode()).isEqualTo(matterStart.getCategoryCode());
    assertThat(result.getProcurementAreaCode()).isEqualTo(matterStart.getProcurementAreaCode());
    assertThat(result.getAccessPointCode()).isEqualTo(matterStart.getAccessPointCode());
    assertThat(result.getDeliveryLocation()).isEqualTo(matterStart.getDeliveryLocation());
  }

  @Test
  void getMatterStart_shouldReturnNotFound() throws Exception {
    mockMvc
        .perform(
            get(API_URI_PREFIX + GET_MATTER_STARTS_URI, submission.getId(), UUID.randomUUID())
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isNotFound())
        .andReturn();
  }
}
