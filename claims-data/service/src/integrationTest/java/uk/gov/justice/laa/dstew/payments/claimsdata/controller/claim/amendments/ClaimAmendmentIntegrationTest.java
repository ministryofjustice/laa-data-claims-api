package uk.gov.justice.laa.dstew.payments.claimsdata.controller.claim.amendments;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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

  @Test
  @DisplayName("a valid amendment commits and updates the claim_amendment, claim and client tables")
  void validAmendmentCommitsAndUpdatesAllTables() throws Exception {
    // Clean PDA response so external validation contributes no errors and the amendment commits.
    stubProviderSchedulesOk();

    // Put the seeded claim into the amendable state.
    Claim seeded = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    seeded.setStatus(ClaimStatus.VALID);
    String originalFeeCode = seeded.getFeeCode();
    claimRepository.saveAndFlush(seeded);

    // Amend a claim-level field (fee code) and the client name in one request.
    ClaimPatch patch = metadataPatch();
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

    // client: the amended client name is applied, proving the client table is affected.
    Client amendedClient = clientRepository.findByClaimId(CLAIM_1_ID).orElseThrow();
    assertThat(amendedClient.getClientForename()).isEqualTo(AMENDED_CLIENT_FORENAME);
    assertThat(amendedClient.getClientSurname()).isEqualTo(AMENDED_CLIENT_SURNAME);
  }
}
