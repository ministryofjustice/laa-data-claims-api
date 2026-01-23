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
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageBase;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagesResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.ValidationMessageService;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@WebMvcTest(ValidationController.class)
@ImportAutoConfiguration(
    exclude = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc(addFilters = false)
class ValidationControllerTest {

  private static final String VALIDATION_MESSAGES_URI = API_URI_PREFIX + "/validation-messages";

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ValidationMessageService validationMessageService;

  @Test
  @DisplayName("should return validation messages when submission id and claim id are provided")
  void getValidationMessages_returnsValidationMessagesWithClaimId() throws Exception {
    UUID submissionId = Uuid7.timeBasedUuid();
    UUID claimId = Uuid7.timeBasedUuid();
    UUID messageId = Uuid7.timeBasedUuid();
    String type = "ERROR";
    String source = "SYSTEM";
    Pageable pageable = PageRequest.of(0, 10);

    ValidationMessageBase message =
        new ValidationMessageBase()
            .id(messageId)
            .type(ValidationMessageType.ERROR)
            .source(source)
            .displayMessage("A display message");

    ValidationMessagesResponse response =
        new ValidationMessagesResponse().totalElements(1).content(List.of(message));

    when(validationMessageService.getValidationErrors(
            submissionId, claimId, ValidationMessageType.ERROR, source, pageable))
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
    UUID submissionId = Uuid7.timeBasedUuid();
    UUID messageId = Uuid7.timeBasedUuid();
    String type = "ERROR";
    String source = "SYSTEM";
    Pageable pageable = PageRequest.of(0, 5);

    ValidationMessageBase message =
        new ValidationMessageBase()
            .id(messageId)
            .type(ValidationMessageType.ERROR)
            .source(source)
            .displayMessage("Missing required field");

    ValidationMessagesResponse response =
        new ValidationMessagesResponse().totalElements(1).content(List.of(message));

    when(validationMessageService.getValidationErrors(
            submissionId, null, ValidationMessageType.ERROR, source, pageable))
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
