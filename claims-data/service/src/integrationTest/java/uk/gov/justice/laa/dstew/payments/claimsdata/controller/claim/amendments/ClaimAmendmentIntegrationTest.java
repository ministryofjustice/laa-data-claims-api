package uk.gov.justice.laa.dstew.payments.claimsdata.controller.claim.amendments;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockserver.verify.VerificationTimes;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

/**
 * Happy-path end-to-end integration test for a successful claim amendment (DSTEW-1646).
 *
 * <p>This is the deliberately-basic "golden path" counterpart to the PDA-focused suites: it drives
 * the real {@code PATCH /api/v1/submissions/{submissionId}/claims/{claimId}} endpoint with a valid,
 * PDA-clean amendment and asserts the full write actually lands. It proves the wiring from the
 * controller through validation (which passes) to commit, and that all three affected tables are
 * updated:
 *
 * <ul>
 *   <li><b>claim_amendment</b> - one audit row is written with the submitted requested-by, reason
 *       and user id.
 *   <li><b>claim</b> - the amended field (fee code) is applied and the {@code is_amended} flag is
 *       set.
 *   <li><b>client</b> - the amended client name is applied, proving the client table is affected.
 * </ul>
 *
 * <p>It reuses the richly-seeded {@code CLAIM_1} (which already has a {@code Client} and a {@code
 * ClaimSummaryFee} carrying the schema-required disbursement amounts), set to the amendable {@code
 * VALID} status. The Fee Scheme Platform calls are stubbed by the base class and the Provider
 * Details {@code /schedules} call is stubbed with a clean {@code 200}, so the external validation
 * step contributes no errors and the amendment commits.
 *
 * <p><b>Scope.</b> This is intentionally a single, minimal happy path so we have end-to-end
 * coverage now, with scope to add richer field/edge coverage later.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("Amendment happy-path integration test")
class ClaimAmendmentIntegrationTest extends AbstractAmendmentPatchIntegrationTest {

  // Amended values applied by the patch (fee code kept alphanumeric and <= 10 chars per schema).
  private static final String AMENDED_FEE_CODE = "FEE99";
  private static final String AMENDED_CLIENT_FORENAME = "Amended-Forename";
  private static final String AMENDED_CLIENT_SURNAME = "Amended-Surname";
  private static final List<String> STRING_FIELDS =
      List.of(
          "id",
          "submission_id",
          "schedule_reference",
          "case_reference_number",
          "unique_file_number",
          "case_start_date",
          "case_concluded_date",
          "matter_type_code",
          "crime_matter_type_code",
          "fee_scheme_code",
          "fee_code",
          "procurement_area_code",
          "access_point_code",
          "delivery_location",
          "representation_order_date",
          "police_station_court_prison_id",
          "dscc_number",
          "maat_id",
          "prison_law_prior_approval_number",
          "scheme_id",
          "outreach_location",
          "referral_source",
          "client_forename",
          "client_surname",
          "client_date_of_birth",
          "unique_client_number",
          "client_postcode",
          "gender_code",
          "ethnicity_code",
          "disability_code",
          "client_type_code",
          "home_office_client_number",
          "cla_reference_number",
          "cla_exemption_code",
          "client_2_forename",
          "client_2_surname",
          "client_2_date_of_birth",
          "client_2_ucn",
          "client_2_postcode",
          "client_2_gender_code",
          "client_2_ethnicity_code",
          "client_2_disability_code",
          "case_id",
          "unique_case_id",
          "case_stage_code",
          "stage_reached_code",
          "standard_fee_category_code",
          "outcome_code",
          "designated_accredited_representative_code",
          "mental_health_tribunal_reference",
          "follow_on_work",
          "transfer_date",
          "exemption_criteria_satisfied",
          "exceptional_case_funding_reference",
          "prior_authority_reference",
          "meetings_attended_code",
          "court_location_code",
          "advice_type_code",
          "surgery_date",
          "ait_hearing_centre_code",
          "local_authority_number",
          "submission_period",
          "created_by_user_id",
          "amendment_requested_by",
          "amendment_reason_code");

  private static final List<String> INTEGER_FIELDS =
      List.of(
          "line_number",
          "suspects_defendants_count",
          "police_station_court_attendances_count",
          "mediation_sessions_count",
          "mediation_time_minutes",
          "advice_time",
          "travel_time",
          "waiting_time",
          "adjourned_hearing_fee_amount",
          "medical_reports_count",
          "surgery_clients_count",
          "surgery_matters_count",
          "cmrh_oral_count",
          "cmrh_telephone_count",
          "ho_interview",
          "total_warnings");

  private static final List<String> BOOLEAN_FIELDS =
      List.of(
          "is_duty_solicitor",
          "is_youth_court",
          "is_legally_aided",
          "client_2_is_legally_aided",
          "is_postal_application_accepted",
          "is_client_2_postal_application_accepted",
          "is_nrm_advice",
          "is_legacy_case",
          "is_vat_applicable",
          "is_tolerance_applicable",
          "is_london_rate",
          "is_additional_travel_payment",
          "is_eligible_client",
          "is_irc_surgery",
          "is_substantive_hearing",
          "is_amended",
          "has_assessment");

  private static final List<String> DECIMAL_FIELDS =
      List.of(
          "net_profit_costs_amount",
          "net_disbursement_amount",
          "net_counsel_costs_amount",
          "disbursements_vat_amount",
          "travel_waiting_costs_amount",
          "net_waiting_costs_amount",
          "costs_damages_recovered_amount",
          "detention_travel_waiting_costs_amount",
          "jr_form_filling_amount");

  private static final List<String> LONG_FIELDS = List.of("version");

  private static final List<String> UUID_FIELDS = List.of("amendment_user_id");

  private static final List<String> ENUM_FIELDS = List.of("status");

  private static final List<String> DATE_FIELDS =
      List.of(
          "case_start_date",
          "case_concluded_date",
          "representation_order_date",
          "client_date_of_birth",
          "client_2_date_of_birth",
          "transfer_date",
          "surgery_date");

  private static final List<String> INVALID_DATES =
      List.of(
          "banana",
          "99/99/9999",
          "31/02/2025",
          "2025-01-01",
          "13-12-2025",
          "01/13/2025",
          "%%%%",
          "01/00/2025",
          "01/01/10000");

  @Test
  @DisplayName("a valid amendment commits and updates the claim_amendment, claim and client tables")
  void validAmendmentCommitsAndUpdatesAllTables() throws Exception {
    // Clean PDA response so external validation contributes no errors and the amendment commits.
    stubProviderSchedulesOk();

    // Put the seeded claim into the amendable state.
    Claim seeded = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    seeded.setStatus(ClaimStatus.VALID);
    String originalFeeCode = seeded.getFeeCode();
    Claim savedClaim = claimRepository.saveAndFlush(seeded);

    // Amend a claim-level field (fee code) and the client name in one request. The patch carries
    // the claim's current version so it passes the early version gate.
    ClaimPatch patch = createBasePatch();
    patch.setVersion(savedClaim.getVersion());
    patch.setFeeCode(AMENDED_FEE_CODE);
    patch.setClientForename(AMENDED_CLIENT_FORENAME);
    patch.setClientSurname(AMENDED_CLIENT_SURNAME);

    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, patch);

    // A successful amendment returns 204 No Content.
    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NO_CONTENT.value());

    // claim_amendment: exactly one audit row with the submitted metadata.
    List<ClaimAmendment> amendments =
        claimAmendmentRepository.findByClaimIdOrderByIdDesc(CLAIM_1_ID);
    assertThat(amendments)
        .singleElement()
        .satisfies(
            amendment -> {
              assertThat(amendment.getRequestedByCode()).isEqualTo(REQUESTED_BY_PROVIDER);
              assertThat(amendment.getAmendmentReasonCode()).isEqualTo(REASON_PROVIDER_ERROR);
              assertThat(amendment.getCreatedByUserId()).isEqualTo(VALID_USER_UUID.toString());
            });

    // claim: the amended fee code is applied and the amended flag is set.
    Claim amendedClaim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    assertThat(amendedClaim.getFeeCode()).isEqualTo(AMENDED_FEE_CODE).isNotEqualTo(originalFeeCode);
    assertThat(amendedClaim.isAmended()).isTrue();

    // claim.version: a successful amendment advances the optimistic-lock version atomically as part
    // of the same commit (DSTEW-1753 / parent AC1), so a later stale submit is rejected.
    assertThat(amendedClaim.getVersion()).isGreaterThan(savedClaim.getVersion());

    // client: the amended client name is applied, proving the client table is affected.
    Client amendedClient = clientRepository.findByClaimId(CLAIM_1_ID).orElseThrow();
    assertThat(amendedClient.getClientForename()).isEqualTo(AMENDED_CLIENT_FORENAME);
    assertThat(amendedClient.getClientSurname()).isEqualTo(AMENDED_CLIENT_SURNAME);
  }

  @Test
  @DisplayName(
      "a stale claim version is rejected end-to-end with 409 Conflict and CLAIM_VERSION_CONFLICT")
  void staleClaimVersionIsRejectedWithConflict() throws Exception {
    // Clean PDA response so, if the flow reached external validation, it would not add noise. The
    // early version gate should short-circuit well before that.
    stubProviderSchedulesOk();

    Claim seeded = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    seeded.setStatus(ClaimStatus.VALID);
    claimRepository.saveAndFlush(seeded);

    // Submit a version that does not match the current claim version, simulating a claim that
    // changed since it was loaded.
    ClaimPatch patch = createBasePatch();
    patch.setVersion(999L);
    patch.setFeeCode(AMENDED_FEE_CODE);

    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, patch);

    // 409 Conflict carrying the stable machine-readable code and the user-safe message.
    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    String body = result.getResponse().getContentAsString();
    assertThat(body).contains("CLAIM_VERSION_CONFLICT");
    assertThat(body).contains("The claim has changed since it was loaded");

    // The conflict is fatal and nothing is written.
    assertThat(claimAmendmentRepository.findByClaimIdOrderByIdDesc(CLAIM_1_ID)).isEmpty();
    assertThat(claimRepository.findById(CLAIM_1_ID).orElseThrow().isAmended()).isFalse();

    // The early gate short-circuits before external calls (parent AC3): neither the PDA
    // /schedules call nor the FSP fee-calculation call is made for a stale amendment.
    verifyProviderSchedulesCalled(VerificationTimes.never());
    verifyFeeCalculationCalled(VerificationTimes.never());
  }

  @Test
  @DisplayName("a missing claim version is rejected end-to-end with 400 Bad Request")
  void missingClaimVersionIsRejectedWithBadRequest() throws Exception {
    // Clean PDA response so, if the flow reached external validation, it would not add noise. The
    // early version gate should short-circuit well before that.
    stubProviderSchedulesOk();

    Claim seeded = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    seeded.setStatus(ClaimStatus.VALID);
    claimRepository.saveAndFlush(seeded);

    // Omit the claim version entirely: the NON_NULL patch mapper drops the null field, so the
    // request body carries no version at all - the mandatory-version contract (DSTEW-1751).
    ClaimPatch patch = createBasePatch();
    patch.setVersion(null);
    patch.setFeeCode(AMENDED_FEE_CODE);

    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, patch);

    // 400 Bad Request with the null-version message; no amendment processing.
    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(result.getResponse().getContentAsString()).contains("Claim Version is null");

    // Nothing is written and no external call is made for a missing-version request.
    assertThat(claimAmendmentRepository.findByClaimIdOrderByIdDesc(CLAIM_1_ID)).isEmpty();
    assertThat(claimRepository.findById(CLAIM_1_ID).orElseThrow().isAmended()).isFalse();
    verifyProviderSchedulesCalled(VerificationTimes.never());
    verifyFeeCalculationCalled(VerificationTimes.never());
  }

  @Test
  @DisplayName("a no-op amendment (no field changes) returns 204 and writes no claim_amendment row")
  void noOpAmendmentReturns204AndWritesNoRow() throws Exception {
    // Deliberately do NOT stub the PDA /schedules call: a no-op amendment must short-circuit at the
    // no-change guard, before the PDA/FSP steps run, so no external call is made. A clean 204 here
    // therefore also proves the guard runs early in the pipeline.

    // Put the seeded claim into the amendable state.
    Claim seeded = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    seeded.setStatus(ClaimStatus.VALID);
    Claim savedClaim = claimRepository.saveAndFlush(seeded);

    // Metadata-only patch: carries the required requested-by/reason/user id but changes no field.
    // It carries the claim's current version so it clears the early version gate, leaving the
    // no-change guard (not the version gate) as what halts the flow with a 204.
    ClaimPatch patch = createBasePatch();
    patch.setVersion(savedClaim.getVersion());

    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, patch);

    // A no-op amendment is accepted with 204 No Content - the same success status a genuine
    // amendment returns - and the response body is empty.
    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NO_CONTENT.value());
    assertThat(result.getResponse().getContentAsString()).isEmpty();

    // No phantom history row: nothing was persisted.
    assertThat(claimAmendmentRepository.findByClaimIdOrderByIdDesc(CLAIM_1_ID)).isEmpty();

    // The claim itself is untouched: the amended flag is not set by a no-op.
    Claim afterClaim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    assertThat(afterClaim.isAmended()).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("malformedPatchScenarios")
  @DisplayName("malformed amendment values are handled without returning 500")
  void malformedAmendmentValuesDoNotReturn500(String scenario, Consumer<ObjectNode> mutator)
      throws Exception {

    Claim seeded = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    seeded.setStatus(ClaimStatus.VALID);
    Claim savedClaim = claimRepository.saveAndFlush(seeded);

    ClaimPatch patch = createBasePatch();
    patch.setVersion(savedClaim.getVersion());

    ObjectNode json = (ObjectNode) PATCH_MAPPER.valueToTree(patch);

    mutator.accept(json);

    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, String.valueOf(json));

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());

    assertThat(result.getResponse().getContentAsString()).isNotBlank();
  }

  private static Stream<Arguments> invalidDateScenarios() {

    return DATE_FIELDS.stream()
        .flatMap(
            field ->
                INVALID_DATES.stream()
                    .map(
                        value ->
                            Arguments.of(
                                field + "=" + value,
                                (Consumer<ObjectNode>) json -> json.put(field, value))));
  }

  private static Stream<Arguments> malformedPatchScenarios() {

    Stream<Arguments> strings =
        STRING_FIELDS.stream()
            .map(
                field -> Arguments.of(field, (Consumer<ObjectNode>) json -> json.putObject(field)));

    Stream<Arguments> integers =
        INTEGER_FIELDS.stream()
            .map(
                field ->
                    Arguments.of(
                        field, (Consumer<ObjectNode>) json -> json.put(field, "not-an-integer")));

    Stream<Arguments> booleans =
        BOOLEAN_FIELDS.stream()
            .map(
                field ->
                    Arguments.of(
                        field, (Consumer<ObjectNode>) json -> json.put(field, "definitely")));

    Stream<Arguments> decimals =
        DECIMAL_FIELDS.stream()
            .map(
                field ->
                    Arguments.of(
                        field, (Consumer<ObjectNode>) json -> json.put(field, "not-a-decimal")));

    Stream<Arguments> longs =
        LONG_FIELDS.stream()
            .map(
                field ->
                    Arguments.of(
                        field, (Consumer<ObjectNode>) json -> json.put(field, "not-a-long")));

    Stream<Arguments> uuids =
        UUID_FIELDS.stream()
            .map(
                field ->
                    Arguments.of(
                        field,
                        (Consumer<ObjectNode>) json -> json.put(field, "this-is-not-a-uuid")));

    Stream<Arguments> enums =
        ENUM_FIELDS.stream()
            .map(
                field ->
                    Arguments.of(
                        field,
                        (Consumer<ObjectNode>) json -> json.put(field, "TOTALLY_INVALID_ENUM")));

    return Stream.of(
            strings, integers, booleans, decimals, longs, uuids, enums, invalidDateScenarios())
        .flatMap(Function.identity());
  }
}
