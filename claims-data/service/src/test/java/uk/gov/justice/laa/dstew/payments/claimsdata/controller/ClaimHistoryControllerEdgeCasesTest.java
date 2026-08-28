package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.JacksonMappingConfig;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ClaimHistoryEventRow;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ClaimHistoryPage;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.ClaimHistoryService;

@WebMvcTest(ClaimHistoryController.class)
@ImportAutoConfiguration(
    exclude = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@Import(JacksonMappingConfig.class)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc(addFilters = false)
@ExtendWith(MockitoExtension.class)
@DisplayName("ClaimHistoryController edge-case tests")
class ClaimHistoryControllerEdgeCasesTest {

  private static final String HISTORY_URI = "/api/v1/claims/{claimId}/history";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private ClaimHistoryService claimHistoryService;

  @Test
  @DisplayName("metadata null in row results in empty metadata map in response")
  void metadataNullResultsInEmptyMap() throws Exception {
    UUID claimId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();

    // Create a row with null metadata and a valid timestamp
    ClaimHistoryEventRow row =
        new ClaimHistoryEventRow(
            "SUBMISSION", Instant.parse("2026-04-22T11:26:00Z"), "SYSTEM", sourceId, null, 1L);

    when(claimHistoryService.getTimeline(eq(claimId), org.mockito.ArgumentMatchers.any()))
        .thenReturn(new ClaimHistoryPage(List.of(row), 1L, 0, 20));

    mockMvc
        .perform(get(HISTORY_URI, claimId))
        .andExpect(status().isOk())
        // metadata object must not expose submission_period key (empty map)
        .andExpect(jsonPath("$.events[0].metadata.submission_period").doesNotExist());
  }

  @Test
  @DisplayName("event timestamp null is preserved and returned as null")
  void eventTimestampNullIsPreserved() {
    UUID claimId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();

    ClaimHistoryEventRow row =
        new ClaimHistoryEventRow("SUBMISSION", null, "SYSTEM", sourceId, null, 1L);

    ClaimHistoryController controller =
        new ClaimHistoryController(claimHistoryService, objectMapper);
    when(claimHistoryService.getTimeline(eq(claimId), isNull()))
        .thenReturn(new ClaimHistoryPage(List.of(row), 1L, 0, 20));

    var response =
        controller.getClaimHistory(claimId, (org.springframework.data.domain.Pageable) null);
    var body = response.getBody();
    org.assertj.core.api.Assertions.assertThat(body).isNotNull();
    org.assertj.core.api.Assertions.assertThat(body.getEvents()).hasSize(1);
    org.assertj.core.api.Assertions.assertThat(body.getEvents().get(0).getEventTimestamp())
        .isNull();
  }
}
