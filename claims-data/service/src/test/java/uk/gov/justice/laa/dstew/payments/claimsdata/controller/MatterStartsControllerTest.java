package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MatterStartGet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MatterStartPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.MatterStartService;

@WebMvcTest(MatterStartsController.class)
@ImportAutoConfiguration(exclude = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class})
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class MatterStartsControllerTest {

  @Autowired
  private MockMvcTester mockMvc;

  @MockitoBean
  private MatterStartService matterStartService;

  @Nested
  @DisplayName("POST: /api/v0/submissions/{id}/matter-starts tests")
  class CreateMatterStartTests {

    @Test
    void createMatterStart_returnsCreatedStatusAndLocationHeader() throws Exception {
      final UUID submissionId = UUID.randomUUID();
      final UUID matterStartId = UUID.randomUUID();
      when(matterStartService.createMatterStart(eq(submissionId), any(MatterStartPost.class)))
          .thenReturn(matterStartId);

      final String body = """
          {
            "schedule_reference": "SCH-123",
            "category_code": "CAT-01",
            "procurement_area_code": "PAC-1",
            "access_point_code": "AP-9",
            "delivery_location": "LOC-77"
          }""";

      assertThat(
          mockMvc.perform(post(API_URI_PREFIX + "/submissions/{id}/matter-starts", submissionId)
              .contentType(MediaType.APPLICATION_JSON)
              .content(body)))
          .hasStatus(HttpStatus.CREATED)
          .hasHeader(
              "Location", "http://localhost" + API_URI_PREFIX + "/submissions/" + submissionId
                  + "/matter-starts/"
                  + matterStartId)
          .bodyJson()
          .hasPathSatisfying("$.id", id -> assertThat(id)
              .isEqualTo(matterStartId.toString()));

      verify(matterStartService).createMatterStart(eq(submissionId), any(MatterStartPost.class));
    }

  }

  @Nested
  @DisplayName("GET: /api/v0/submissions/{id}/matter-starts/{matter-starts-id} tests")
  class GetMatterStarts {

    @Test
    @DisplayName("Should return 200 response")
    void shouldReturn200() throws JsonProcessingException {
      // Given
      UUID id = UUID.randomUUID();
      UUID submissionId = UUID.randomUUID();
      Optional<MatterStartGet> expected =
          Optional.of(new MatterStartGet().categoryCode("Category"));
      when(matterStartService.getMatterStart(submissionId, id)).thenReturn(
          expected);
      // When
      ObjectMapper mapper = new ObjectMapper();
      assertThat(mockMvc.perform(get(API_URI_PREFIX + "/submissions/{id}/matter-starts/{matter-starts-id}",
          submissionId, id)))
          .hasStatusOk()
          .bodyJson()
          .isLenientlyEqualTo(mapper.writeValueAsString(expected.get()));

    }
  }
}
