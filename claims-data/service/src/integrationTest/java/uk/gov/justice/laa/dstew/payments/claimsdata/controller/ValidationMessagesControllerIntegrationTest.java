package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ValidationMessagesControllerIntegrationTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @BeforeEach()
  void setup() {
    seedClaimsData();
  }

  @Test
  void getValidationMessages_shouldReturn200() throws Exception {
    // given: a validation message not linked to any claim (no client details expected)
    ValidationMessageLog log = new ValidationMessageLog();
    log.setId(Uuid7.timeBasedUuid());
    log.setSubmissionId(submission1.getId());
    log.setType(ValidationMessageType.ERROR);
    log.setSource("SOURCE1");
    log.setDisplayMessage("MESSAGE1");
    log.setCreatedOn(Instant.now());
    validationMessageLogRepository.save(log);

    // when: calling GET endpoint with a valid submission id
    MvcResult mvcResult =
        mockMvc
            .perform(
                get(API_URI_PREFIX + "/validation-messages")
                    .param("submission-id", submission1.getId().toString())
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    // then: the response data matches the values on the DB validation message
    ValidationMessagesResponse response =
        OBJECT_MAPPER.readValue(
            mvcResult.getResponse().getContentAsString(), ValidationMessagesResponse.class);
    assertThat(response.getTotalElements()).isEqualTo(1);
    ValidationMessageBase msg = response.getContent().getFirst();
    assertThat(msg.getId()).isEqualTo(log.getId());
    assertThat(msg.getType()).isEqualTo(ValidationMessageType.ERROR);
    assertThat(msg.getSource()).isEqualTo("SOURCE1");
    assertThat(msg.getDisplayMessage()).isEqualTo("MESSAGE1");
    validationMessageLogRepository.deleteAll();
  }

  @Test
  @DisplayName(
      "getValidationMessages returns client forename, surname, UCN and UFN from the linked claim")
  void getValidationMessages_shouldReturnClaimDetails() throws Exception {
    // given: a validation message linked to CLAIM_1_ID which has a Client record seeded
    // with clientForename="Alice", clientSurname="Smith",
    // uniqueClientNumber=SEEDED_UNIQUE_CLIENT_NUMBER ("01011990/A/BCDE")
    // and the claim itself has uniqueFileNumber=UNIQUE_FILE_NUMBER ("010125/001")
    ValidationMessageLog log = new ValidationMessageLog();
    log.setId(Uuid7.timeBasedUuid());
    log.setSubmissionId(submission1.getId());
    log.setClaimId(CLAIM_1_ID);
    log.setType(ValidationMessageType.ERROR);
    log.setSource("SYSTEM");
    log.setDisplayMessage("Missing case reference");
    log.setCreatedOn(Instant.now());
    validationMessageLogRepository.save(log);

    // when: calling GET endpoint filtered by claimId
    MvcResult mvcResult =
        mockMvc
            .perform(
                get(API_URI_PREFIX + "/validation-messages")
                    .param("submission-id", submission1.getId().toString())
                    .param("claim-id", CLAIM_1_ID.toString())
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    // then: client/claim detail fields are populated from the claim and client tables
    ValidationMessagesResponse response =
        OBJECT_MAPPER.readValue(
            mvcResult.getResponse().getContentAsString(), ValidationMessagesResponse.class);
    assertThat(response.getTotalElements()).isEqualTo(1);
    ValidationMessageBase msg = response.getContent().getFirst();
    assertThat(msg.getId()).isEqualTo(log.getId());
    assertThat(msg.getUniqueFileNumber()).isEqualTo(UNIQUE_FILE_NUMBER);
    assertThat(msg.getClientForename()).isEqualTo("Alice");
    assertThat(msg.getClientSurname()).isEqualTo("Smith");
    assertThat(msg.getUniqueClientNumber()).isEqualTo(SEEDED_UNIQUE_CLIENT_NUMBER);
    validationMessageLogRepository.deleteAll();
  }

  @Test
  @DisplayName(
      "getValidationMessages returns message_code for FSP-sourced messages when present in response")
  void getValidationMessages_shouldReturnMessageCodeForFspMessages() throws Exception {
    // given: an FSP-sourced ERROR validation message with a messageCode
    ValidationMessageLog fspLog = new ValidationMessageLog();
    fspLog.setId(Uuid7.timeBasedUuid());
    fspLog.setSubmissionId(submission1.getId());
    fspLog.setClaimId(CLAIM_1_ID);
    fspLog.setType(ValidationMessageType.ERROR);
    fspLog.setSource("FSP");
    fspLog.setDisplayMessage("Enter a valid Fee Code.");
    fspLog.setTechnicalMessage("Fee Code is invalid");
    fspLog.setMessageCode("ERRALL1");
    fspLog.setCreatedOn(Instant.now());
    validationMessageLogRepository.save(fspLog);

    // when: calling GET /api/v1/validation-messages filtered by submissionId and claimId
    MvcResult mvcResult =
        mockMvc
            .perform(
                get(API_URI_PREFIX + "/validation-messages")
                    .param("submission-id", submission1.getId().toString())
                    .param("claim-id", CLAIM_1_ID.toString())
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    // then: message_code is present in the response item
    ValidationMessagesResponse response =
        OBJECT_MAPPER.readValue(
            mvcResult.getResponse().getContentAsString(), ValidationMessagesResponse.class);
    assertThat(response.getTotalElements()).isEqualTo(1);
    ValidationMessageBase msg = response.getContent().getFirst();
    assertThat(msg.getId()).isEqualTo(fspLog.getId());
    assertThat(msg.getSource()).isEqualTo("FSP");
    assertThat(msg.getType()).isEqualTo(ValidationMessageType.ERROR);
    assertThat(msg.getMessageCode()).isEqualTo("ERRALL1");
    validationMessageLogRepository.deleteAll();
  }

  @Test
  @DisplayName("getValidationMessages returns null message_code for non-FSP messages in response")
  void getValidationMessages_shouldReturnNullMessageCodeForNonFspMessages() throws Exception {
    // given: a SYSTEM-sourced validation message (no messageCode)
    ValidationMessageLog systemLog = new ValidationMessageLog();
    systemLog.setId(Uuid7.timeBasedUuid());
    systemLog.setSubmissionId(submission1.getId());
    systemLog.setClaimId(CLAIM_1_ID);
    systemLog.setType(ValidationMessageType.ERROR);
    systemLog.setSource("SYSTEM");
    systemLog.setDisplayMessage("Missing case reference");
    systemLog.setCreatedOn(Instant.now());
    validationMessageLogRepository.save(systemLog);

    // when: calling GET /api/v1/validation-messages
    MvcResult mvcResult =
        mockMvc
            .perform(
                get(API_URI_PREFIX + "/validation-messages")
                    .param("submission-id", submission1.getId().toString())
                    .param("claim-id", CLAIM_1_ID.toString())
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    // then: message_code is null for non-FSP messages
    ValidationMessagesResponse response =
        OBJECT_MAPPER.readValue(
            mvcResult.getResponse().getContentAsString(), ValidationMessagesResponse.class);
    assertThat(response.getTotalElements()).isEqualTo(1);
    ValidationMessageBase msg = response.getContent().getFirst();
    assertThat(msg.getSource()).isEqualTo("SYSTEM");
    assertThat(msg.getMessageCode()).isNull();
    validationMessageLogRepository.deleteAll();
  }

  @Test
  @DisplayName("GET v1/validation-messages - page size limits results to requested size")
  void pageSizeLimitsResultsV1() throws Exception {
    // ensure a clean store for deterministic assertions
    validationMessageLogRepository.deleteAll();

    var fixtures = saveManyValidationMessages(submission1.getId(), 25);

    MvcResult result =
        mockMvc
            .perform(
                get(API_URI_PREFIX + "/validation-messages")
                    .param("submission-id", submission1.getId().toString())
                    .param("size", "10")
                    .param("page", "0")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    ValidationMessagesResponse response =
        OBJECT_MAPPER.readValue(
            result.getResponse().getContentAsString(), ValidationMessagesResponse.class);

    assertThat(response.getTotalElements()).isEqualTo(fixtures.size());
    assertThat(response.getSize()).isEqualTo(10);
    assertThat(response.getContent()).hasSize(10);

    validationMessageLogRepository.deleteAll(fixtures);
  }

  @Test
  @DisplayName("GET v1/validation-messages - page offset returns correct subset (items 11-20)")
  void pageOffsetReturnsCorrectSubsetV1() throws Exception {
    validationMessageLogRepository.deleteAll();
    var fixtures = saveManyValidationMessages(submission1.getId(), 25);

    MvcResult page0 =
        mockMvc
            .perform(
                get(API_URI_PREFIX + "/validation-messages")
                    .param("submission-id", submission1.getId().toString())
                    .param("page", "0")
                    .param("size", "10")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    MvcResult page1 =
        mockMvc
            .perform(
                get(API_URI_PREFIX + "/validation-messages")
                    .param("submission-id", submission1.getId().toString())
                    .param("page", "1")
                    .param("size", "10")
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    ValidationMessagesResponse r0 =
        OBJECT_MAPPER.readValue(
            page0.getResponse().getContentAsString(), ValidationMessagesResponse.class);
    ValidationMessagesResponse r1 =
        OBJECT_MAPPER.readValue(
            page1.getResponse().getContentAsString(), ValidationMessagesResponse.class);

    assertThat(r0.getContent()).hasSize(10);
    assertThat(r1.getContent()).hasSize(10);
    // ensure there's no overlap between pages
    var ids0 = r0.getContent().stream().map(ValidationMessageBase::getId).toList();
    var ids1 = r1.getContent().stream().map(ValidationMessageBase::getId).toList();
    assertThat(ids0).doesNotContainAnyElementsOf(ids1);

    validationMessageLogRepository.deleteAll(fixtures);
  }

  @Test
  @DisplayName("No pageable parameters: defaults to page 0 and size 20")
  void noPageableParametersDefaultsToPageZeroAndSize20ValidationMessages() throws Exception {
    validationMessageLogRepository.deleteAll();
    var fixtures = saveManyValidationMessages(submission1.getId(), 25);

    org.springframework.test.web.servlet.MvcResult result =
        mockMvc
            .perform(
                get(API_URI_PREFIX + "/validation-messages")
                    .param("submission-id", submission1.getId().toString())
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagesResponse response =
        OBJECT_MAPPER.readValue(
            result.getResponse().getContentAsString(),
            uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagesResponse.class);

    // metadata defaults
    assertThat(response.getNumber()).isEqualTo(0);
    assertThat(response.getSize()).isEqualTo(20);
    assertThat(response.getTotalElements()).isEqualTo(fixtures.size());

    // content truncated to default page size
    assertThat(response.getContent()).hasSize(20);

    // deterministic ordering: default ordering is by id (UUIDv7 ascending) when no sort param
    // provided
    var expectedIds =
        fixtures.stream()
            .map(ValidationMessageLog::getId)
            .sorted() // UUID natural order; Uuid7 is time-based so this is monotonic
            .limit(20)
            .toList();
    var returnedIds =
        response.getContent().stream()
            .map(uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageBase::getId)
            .toList();

    // Expect the page to contain the first 20 ids by id ascending
    assertThat(returnedIds).containsExactlyElementsOf(expectedIds);

    validationMessageLogRepository.deleteAll(fixtures);
  }

  private List<ValidationMessageLog> saveManyValidationMessages(UUID submissionId, int count) {
    List<ValidationMessageLog> list = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      ValidationMessageLog log = new ValidationMessageLog();
      log.setId(Uuid7.timeBasedUuid());
      log.setSubmissionId(submissionId);
      log.setType(ValidationMessageType.ERROR);
      log.setSource("SRC" + i);
      log.setDisplayMessage("MSG" + i);
      log.setCreatedOn(java.time.Instant.now().plusSeconds(i));
      list.add(log);
    }
    validationMessageLogRepository.saveAll(list);
    return list;
  }
}
