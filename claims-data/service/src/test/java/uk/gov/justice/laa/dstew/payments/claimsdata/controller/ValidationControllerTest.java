package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageBase;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagesResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.ValidationMessageService;

@WebMvcTest(ValidationController.class)
@ImportAutoConfiguration(
    exclude = {
      org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
    })
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class ValidationControllerTest {

  private static final String VALIDATION_MESSAGES_URI = API_URI_PREFIX + "/validation-messages";

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ValidationMessageService validationMessageService;

  @Test
  @DisplayName("should return validation messages when submission id and claim id are provided")
  void getValidationMessages_returnsValidationMessagesWithClaimId() throws Exception {
    UUID submissionId = UUID.randomUUID();
    UUID claimId = UUID.randomUUID();
    UUID messageId = UUID.randomUUID();
    String type = "ERROR";
    String source = "SYSTEM";
    Pageable pageable = PageRequest.of(0, 10);

    ValidationMessageBase message =
        new ValidationMessageBase()
            .id(messageId)
            .type(type)
            .source(source)
            .displayMessage("A display message");

    ValidationMessagesResponse response =
        new ValidationMessagesResponse().totalElements(1).content(List.of(message));

    when(validationMessageService.getValidationErrors(
            submissionId, claimId, type, source, pageable))
        .thenReturn(response);

    String jsonContent = objectMapper.writeValueAsString(response);

    mockMvc
        .perform(
            get(VALIDATION_MESSAGES_URI)
                .queryParam("submission-id", submissionId.toString())
                .queryParam("claim-id", claimId.toString())
                .queryParam("type", type)
                .queryParam("source", source)
                .queryParam("page", "0")
                .queryParam("size", "10")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().json(jsonContent));
  }

  @Test
  @DisplayName("should return validation messages when only submission id is provided")
  void getValidationMessages_returnsValidationMessagesWithoutClaimId() throws Exception {
    UUID submissionId = UUID.randomUUID();
    UUID messageId = UUID.randomUUID();
    String type = "ERROR";
    String source = "SYSTEM";
    Pageable pageable = PageRequest.of(0, 5);

    ValidationMessageBase message =
        new ValidationMessageBase()
            .id(messageId)
            .type(type)
            .source(source)
            .displayMessage("Missing required field");

    ValidationMessagesResponse response =
        new ValidationMessagesResponse().totalElements(1).content(List.of(message));

    when(validationMessageService.getValidationErrors(submissionId, null, type, source, pageable))
        .thenReturn(response);

    String jsonContent = objectMapper.writeValueAsString(response);

    mockMvc
        .perform(
            get(VALIDATION_MESSAGES_URI)
                .queryParam("submission-id", submissionId.toString())
                .queryParam("type", type)
                .queryParam("source", source)
                .queryParam("page", "0")
                .queryParam("size", "5")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().json(jsonContent));
  }
}
