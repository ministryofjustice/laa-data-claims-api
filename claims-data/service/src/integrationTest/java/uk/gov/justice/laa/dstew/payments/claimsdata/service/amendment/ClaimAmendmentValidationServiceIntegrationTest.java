package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.CalculatedFeeDetailSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AmendmentReasonReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.RequestedByReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.helper.MockServerIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.AmendmentReasonReferenceRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.RequestedByReferenceRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Assembled-chain integration tests for {@link ClaimAmendmentValidationService}.
 *
 * <p>These complement the mock-based {@code ClaimAmendmentValidationServiceTest} (orchestration
 * mechanics) and the per-step unit tests (exhaustive rule coverage) by exercising the <b>real</b>
 * wiring end to end: the real {@code STEP_ORDER}, the real validation step beans, and the real
 * {@code AmendmentReferenceDataProvider} reading the governed reference data from a real PostgreSQL
 * (Testcontainers). The reference-data reads run against the Flyway seed (V41).
 *
 * <p>The assembled chain includes {@code AmendmentExternalValidationStep}, which delegates to the
 * shared claims-validation-core {@code ValidationService}. That library makes outbound HTTP calls
 * to the Fee Scheme Platform and Provider Details APIs, which are stubbed here via {@link
 * MockServerIntegrationTest} so the chain runs end to end against controlled responses.
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
@DisplayName("ClaimAmendmentValidationService assembled-chain integration test")
@Transactional
class ClaimAmendmentValidationServiceIntegrationTest extends MockServerIntegrationTest {

  private static final String VALID_UUID = "0190b6a0-9b7e-7c8a-9e2d-2f3a4b5c6d7e";

  // The claim fee code used by the fixtures below (alphanumeric, <= 10 chars per the claim schema).
  private static final String CLAIM_FEE_CODE = "FEE01";

  // Seeded (V41) codes used by the happy-path and lookup scenarios.
  private static final String SEEDED_PARTY = "PROVIDER";
  private static final String SEEDED_REASON = "PROVIDER_ERROR";
  // INCORRECT_MEANS_ASSESSMENT is seeded only for CONTRACT_MANAGEMENT / ASSURANCE, never PROVIDER.
  private static final String SEEDED_REASON_OTHER_PARTY = "INCORRECT_MEANS_ASSESSMENT";
  public static final String NOT_A_UUID = "not-a-uuid";

  @Autowired private ClaimAmendmentValidationService validationService;
  @Autowired private RequestedByReferenceRepository requestedByReferenceRepository;
  @Autowired private AmendmentReasonReferenceRepository amendmentReasonReferenceRepository;

  /**
   * Stubs the validation-core external calls so the assembled chain can run end to end. The
   * responses are deliberately "clean" so the external validation contributes no issues, leaving
   * the amendment-metadata errors (the focus of these tests) as the only failures.
   */
  @BeforeEach
  void setUp() throws IOException {
    stubExternalValidationEndpoints();
  }

  @Nested
  @DisplayName("happy path")
  class HappyPath {

    @Test
    @DisplayName("valid status, requested-by, reason and user id produce no errors")
    void allValidProducesNoErrors() {
      ClaimAmendmentState state =
          stateOf(ClaimStatus.VALID, SEEDED_PARTY, SEEDED_REASON, VALID_UUID);

      assertThat(validationService.validateAmendmentRequest(state)).isEmpty();
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
      ClaimAmendmentState state = stateOf(ClaimStatus.VOID, "MADE_UP", "ALSO_MADE_UP", NOT_A_UUID);

      assertThat(validationService.validateAmendmentRequest(state))
          .extracting(ClaimAmendmentValidationError::getCode)
          .containsExactly(
              ClaimAmendmentValidationCode.INVALID_VOIDED_CLAIM_NOT_AMENDABLE.toString());
    }
  }

  @Nested
  @DisplayName("reference-data lookups against the seeded data")
  class ReferenceLookups {

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

    @Test
    @DisplayName("an unknown requested-by code is rejected as unknown")
    void unknownRequestedBy() {
      ClaimAmendmentState state = stateOf(ClaimStatus.VALID, "MADE_UP", SEEDED_REASON, VALID_UUID);

      assertThat(validationService.validateAmendmentRequest(state))
          .extracting(ClaimAmendmentValidationError::getCode)
          .contains(ClaimAmendmentValidationCode.INVALID_REQUESTED_BY_UNKNOWN.toString());
    }

    @Test
    @DisplayName("a reason valid for another party is rejected for the submitted party")
    void reasonNotValidForRequestedBy() {
      // INCORRECT_MEANS_ASSESSMENT exists, but not under PROVIDER.
      ClaimAmendmentState state =
          stateOf(ClaimStatus.VALID, SEEDED_PARTY, SEEDED_REASON_OTHER_PARTY, VALID_UUID);

      assertThat(validationService.validateAmendmentRequest(state))
          .extracting(ClaimAmendmentValidationError::getCode)
          .containsExactly(
              ClaimAmendmentValidationCode.INVALID_AMENDMENT_REASON_FOR_REQUESTED_BY.toString());
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

      assertThat(validationService.validateAmendmentRequest(state))
          .extracting(ClaimAmendmentValidationError::getCode)
          .containsExactly(ClaimAmendmentValidationCode.INVALID_REQUESTED_BY_INACTIVE.toString());
    }
  }

  @Nested
  @DisplayName("user-id step")
  class UserIdStep {

