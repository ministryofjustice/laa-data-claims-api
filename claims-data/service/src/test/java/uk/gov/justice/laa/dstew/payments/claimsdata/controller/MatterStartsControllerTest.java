package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CategoryCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MatterStartGet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MatterStartPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MatterStartResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.MatterStartService;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@WebMvcTest(MatterStartsController.class)
@ImportAutoConfiguration(
    exclude = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc(addFilters = false)
class MatterStartsControllerTest {

  private static final String GET_ALL_MATTERS_URI =
      API_URI_PREFIX + "/submissions/{id}/matter-starts";
  private static final String GET_MATTER_STARTS_URI = GET_ALL_MATTERS_URI + "/{matter-start-id}";

  @Autowired private MockMvcTester mockMvc;

  @Autowired private MockMvc mockMvcTest;

  @MockitoBean private MatterStartService matterStartService;

  @Nested
  @DisplayName("POST: /api/v1/submissions/{id}/matter-starts tests")
  class CreateMatterStartTests {

    @Test
    void createMatterStart_returnsCreatedStatusAndLocationHeader() throws Exception {
      final UUID submissionId = Uuid7.timeBasedUuid();
      final UUID matterStartId = Uuid7.timeBasedUuid();
      when(matterStartService.createMatterStart(eq(submissionId), any(MatterStartPost.class)))
          .thenReturn(matterStartId);

      final String body =
          """
          {
            "schedule_reference": "SCH-123",
            "category_code": "HOU",
            "procurement_area_code": "PAC-1",
            "access_point_code": "AP-9",
            "delivery_location": "LOC-77",
            "number_of_matter_starts": 2
          }""";

      mockMvcTest
          .perform(
              post(GET_ALL_MATTERS_URI, submissionId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(MockMvcResultMatchers.status().isCreated())
          .andExpect(
              MockMvcResultMatchers.header()
                  .string(
                      "Location",
                      "http://localhost"
                          + API_URI_PREFIX
                          + "/submissions/"
                          + submissionId
                          + "/matter-starts/"
                          + matterStartId))
          .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(matterStartId.toString()));

      verify(matterStartService).createMatterStart(eq(submissionId), any(MatterStartPost.class));
    }
  }

  @Nested
  @DisplayName("GET: /api/v1/submissions/{id}/matter-starts/{matter-start-id} tests")
  class GetMatterStarts {

    @Test
    @DisplayName("Should return 200 response")
    void shouldReturn200() throws JsonProcessingException {
      // Given
      UUID id = Uuid7.timeBasedUuid();
      UUID submissionId = Uuid7.timeBasedUuid();
      Optional<MatterStartGet> expected =
          Optional.of(new MatterStartGet().categoryCode(CategoryCode.AAP));
      when(matterStartService.getMatterStart(submissionId, id)).thenReturn(expected);
      // When
      ObjectMapper mapper = new ObjectMapper();
      assertThat(mockMvc.get().uri(GET_MATTER_STARTS_URI, submissionId, id))
          .hasStatusOk()
          .bodyJson()
          .isLenientlyEqualTo(mapper.writeValueAsString(expected.get()));
    }
  }

  @Nested
  class GetAllMatterStartsForSubmission {

    @DisplayName("Should call service once and return list of matter start")
    @Test
    void shouldCallServiceOnceAndReturnListOfMatterStart() throws Exception {
      var submissionId = Uuid7.timeBasedUuid();
      MatterStartGet matterStartGet = new MatterStartGet().categoryCode(CategoryCode.AAP);
      MatterStartResultSet matterStartResultSet =
          MatterStartResultSet.builder()
              .submissionId(submissionId)
              .matterStarts(List.of(matterStartGet))
              .build();
      when(matterStartService.getAllMatterStartsForSubmission(submissionId))
          .thenReturn(matterStartResultSet);

      ObjectMapper mapper = new ObjectMapper();
      var actualResult =
          mockMvcTest
              .perform(get(GET_ALL_MATTERS_URI, submissionId))
              .andExpect(MockMvcResultMatchers.status().isOk())
              .andReturn();
      JsonNode expectedJson = mapper.readTree(mapper.writeValueAsString(matterStartResultSet));
      JsonNode actualJson = mapper.readTree(actualResult.getResponse().getContentAsString());
      assertThat(actualJson).isEqualTo(expectedJson);
      verify(matterStartService).getAllMatterStartsForSubmission(eq(submissionId));
    }

    @DisplayName("Should return 404 when submission not found")
    @Test
    void shouldReturn404WhenSubmissionNotFound() throws Exception {
      var submissionId = Uuid7.timeBasedUuid();
      when(matterStartService.getAllMatterStartsForSubmission(submissionId))
          .thenThrow(new SubmissionNotFoundException("No entity found with id:" + submissionId));

      mockMvcTest
          .perform(get(GET_ALL_MATTERS_URI, submissionId))
          .andExpect(MockMvcResultMatchers.status().isNotFound())
          .andExpect(
              result ->
                  assertInstanceOf(
                      SubmissionNotFoundException.class, result.getResolvedException()))
          .andExpect(
              result ->
                  assertThat(Objects.requireNonNull(result.getResolvedException()).getMessage())
                      .isEqualTo("No entity found with id:" + submissionId))
          .andReturn();
    }
  }
}
