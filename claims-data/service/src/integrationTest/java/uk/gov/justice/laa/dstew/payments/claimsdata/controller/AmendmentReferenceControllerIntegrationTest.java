package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AmendmentReasonReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.RequestedByReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AmendmentRequestedByReferenceList;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.AmendmentReasonReferenceRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.RequestedByReferenceRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Integration tests for the amendment reference lookup. Read assertions run against the Flyway seed
 * data (V41). Mutation tests insert temporary rows with a dedicated code; the class is {@link
 * Transactional} so Spring rolls each test back, leaving the seeded data untouched (no manual
 * teardown required). The MockMvc call shares the test transaction, so the read-only service sees
 * the uncommitted temporary rows.
 */
@DisplayName("Amendment reference lookup (integration)")
@Transactional
class AmendmentReferenceControllerIntegrationTest extends AbstractIntegrationTest {

  private static final String ENDPOINT = SystemReferencePaths.AMENDMENT_REQUESTED_BY;
  private static final String TEMP_CODE = "ZZ_TEST_TEMP_PARTY";

  @Autowired private RequestedByReferenceRepository requestedByReferenceRepository;
  @Autowired private AmendmentReasonReferenceRepository amendmentReasonReferenceRepository;

  // Used to force a real DB round-trip in updatingDisplayLabelLeavesCodeUnchanged: because the
  // class
  // is @Transactional, repository reads would otherwise be served from the shared persistence
  // context (L1 cache), so a flush()/clear() is needed to prove the update actually hit the DB.
  @PersistenceContext private EntityManager entityManager;

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
      assertThat(provider.getIsActive()).isTrue();
      assertThat(provider.getReasons())
          .extracting("code")
          .containsExactly(
              "PROVIDER_ERROR", "CASE_REOPENED_REBILLED", "RECOVERY_FROM_CLIENT_OR_OTHER_SIDE");
      assertThat(provider.getReasons()).extracting("isActive").containsOnly(true);

      var assurance = result.getRequestedBy().get(2);
      assertThat(assurance.getReasons())
          .extracting("code")
          .containsExactly("INCORRECT_MEANS_ASSESSMENT", "OTHER");
    }

    @Test
    @DisplayName("includes an inactive Requested By value flagged is_active=false in the lookup")
    void inactiveRequestedByValueIsIncludedFlaggedInactive() throws Exception {
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

      var tempParty =
          result.getRequestedBy().stream()
              .filter(r -> TEMP_CODE.equals(r.getCode()))
              .findFirst()
              .orElseThrow();
      assertThat(tempParty.getIsActive()).isFalse();
      assertThat(tempParty.getDisplayLabel()).isEqualTo("Temp Party");
    }

    @Test
    @DisplayName("includes an inactive reason flagged is_active=false under its Requested By value")
    void inactiveReasonIsIncludedFlaggedInactive() throws Exception {
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
      var reason =
          tempParty.getReasons().stream()
              .filter(r -> "TEMP_INACTIVE_REASON".equals(r.getCode()))
              .findFirst()
              .orElseThrow();
      assertThat(reason.getIsActive()).isFalse();
      assertThat(reason.getDisplayLabel()).isEqualTo("Temp inactive reason");
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

      // Detach everything so the reload below is a genuine SELECT from the database rather than a
      // hit on the shared persistence context (L1 cache). Without this, @Transactional would return
      // the same managed instance and the assertions would not prove the update was persisted.
      entityManager.flush();
      entityManager.clear();

      RequestedByReferenceEntity reloaded =
          requestedByReferenceRepository.findById(id).orElseThrow();
      assertThat(reloaded.getCode()).isEqualTo(TEMP_CODE);
      assertThat(reloaded.getDisplayLabel()).isEqualTo("Updated label");
      assertThat(reloaded.getUpdatedByUserId()).isEqualTo("integration-test-update");
    }
  }

  @Nested
  @DisplayName("Active-only reason data access (findByIsActiveTrue...)")
  class ActiveOnlyReasonDataAccess {

    @Test
    @DisplayName("excludes inactive reasons, returning only active ones")
    void excludesInactiveReasons() {
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
          reason(TEMP_CODE, "TEMP_ACTIVE_REASON", "Temp active reason", 10, true));
      amendmentReasonReferenceRepository.save(
          reason(TEMP_CODE, "TEMP_INACTIVE_REASON", "Temp inactive reason", 20, false));

      var activeReasons =
          amendmentReasonReferenceRepository
              .findByIsActiveTrueOrderByRequestedByCodeAscDisplayOrderAsc()
              .stream()
              .filter(r -> TEMP_CODE.equals(r.getRequestedByCode()))
              .toList();

      assertThat(activeReasons).extracting("code").containsExactly("TEMP_ACTIVE_REASON");
      assertThat(activeReasons).extracting("isActive").containsOnly(true);
    }

    @Test
    @DisplayName("orders results by Requested By code then display order")
    void ordersByRequestedByCodeThenDisplayOrder() {
      // Two parties (alphabetical: AAA before ZZZ), each with reasons saved out of display order.
      String partyA = "AAA_TEST_PARTY";
      String partyZ = "ZZZ_TEST_PARTY";
      requestedByReferenceRepository.save(
          RequestedByReferenceEntity.builder()
              .id(Uuid7.timeBasedUuid())
              .code(partyA)
              .displayLabel("Party A")
              .isActive(true)
              .displayOrder(998)
              .createdByUserId("integration-test")
              .createdOn(Instant.now())
              .build());
      requestedByReferenceRepository.save(
          RequestedByReferenceEntity.builder()
              .id(Uuid7.timeBasedUuid())
              .code(partyZ)
              .displayLabel("Party Z")
              .isActive(true)
              .displayOrder(999)
              .createdByUserId("integration-test")
              .createdOn(Instant.now())
              .build());
      amendmentReasonReferenceRepository.save(reason(partyZ, "Z_SECOND", "Z second", 20, true));
      amendmentReasonReferenceRepository.save(reason(partyA, "A_SECOND", "A second", 20, true));
      amendmentReasonReferenceRepository.save(reason(partyZ, "Z_FIRST", "Z first", 10, true));
      amendmentReasonReferenceRepository.save(reason(partyA, "A_FIRST", "A first", 10, true));

      var orderedTestReasons =
          amendmentReasonReferenceRepository
              .findByIsActiveTrueOrderByRequestedByCodeAscDisplayOrderAsc()
              .stream()
              .filter(r -> r.getRequestedByCode().endsWith("_TEST_PARTY"))
              .toList();

      // Grouped by party code ascending, then by display order ascending within each party.
      assertThat(orderedTestReasons)
          .extracting("code")
          .containsExactly("A_FIRST", "A_SECOND", "Z_FIRST", "Z_SECOND");
    }

    private AmendmentReasonReferenceEntity reason(
        String requestedByCode, String code, String label, int order, boolean active) {
      return AmendmentReasonReferenceEntity.builder()
          .id(Uuid7.timeBasedUuid())
          .requestedByCode(requestedByCode)
          .code(code)
          .displayLabel(label)
          .isActive(active)
          .displayOrder(order)
          .createdByUserId("integration-test")
          .createdOn(Instant.now())
          .build();
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
