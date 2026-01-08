package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.*;

import java.time.Instant;
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
    seedSubmissionsData();
  }

  @Test
  void getValidationMessages_shouldReturn200() throws Exception {
    // given: a submission with Validation Messages associated is created on DB
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
}
