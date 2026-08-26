package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.JacksonMappingConfig;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimHistoryResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ClaimHistoryEventRow;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ClaimHistoryPage;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.ClaimHistoryService;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@WebMvcTest(ClaimHistoryController.class)
@ImportAutoConfiguration(
    exclude = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@Import(JacksonMappingConfig.class)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc(addFilters = false)
class ClaimHistoryControllerTest {

  private static final String HISTORY_URI = "/api/v1/claims/{claimId}/history";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private ClaimHistoryService claimHistoryService;

  private static ClaimHistoryEventRow submissionRow(UUID sourceId, String actorId) {
    ObjectNode metadata = JsonNodeFactory.instance.objectNode();
    metadata.put("submission_period", "APR-2026");
    metadata.put("office_account_number", "0X123Y");
    metadata.put("area_of_law", "CRIME LOWER");
    return new ClaimHistoryEventRow(
        "SUBMISSION", Instant.parse("2026-04-22T11:26:00Z"), actorId, sourceId, metadata, 1L);
  }

  @Test
  @DisplayName("Returns timeline with envelope and metadata")
  void returnsTimelineWithEnvelopeAndMetadata() throws Exception {
    UUID claimId = Uuid7.timeBasedUuid();
    UUID sourceId = Uuid7.timeBasedUuid();
    when(claimHistoryService.getTimeline(eq(claimId), ArgumentMatchers.any()))
        .thenReturn(
            new ClaimHistoryPage(List.of(submissionRow(sourceId, "provider-user-id")), 1L, 0, 20));

    mockMvc
        .perform(get(HISTORY_URI, claimId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.claim_id").value(claimId.toString()))
        .andExpect(jsonPath("$.events[0].event_type").value("SUBMISSION"))
        .andExpect(jsonPath("$.events[0].actor_id").value("provider-user-id"))
        .andExpect(jsonPath("$.events[0].source_id").value(sourceId.toString()))
        .andExpect(jsonPath("$.events[0].event_timestamp").exists())
        .andExpect(jsonPath("$.events[0].metadata.submission_period").value("APR-2026"))
        .andExpect(jsonPath("$.events[0].metadata.office_account_number").value("0X123Y"))
        .andExpect(jsonPath("$.events[0].metadata.area_of_law").value("CRIME LOWER"));

    verify(claimHistoryService).getTimeline(eq(claimId), ArgumentMatchers.any());
  }

  @Test
  @DisplayName("Uses pageable size when size query param supplied")
  void usesLimitOverloadWhenLimitProvided() throws Exception {
    UUID claimId = Uuid7.timeBasedUuid();
    when(claimHistoryService.getTimeline(eq(claimId), ArgumentMatchers.any()))
        .thenReturn(new ClaimHistoryPage(List.of(), 0L, 0, 10));

    mockMvc
        .perform(get(HISTORY_URI, claimId).param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.events").isArray())
        .andExpect(jsonPath("$.events").isEmpty());

    verify(claimHistoryService).getTimeline(eq(claimId), ArgumentMatchers.any());
  }

  @Test
  @DisplayName("Passes page and size to service")
  void passesPageAndSizeToService() throws Exception {
    UUID claimId = Uuid7.timeBasedUuid();
    when(claimHistoryService.getTimeline(eq(claimId), ArgumentMatchers.any()))
        .thenReturn(new ClaimHistoryPage(List.of(), 0L, 2, 10));

    mockMvc
        .perform(get(HISTORY_URI, claimId).param("page", "2").param("size", "10"))
        .andExpect(status().isOk());

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(claimHistoryService).getTimeline(eq(claimId), captor.capture());
    Pageable pageable = captor.getValue();
    Assertions.assertThat(pageable.getPageNumber()).isEqualTo(2);
    Assertions.assertThat(pageable.getPageSize()).isEqualTo(10);
  }

  @Test
  @DisplayName("Populates actor fallback from service value")
  void populatesActorFallbackFromServiceValue() throws Exception {
    UUID claimId = Uuid7.timeBasedUuid();
    when(claimHistoryService.getTimeline(eq(claimId), ArgumentMatchers.any()))
        .thenReturn(
            new ClaimHistoryPage(
                List.of(submissionRow(Uuid7.timeBasedUuid(), "SYSTEM")), 1L, 0, 20));

    mockMvc
        .perform(get(HISTORY_URI, claimId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.events[0].actor_id").value("SYSTEM"));
  }

  @Test
  @DisplayName("Returns 404 Not Found when claim does not exist")
  void returnsNotFoundWhenClaimDoesNotExist() throws Exception {
    UUID claimId = Uuid7.timeBasedUuid();
    when(claimHistoryService.getTimeline(eq(claimId), ArgumentMatchers.any()))
        .thenThrow(new ClaimNotFoundException("No Claim found with id: " + claimId));

    mockMvc
        .perform(get(HISTORY_URI, claimId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("No Claim found with id: " + claimId));
  }

  @Test
  @DisplayName("Returns 400 Bad Request for invalid claim id")
  void returnsBadRequestForInvalidClaimId() throws Exception {
    mockMvc.perform(get(HISTORY_URI, "not-a-uuid")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Passes null Pageable to service when no pagination parameters supplied")
  void usesDefaultTimelineWhenLimitIsNull() {
    // The generated contract defaults limit to 50, but when the controller is invoked directly
    // it passes a null Pageable to the service. Assert the controller remains null-safe and
    // forwards a null Pageable so service-side unpaged behaviour can be applied.
    UUID claimId = Uuid7.timeBasedUuid();
    ClaimHistoryController controller =
        new ClaimHistoryController(claimHistoryService, objectMapper);
    when(claimHistoryService.getTimeline(eq(claimId), ArgumentMatchers.isNull()))
        .thenReturn(
            new ClaimHistoryPage(
                List.of(submissionRow(Uuid7.timeBasedUuid(), "SYSTEM")), 1L, 0, 20));

    ResponseEntity<ClaimHistoryResultSet> response =
        controller.getClaimHistory(claimId, (org.springframework.data.domain.Pageable) null);

    verify(claimHistoryService).getTimeline(eq(claimId), ArgumentMatchers.isNull());
    Assertions.assertThat(response.getBody()).isNotNull();
    Assertions.assertThat(response.getBody().getEvents()).hasSize(1);
  }
}
