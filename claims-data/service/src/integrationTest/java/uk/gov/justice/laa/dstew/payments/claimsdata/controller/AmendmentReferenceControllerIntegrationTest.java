package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AmendmentReasonReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.RequestedByReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AmendmentRequestedByReferenceList;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.AmendmentReasonReferenceRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.RequestedByReferenceRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Integration tests for the amendment reference lookup. Read assertions run against the Flyway seed
 * data (V40). Mutation tests use temporary rows with a dedicated code so they do not disturb the
 * seeded data, and clean up after themselves.
 */
@DisplayName("Amendment reference lookup (integration)")
class AmendmentReferenceControllerIntegrationTest extends AbstractIntegrationTest {

  private static final String ENDPOINT = SystemReferencePaths.AMENDMENT_REQUESTED_BY;
  private static final String TEMP_CODE = "ZZ_TEST_TEMP_PARTY";

  @Autowired private RequestedByReferenceRepository requestedByReferenceRepository;
  @Autowired private AmendmentReasonReferenceRepository amendmentReasonReferenceRepository;

  @AfterEach
  void cleanUpTemporaryRows() {
    // Delete all temporary reasons (active and inactive) before their parent Requested By row,
    // otherwise an inactive reason left behind would block the FK delete and leak into later tests.
    amendmentReasonReferenceRepository.findAll().stream()
        .filter(r -> TEMP_CODE.equals(r.getRequestedByCode()))
        .forEach(amendmentReasonReferenceRepository::delete);
    requestedByReferenceRepository.findAll().stream()
        .filter(r -> TEMP_CODE.equals(r.getCode()))
        .forEach(requestedByReferenceRepository::delete);
  }

  @Nested
  @DisplayName("GET endpoint - lookup response")
  class LookupResponse {

    @Test
    @DisplayName("returns seeded Requested By values in display order with nested reasons")
    void returnsSeededRequestedByValuesInDisplayOrderWithNestedReasons() throws Exception {
      AmendmentRequestedByReferenceList result = callEndpoint();

      assertThat(result.getRequestedBy())
          .extracting("code")
          .containsExactly("PROVIDER", "CONTRACT_MANAGEMENT", "ASSURANCE");

      var provider = result.getRequestedBy().get(0);
      assertThat(provider.getReasons())
          .extracting("code")
          .containsExactly(
              "PROVIDER_ERROR", "CASE_REOPENED_REBILLED", "RECOVERY_FROM_CLIENT_OR_OTHER_SIDE");

      var assurance = result.getRequestedBy().get(2);
      assertThat(assurance.getReasons())
          .extracting("code")
          .containsExactly("INCORRECT_MEANS_ASSESSMENT", "OTHER");
    }

    @Test
    @DisplayName("excludes an inactive Requested By value from the lookup")
    void inactiveRequestedByValueIsExcludedFromLookup() throws Exception {
      requestedByReferenceRepository.save(
          RequestedByReferenceEntity.builder()
              .id(Uuid7.timeBasedUuid())
              .code(TEMP_CODE)
              .displayLabel("Temp Party")
              .isActive(false)
              .displayOrder(999)
              .createdByUserId("integration-test")
              .createdOn(Instant.now())
              .build());

      AmendmentRequestedByReferenceList result = callEndpoint();

      assertThat(result.getRequestedBy()).extracting("code").doesNotContain(TEMP_CODE);
    }

    @Test
    @DisplayName("excludes an inactive reason from its Requested By value in the lookup")
    void inactiveReasonIsExcludedFromLookup() throws Exception {
      requestedByReferenceRepository.save(
          RequestedByReferenceEntity.builder()
              .id(Uuid7.timeBasedUuid())
              .code(TEMP_CODE)
              .displayLabel("Temp Party")
              .isActive(true)
              .displayOrder(999)
              .createdByUserId("integration-test")
              .createdOn(Instant.now())
              .build());
      amendmentReasonReferenceRepository.save(
          AmendmentReasonReferenceEntity.builder()
              .id(Uuid7.timeBasedUuid())
              .requestedByCode(TEMP_CODE)
              .code("TEMP_INACTIVE_REASON")
              .displayLabel("Temp inactive reason")
              .isActive(false)
              .displayOrder(10)
              .createdByUserId("integration-test")
              .createdOn(Instant.now())
              .build());

      AmendmentRequestedByReferenceList result = callEndpoint();

      var tempParty =
          result.getRequestedBy().stream()
              .filter(r -> TEMP_CODE.equals(r.getCode()))
              .findFirst()
              .orElseThrow();
      assertThat(tempParty.getReasons()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Seeded reference data integrity")
  class SeededReferenceDataIntegrity {

    @Test
    @DisplayName("generates reference ids as UUIDv7")
    void seededReferenceIdsAreUuidV7() {
      RequestedByReferenceEntity provider =
          requestedByReferenceRepository.findByIsActiveTrueOrderByDisplayOrderAsc().stream()
              .filter(r -> "PROVIDER".equals(r.getCode()))
              .findFirst()
              .orElseThrow();

      assertThat(provider.getId().version()).isEqualTo(7);
    }

    @Test
    @DisplayName("populates create audit columns for seeded rows")
    void seededReferenceRowsHaveAuditActorPopulated() {
      RequestedByReferenceEntity provider =
          requestedByReferenceRepository.findByIsActiveTrueOrderByDisplayOrderAsc().stream()
              .filter(r -> "PROVIDER".equals(r.getCode()))
              .findFirst()
              .orElseThrow();

      assertThat(provider.getCreatedByUserId()).isNotBlank();
      assertThat(provider.getCreatedOn()).isNotNull();
    }

    @Test
    @DisplayName("leaves the underlying code unchanged when the display label is updated")
    void updatingDisplayLabelLeavesCodeUnchanged() {
      UUID id = Uuid7.timeBasedUuid();
      requestedByReferenceRepository.save(
          RequestedByReferenceEntity.builder()
              .id(id)
              .code(TEMP_CODE)
              .displayLabel("Original label")
              .isActive(true)
              .displayOrder(999)
              .createdByUserId("integration-test")
              .createdOn(Instant.now())
              .build());

      RequestedByReferenceEntity saved = requestedByReferenceRepository.findById(id).orElseThrow();
      saved.setDisplayLabel("Updated label");
      saved.setUpdatedByUserId("integration-test-update");
      requestedByReferenceRepository.saveAndFlush(saved);

      RequestedByReferenceEntity reloaded =
          requestedByReferenceRepository.findById(id).orElseThrow();
      assertThat(reloaded.getCode()).isEqualTo(TEMP_CODE);
      assertThat(reloaded.getDisplayLabel()).isEqualTo("Updated label");
      assertThat(reloaded.getUpdatedByUserId()).isEqualTo("integration-test-update");
    }
  }

  private AmendmentRequestedByReferenceList callEndpoint() throws Exception {
    MvcResult mvcResult =
        mockMvc
            .perform(get(ENDPOINT).header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();
    return OBJECT_MAPPER.readValue(
        mvcResult.getResponse().getContentAsString(), AmendmentRequestedByReferenceList.class);
  }
}