    @Test
    @DisplayName("a structurally invalid user id is rejected while the metadata is valid")
    void invalidUserId() {
      ClaimAmendmentState state =
          stateOf(ClaimStatus.VALID, SEEDED_PARTY, SEEDED_REASON, NOT_A_UUID);

      assertThat(validationService.validateAmendmentRequest(state))
          .extracting(ClaimAmendmentValidationError::getCode)
          .containsExactly(ClaimAmendmentValidationCode.INVALID_USER_IDENTIFIER_FORMAT.toString());
    }
  }

  @Nested
  @DisplayName("multi-error collection across steps")
  class MultiError {

    @Test
    @DisplayName("missing metadata and a bad user id are all collected when no error is fatal")
    void collectsErrorsFromMultipleSteps() {
      ClaimAmendmentState state = stateOf(ClaimStatus.VALID, null, null, "bad");

      assertThat(validationService.validateAmendmentRequest(state))
          .extracting(ClaimAmendmentValidationError::getCode)
          .containsExactlyInAnyOrder(
              ClaimAmendmentValidationCode.INVALID_REQUESTED_BY_MISSING.toString(),
              ClaimAmendmentValidationCode.INVALID_AMENDMENT_REASON_MISSING.toString(),
              ClaimAmendmentValidationCode.INVALID_USER_IDENTIFIER_FORMAT.toString());
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

      List<ClaimAmendmentValidationError> errors =
          validationService.validateAmendmentRequest(state);

      assertThat(errors)
          .singleElement()
          .satisfies(
              error -> {
                assertThat(error.getCode())
                    .isEqualTo(
                        ClaimAmendmentValidationCode
                            .TECHNICAL_ERROR_AMENDMENT_METADATA_REFERENCE_DATA
                            .toString());
                assertThat(error.isFatal()).isTrue();
              });
    }
  }

  @Nested
  @DisplayName("field amendability gate (Step 12 aggregation)")
  class FieldAmendabilityGate {

    @Test
    @DisplayName("a non-amendable changed field is collected and aggregates with other errors")
    void nonAmendableFieldAggregatesWithOtherErrors() {
      ClaimAmendmentState state = stateWithNonAmendableChange();

      assertThat(validationService.validateAmendmentRequest(state))
          .extracting(ClaimAmendmentValidationError::getCode)
          .contains(
              ClaimAmendmentValidationCode.INVALID_FIELD_NOT_AMENDABLE_FOR_AREA_OF_LAW.toString(),
              ClaimAmendmentValidationCode.INVALID_USER_IDENTIFIER_FORMAT.toString());
    }

    /**
     * Builds a state whose requested change is not amendable for the claim's area of law, alongside
     * a structurally invalid user id. Scheme ID is amendable for Crime Lower only, so changing it
     * on a Legal Help claim must be rejected by the amendability gate; the invalid user id proves
     * the gate's field-level failure aggregates with other collected errors.
     */
    private ClaimAmendmentState stateWithNonAmendableChange() {
      ClaimStateSnapshot before =
          ClaimStateSnapshot.builder()
              .claimId(Uuid7.timeBasedUuid())
              .areaOfLaw(AreaOfLaw.LEGAL_HELP)
              .feeCode(CLAIM_FEE_CODE)
              .status(ClaimStatus.VALID)
              .lineNumber(1)
              .netDisbursementAmount(new BigDecimal("0.00"))
              .disbursementsVatAmount(new BigDecimal("0.00"))
              .caseStartDate(LocalDate.ofInstant(Instant.now(), ZoneId.systemDefault()))
              .schemeId("SCHEME-1")
              .calculatedFeeDetail(CalculatedFeeDetailSnapshot.builder().totalAmount(BigDecimal.TEN).build())
              .build();

      ClaimStateSnapshot after = before.toBuilder().schemeId("SCHEME-2").build();

      return ClaimAmendmentState.builder()
          .beforeState(before)
          .postAmendmentState(after)
          .requestPayload(
              ClaimAmendmentPayload.builder()
                  .amendmentRequestedBy(JsonNullable.of(SEEDED_PARTY))
                  .amendmentReasonCode(JsonNullable.of(SEEDED_REASON))
                  .amendmentUserId(JsonNullable.of(NOT_A_UUID))
                  .build())
          .build();
    }
  }

  // ---------------------------------------------------------------------------
  // Fixtures
  // ---------------------------------------------------------------------------

  private ClaimAmendmentState stateOf(
      ClaimStatus status, String requestedBy, String reason, String userId) {

    // A schema-valid claim so the external validation step (which runs the full CLAIM_* validator
    // set against MockServer) contributes no issues, leaving the amendment-metadata errors as the
    // only failures. Required fields per the validation-core claim schema: status, line_number,
    // net_disbursement_amount, disbursements_vat_amount, fee_code (alphanumeric, <= 10 chars).
    ClaimStateSnapshot snapshot =
        ClaimStateSnapshot.builder()
            .claimId(Uuid7.timeBasedUuid())
            .feeCode(CLAIM_FEE_CODE)
            .status(status)
            .lineNumber(1)
            .netDisbursementAmount(new BigDecimal("0.00"))
            .disbursementsVatAmount(new BigDecimal("0.00"))
            .caseStartDate(LocalDate.ofInstant(Instant.now(), ZoneId.systemDefault()))
            .calculatedFeeDetail(CalculatedFeeDetailSnapshot.builder().totalAmount(BigDecimal.TEN).build())
            .build();

    return ClaimAmendmentState.builder()
        .beforeState(snapshot)
        .postAmendmentState(snapshot)
        .requestPayload(
            ClaimAmendmentPayload.builder()
                .amendmentRequestedBy(JsonNullable.of(requestedBy))
                .amendmentReasonCode(JsonNullable.of(reason))
                .amendmentUserId(JsonNullable.of(userId))
                .build())
        .build();
  }
}
