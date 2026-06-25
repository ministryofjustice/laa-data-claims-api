package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AmendmentReasonReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.RequestedByReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.AmendmentReasonReferenceRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.RequestedByReferenceRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Assembled-chain integration tests for {@link ClaimAmendmentService}.
 *
 * <p>These complement the mock-based {@code ClaimAmendmentServiceTest} (orchestration mechanics)
 * and the per-step unit tests (exhaustive rule coverage) by exercising the <b>real</b> wiring end
 * to end: the real {@code STEP_ORDER}, the real validation step beans, and the real {@code
 * AmendmentReferenceDataProvider} reading the governed reference data from a real PostgreSQL
 * (Testcontainers). The reference-data reads run against the Flyway seed (V41).
 *
 * <p>Scope is deliberately <b>representative</b>, not exhaustive: it proves the chain is assembled
 * correctly (ordering, fatal short-circuit, multi-error collection) and that the steps interact
 * with real reference data and the DB-backed active flags - it does not re-test every validation
 * code, which stays at the unit level.
 *
 * <p>The class is {@link Transactional} so temporary reference rows (and the deletion used to
 * simulate an unavailable reference dataset) are rolled back after each test, leaving the seeded
 * data untouched. The provider's read shares the test transaction, so it sees the uncommitted
 * changes.
 */
@DisplayName("ClaimAmendmentService assembled-chain integration test")
@Transactional
class ClaimAmendmentServiceIntegrationTest extends AbstractIntegrationTest {

  private static final String VALID_UUID = "0190b6a0-9b7e-7c8a-9e2d-2f3a4b5c6d7e";

  // Seeded (V41) codes used by the happy-path and lookup scenarios.
  private static final String SEEDED_PARTY = "PROVIDER";
  private static final String SEEDED_REASON = "PROVIDER_ERROR";
  // INCORRECT_MEANS_ASSESSMENT is seeded only for CONTRACT_MANAGEMENT / ASSURANCE, never PROVIDER.
  private static final String SEEDED_REASON_OTHER_PARTY = "INCORRECT_MEANS_ASSESSMENT";

  @Autowired private ClaimAmendmentService claimAmendmentService;
  @Autowired private RequestedByReferenceRepository requestedByReferenceRepository;
  @Autowired private AmendmentReasonReferenceRepository amendmentReasonReferenceRepository;

  @Nested
  @DisplayName("happy path")
  class HappyPath {

    @Test
    @DisplayName("valid status, requested-by, reason and user id produce no errors")
    void allValidProducesNoErrors() {
      ClaimAmendmentState state =
          stateOf(ClaimStatus.VALID, SEEDED_PARTY, SEEDED_REASON, VALID_UUID);

      assertThat(claimAmendmentService.orchestrate(state)).isEmpty();
    }
  }

  @Nested
  @DisplayName("fatal short-circuit")
  class FatalShortCircuit {

    @Test
    @DisplayName("a voided claim returns only the fatal status error; later steps do not run")
    void voidClaimReturnsOnlyFatalAndSkipsLaterSteps() {
      // Requested-by, reason and user id are all invalid too, but the fatal status error must
      // short-circuit the flow before the reference and user-id steps run.
      ClaimAmendmentState state =
          stateOf(ClaimStatus.VOID, "MADE_UP", "ALSO_MADE_UP", "not-a-uuid");

      assertThat(claimAmendmentService.orchestrate(state))
          .extracting(ClaimAmendmentValidationError::getCode)
          .containsExactly(ClaimAmendmentValidationCode.INVALID_VOIDED_CLAIM_NOT_AMENDABLE);
    }
  }

  @Nested
  @DisplayName("reference-data lookups against the seeded data")
  class ReferenceLookups {

    @Test
    @DisplayName("an unknown requested-by code is rejected as unknown")
    void unknownRequestedBy() {
      ClaimAmendmentState state = stateOf(ClaimStatus.VALID, "MADE_UP", SEEDED_REASON, VALID_UUID);

      assertThat(claimAmendmentService.orchestrate(state))
          .extracting(ClaimAmendmentValidationError::getCode)
          .contains(ClaimAmendmentValidationCode.INVALID_REQUESTED_BY_UNKNOWN);
    }

    @Test
    @DisplayName("a reason valid for another party is rejected for the submitted party")
    void reasonNotValidForRequestedBy() {
      // INCORRECT_MEANS_ASSESSMENT exists, but not under PROVIDER.
      ClaimAmendmentState state =
          stateOf(ClaimStatus.VALID, SEEDED_PARTY, SEEDED_REASON_OTHER_PARTY, VALID_UUID);

      assertThat(claimAmendmentService.orchestrate(state))
          .extracting(ClaimAmendmentValidationError::getCode)
          .containsExactly(ClaimAmendmentValidationCode.INVALID_AMENDMENT_REASON_FOR_REQUESTED_BY);
    }

