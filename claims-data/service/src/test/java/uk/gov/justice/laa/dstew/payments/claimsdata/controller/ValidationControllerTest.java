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
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationErrorFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationErrorsResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.ValidationErrorService;

@WebMvcTest(ValidationController.class)
@ImportAutoConfiguration(
    exclude = {
      org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
    })
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class ValidationControllerTest {

  private static final String VALIDATION_ERRORS_URI = API_URI_PREFIX + "/validation-errors";

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ValidationErrorService validationErrorService;

  @Test
  @DisplayName("should return validation errors when submission id and claim id are provided")
  void getValidationErrors_returnsValidationErrorsWithClaimId() throws Exception {
    UUID submissionId = UUID.randomUUID();
    UUID claimId = UUID.randomUUID();
    UUID errorId = UUID.randomUUID();
    Pageable pageable = PageRequest.of(0, 10);

    ValidationErrorFields error =
        new ValidationErrorFields().id(errorId).errorDescription("Invalid data format");

    ValidationErrorsResponse response =
        new ValidationErrorsResponse().totalElements(1).content(List.of(error));

    when(validationErrorService.getValidationErrors(submissionId, claimId, pageable))
        .thenReturn(response);

    String jsonContent = objectMapper.writeValueAsString(response);

    mockMvc
        .perform(
            get(VALIDATION_ERRORS_URI)
                .queryParam("submission-id", submissionId.toString())
                .queryParam("claim-id", claimId.toString())
                .queryParam("page", "0")
                .queryParam("size", "10")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().json(jsonContent));
  }

  @Test
  @DisplayName("should return validation errors when only submission id is provided")
  void getValidationErrors_returnsValidationErrorsWithoutClaimId() throws Exception {
    UUID submissionId = UUID.randomUUID();
    UUID errorId = UUID.randomUUID();
    Pageable pageable = PageRequest.of(0, 5);

    ValidationErrorFields error =
        new ValidationErrorFields().id(errorId).errorDescription("Missing required field");

    ValidationErrorsResponse response =
        new ValidationErrorsResponse().totalElements(1).content(List.of(error));

    when(validationErrorService.getValidationErrors(submissionId, null, pageable))
        .thenReturn(response);

    String jsonContent = objectMapper.writeValueAsString(response);

    mockMvc
        .perform(
            get(VALIDATION_ERRORS_URI)
                .queryParam("submission-id", submissionId.toString())
                .queryParam("page", "0")
                .queryParam("size", "5")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().json(jsonContent));
  }
}
