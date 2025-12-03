package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.HttpHeaders.AUTHORIZATION;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentOutcome.REDUCED_TO_FIXED_FEE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.ASSESSMENT_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.AssertionsForClassTypes;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.AssessmentNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimSummaryFeeNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentGet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.AssessmentService;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@WebMvcTest(AssessmentController.class)
@ImportAutoConfiguration(
    exclude = {
      org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
    })
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class AssessmentControllerTest {

  private static final String CLAIMS_URI = API_URI_PREFIX + "/claims";
  private static final String GET_ASSESSMENT_URI = "/claims/{claimId}/assessments/{assessmentId}";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AssessmentService assessmentService;

  @Test
  void createAssessment_returnsCreatedStatusAndLocationHeader() throws Exception {
    final UUID claimId = Uuid7.timeBasedUuid();
    final UUID assessmentId = Uuid7.timeBasedUuid();
    when(assessmentService.createAssessment(eq(claimId), any(AssessmentPost.class)))
        .thenReturn(assessmentId);

    final String body =
        """
        {
          "claim_id": "c2ecc377-3223-49c3-999b-08ea461bbd1e",
          "claim_summary_fee_id": "8dae05c8-1ebd-464d-8a37-8548fd051527",
          "assessment_outcome": "NILLED",
          "created_by_user_id": "test-user",
          "allowed_total_vat": "1",
          "allowed_total_incl_vat": "1"
        }
        """;

    mockMvc
        .perform(
            post(CLAIMS_URI + "/{claimId}/assessments", claimId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(
            header()
                .string(
                    "Location",
                    containsString(CLAIMS_URI + "/" + claimId + "/assessments/" + assessmentId)))
        .andExpect(jsonPath("$.id").value(assessmentId.toString()));

    verify(assessmentService).createAssessment(eq(claimId), any(AssessmentPost.class));
  }

  @Test
  void createAssessment_whenClaimDoesNotExist_returnsNotFoundStatus() throws Exception {
    final UUID claimId = Uuid7.timeBasedUuid();
    when(assessmentService.createAssessment(eq(claimId), any(AssessmentPost.class)))
        .thenThrow(new ClaimNotFoundException(""));

    final String body =
        """
        {
          "claim_id": "c2ecc377-3223-49c3-999b-08ea461bbd1e",
          "claim_summary_fee_id": "8dae05c8-1ebd-464d-8a37-8548fd051527",
          "assessment_outcome": "NILLED",
          "created_by_user_id": "test-user",
          "allowed_total_vat": "1",
          "allowed_total_incl_vat": "1"
        }
        """;

    mockMvc
        .perform(
            post(CLAIMS_URI + "/{claimId}/assessments", claimId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound());

    verify(assessmentService).createAssessment(eq(claimId), any(AssessmentPost.class));
  }

  @Test
  void createAssessment_whenClaimSummaryFeeDoesNotExist_returnsNotFoundStatus() throws Exception {
    final UUID claimId = Uuid7.timeBasedUuid();
    when(assessmentService.createAssessment(eq(claimId), any(AssessmentPost.class)))
        .thenThrow(new ClaimSummaryFeeNotFoundException(""));

    final String body =
        """
        {
          "claim_id": "c2ecc377-3223-49c3-999b-08ea461bbd1e",
          "claim_summary_fee_id": "8dae05c8-1ebd-464d-8a37-8548fd051527",
          "assessment_outcome": "NILLED",
          "created_by_user_id": "test-user",
          "allowed_total_vat": "1",
          "allowed_total_incl_vat": "1"
        }
        """;

    mockMvc
        .perform(
            post(CLAIMS_URI + "/{claimId}/assessments", claimId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound());

    verify(assessmentService).createAssessment(eq(claimId), any(AssessmentPost.class));
  }

  @Nested
  @DisplayName("Get assessments")
  class GetAssessment {

    @BeforeEach
    public void init() {
      OBJECT_MAPPER.registerModule(new JavaTimeModule());
      OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
    }

    private static @NotNull AssessmentGet createAssessmentGet() {
      return AssessmentGet.builder()
          .claimId(CLAIM_1_ID)
          .assessmentOutcome(REDUCED_TO_FIXED_FEE)
          .disbursementAmount(BigDecimal.valueOf(44.55))
          .jrFormFillingAmount(BigDecimal.valueOf(44.55))
          .createdByUserId("test-user")
          .build();
    }

    @DisplayName("Status 200: when a valid Claim ID & Assessment ID is provided")
    @Test
    void getAssessmentShouldReturnSuccess() throws Exception {
      AssessmentGet mockAssessment = createAssessmentGet();

      Mockito.when(assessmentService.getAssessment(any(), any())).thenReturn(mockAssessment);

      MvcResult mvcResult =
          mockMvc
              .perform(
                  get(API_URI_PREFIX + GET_ASSESSMENT_URI, CLAIM_1_ID, ASSESSMENT_1_ID)
                      .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
              .andExpect(status().isOk())
              .andReturn();

      AssessmentGet result =
          OBJECT_MAPPER.readValue(
              mvcResult.getResponse().getContentAsString(), AssessmentGet.class);
      AssertionsForClassTypes.assertThat(result.getClaimId()).isEqualTo(CLAIM_1_ID);
      AssertionsForClassTypes.assertThat(result.getAssessmentOutcome())
          .isEqualTo(REDUCED_TO_FIXED_FEE);
      AssertionsForClassTypes.assertThat(result.getDisbursementAmount())
          .isEqualTo(new BigDecimal("44.55"));
      AssertionsForClassTypes.assertThat(result.getJrFormFillingAmount())
          .isEqualTo(new BigDecimal("44.55"));
      AssertionsForClassTypes.assertThat(result.getCreatedByUserId()).isEqualTo("test-user");
    }

    @DisplayName("Should return 500 when any internal error")
    @Test
    void getAssessment_shouldReturnServerError() throws Exception {
      Mockito.when(assessmentService.getAssessment(any(), any()))
          .thenThrow(new IllegalArgumentException("Error retrieving assessment"));

      var result =
          mockMvc
              .perform(
                  get(API_URI_PREFIX + GET_ASSESSMENT_URI, CLAIM_1_ID, ASSESSMENT_1_ID)
                      .header(AUTHORIZATION, AUTHORIZATION_TOKEN))
              .andExpect(status().isInternalServerError())
              .andReturn();
      AssertionsForClassTypes.assertThat(result.getResponse().getStatus()).isEqualTo(500);
    }

    @Test
    void shouldReturnAssessmentsForValidClaimId() throws Exception {
      UUID claimId = UUID.randomUUID();

      AssessmentResultSet resultSet = new AssessmentResultSet();
      resultSet.assessments(List.of(createAssessmentGet()));

      when(assessmentService.getAssessmentsByClaimId(claimId)).thenReturn(resultSet);

      mockMvc
          .perform(get("/api/v0/claims/{claimId}/assessments", claimId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.assessments").isArray())
          .andExpect(jsonPath("$.assessments[0]").exists());
    }

    @Test
    void shouldReturnNotFoundWhenNoAssessmentsExist() throws Exception {
      UUID claimId = UUID.randomUUID();

      when(assessmentService.getAssessmentsByClaimId(claimId))
          .thenThrow(new AssessmentNotFoundException("No assessments found"));

      mockMvc
          .perform(get("/api/v0/claims/{claimId}/assessments", claimId))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.message").value("No assessments found"));
    }

    @Test
    void shouldReturnBadRequestForInvalidClaimId() throws Exception {
      mockMvc
          .perform(get("/api/v0/claims/{claimId}/assessments", "invalid-uuid"))
          .andExpect(status().isBadRequest());
    }
  }
}