    @Test
    @DisplayName("an inactive requested-by value (read from the DB active flag) is rejected")
    void inactiveRequestedBy() {
      String inactiveParty = "ZZ_TEST_INACTIVE_PARTY";
      String partyReason = "ZZ_TEST_INACTIVE_PARTY_REASON";
      requestedByReferenceRepository.save(
          requestedByRow(inactiveParty, "Temp Inactive Party", false));
      amendmentReasonReferenceRepository.save(
          reasonRow(inactiveParty, partyReason, "Temp reason", true));

      ClaimAmendmentState state =
          stateOf(ClaimStatus.VALID, inactiveParty, partyReason, VALID_UUID);

      assertThat(claimAmendmentService.orchestrate(state))
          .extracting(ClaimAmendmentValidationError::getCode)
          .containsExactly(ClaimAmendmentValidationCode.INVALID_REQUESTED_BY_INACTIVE);
    }
  }

  @Nested
  @DisplayName("user-id step")
  class UserIdStep {

    @Test
    @DisplayName("a structurally invalid user id is rejected while the metadata is valid")
    void invalidUserId() {
      ClaimAmendmentState state =
          stateOf(ClaimStatus.VALID, SEEDED_PARTY, SEEDED_REASON, "not-a-uuid");

      assertThat(claimAmendmentService.orchestrate(state))
          .extracting(ClaimAmendmentValidationError::getCode)
          .containsExactly(ClaimAmendmentValidationCode.INVALID_USER_IDENTIFIER_FORMAT);
    }
  }

  @Nested
  @DisplayName("multi-error collection across steps")
  class MultiError {

    @Test
    @DisplayName("missing metadata and a bad user id are all collected when no error is fatal")
    void collectsErrorsFromMultipleSteps() {
      ClaimAmendmentState state = stateOf(ClaimStatus.VALID, null, null, "bad");

      assertThat(claimAmendmentService.orchestrate(state))
          .extracting(ClaimAmendmentValidationError::getCode)
          .containsExactlyInAnyOrder(
              ClaimAmendmentValidationCode.INVALID_REQUESTED_BY_MISSING,
              ClaimAmendmentValidationCode.INVALID_AMENDMENT_REASON_MISSING,
              ClaimAmendmentValidationCode.INVALID_USER_IDENTIFIER_FORMAT);
    }
  }

  @Nested
  @DisplayName("controlled technical failure")
  class TechnicalFailure {

    @Test
    @DisplayName("an unavailable (empty) reference dataset yields a single fatal technical error")
    void emptyReferenceDataReturnsFatalTechnicalError() {
      // Reasons reference the parties (FK ON DELETE RESTRICT), so clear reasons first. Rolled back.
      amendmentReasonReferenceRepository.deleteAllInBatch();
      requestedByReferenceRepository.deleteAllInBatch();

      ClaimAmendmentState state =
          stateOf(ClaimStatus.VALID, SEEDED_PARTY, SEEDED_REASON, VALID_UUID);

      List<ClaimAmendmentValidationError> errors = claimAmendmentService.orchestrate(state);

      assertThat(errors)
          .singleElement()
          .satisfies(
              error -> {
                assertThat(error.getCode())
                    .isEqualTo(
                        ClaimAmendmentValidationCode
                            .TECHNICAL_ERROR_AMENDMENT_METADATA_REFERENCE_DATA);
                assertThat(error.isFatal()).isTrue();
              });
    }
  }

  // ---------------------------------------------------------------------------
  // Fixtures
  // ---------------------------------------------------------------------------

  private ClaimAmendmentState stateOf(
      ClaimStatus status, String requestedBy, String reason, String userId) {
    return ClaimAmendmentState.builder()
        .beforeState(
            ClaimStateSnapshot.builder().claimId(Uuid7.timeBasedUuid()).status(status).build())
        .requestPayload(
            ClaimAmendmentPayload.builder()
                .amendmentRequestedBy(JsonNullable.of(requestedBy))
                .amendmentReasonCode(JsonNullable.of(reason))
                .amendmentUserId(JsonNullable.of(userId))
                .build())
        .build();
  }

  private RequestedByReferenceEntity requestedByRow(String code, String label, boolean active) {
    return RequestedByReferenceEntity.builder()
        .id(Uuid7.timeBasedUuid())
        .code(code)
        .displayLabel(label)
        .isActive(active)
        .displayOrder(999)
        .createdByUserId("integration-test")
        .createdOn(Instant.now())
        .build();
  }

  private AmendmentReasonReferenceEntity reasonRow(
      String requestedByCode, String code, String label, boolean active) {
    return AmendmentReasonReferenceEntity.builder()
        .id(Uuid7.timeBasedUuid())
        .requestedByCode(requestedByCode)
        .code(code)
        .displayLabel(label)
        .isActive(active)
        .displayOrder(10)
        .createdByUserId("integration-test")
        .createdOn(Instant.now())
        .build();
  }
}
