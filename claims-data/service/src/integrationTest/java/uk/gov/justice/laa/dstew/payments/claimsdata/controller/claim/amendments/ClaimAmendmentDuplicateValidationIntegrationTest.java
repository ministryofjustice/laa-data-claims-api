package uk.gov.justice.laa.dstew.payments.claimsdata.controller.claim.amendments;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.FEE_CODE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.UNIQUE_FILE_NUMBER;

import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.verify.VerificationTimes;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Amendment-focused duplicate-validation integration tests (DSTEW-1769).
 *
 * <p>Drives the real {@code PATCH /api/v1/submissions/{submissionId}/claims/{claimId}} endpoint and
 * proves that when a VALID claim is amended so its <em>post-amendment merged state</em> collides
 * with a claim in another submission, the shared claims-validation-core {@code
 * CLAIM_DUPLICATE_CLAIM} validator rejects the amendment (HTTP 400), returns {@code
 * INVALID_CLAIM_HAS_DUPLICATE_IN_ANOTHER_SUBMISSION} and nothing is persisted (full rollback). The
 * negative cases (self-exclusion, VOID/ineligible-status comparison, differing key) prove the
 * amendment instead commits (HTTP 204).
 *
 * <p><b>Trigger mechanism.</b> The duplicate comparison set is read from the database by {@code
 * RepositoryClaimsDataProvider}, so duplicates are created by <b>DB seeding</b> a claim in a prior
 * (validation-succeeded) submission for the same office - not by a MockServer stub. The richly
 * seeded {@code CLAIM_1} (Legal Help, office {@code OFICE1}) is used as the target because it is
 * otherwise valid enough to commit, so the duplicate is the only validation error surfaced.
 *
 * <p><b>PDA independence.</b> The PDA {@code /schedules} call is only made for PDA-impacting field
 * changes. A UCN amendment is non-PDA, so those tests additionally assert zero outbound PDA calls,
 * proving duplicate checking runs independently of PDA validation.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("Amendment duplicate-validation integration test")
class ClaimAmendmentDuplicateValidationIntegrationTest
    extends AbstractAmendmentPatchIntegrationTest {

  private static final String CREATED_BY = "amendment-duplicate-integration-test";

  private static final String DUPLICATE_IN_ANOTHER_SUBMISSION =
      "INVALID_CLAIM_HAS_DUPLICATE_IN_ANOTHER_SUBMISSION";
  private static final String DUPLICATE_IN_SAME_SUBMISSION =
      "INVALID_CLAIM_HAS_DUPLICATE_IN_SAME_SUBMISSION";

  // A UCN distinct from CLAIM_1's seeded UCN (SEEDED_UNIQUE_CLIENT_NUMBER = 01011990/A/BCDE).
  private static final String OTHER_UCN = "02021990/B/CDEF";
  private static final String PRIOR_PERIOD = "DEC-2024";

  // ===========================================================================
  // Duplicate created by an amendment => rejected (400) and nothing persisted.
  // ===========================================================================

  @Test
  @DisplayName(
      "UCN amend into a prior-submission twin is rejected as a cross-submission duplicate "
          + "(PDA-independent) and rolled back")
  void ucnAmendCreatingCrossSubmissionDuplicateIsRejectedAndRolledBack() throws Exception {
    final Long originalVersion = amendableClaim1().getVersion();

    // Prior (validation-succeeded) submission for the same office with a claim matching CLAIM_1 on
    // feeCode + UFN and carrying the UCN we are about to amend to. Before the amendment the UCNs
    // differ, so there is no pre-existing duplicate; the amendment creates the collision.
    seedPriorDuplicateClaim(
        b -> b.feeCode(FEE_CODE).uniqueFileNumber(UNIQUE_FILE_NUMBER), OTHER_UCN);

    ClaimPatch patch = metadataPatch();
    patch.setUniqueClientNumber(OTHER_UCN);

    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, patch);

    // UCN is non-PDA: duplicate checking must run without any Provider Details call.
    verifyProviderSchedulesCalled(VerificationTimes.exactly(0));
    assertRejectedAsDuplicate(result, DUPLICATE_IN_ANOTHER_SUBMISSION);
    assertNothingPersisted(originalVersion);
  }

  @Test
  @DisplayName(
      "feeCode amend into a prior-submission twin is rejected as a cross-submission duplicate")
  void feeCodeAmendCreatingCrossSubmissionDuplicateIsRejected() throws Exception {
    // A fee-code change is PDA-impacting, so stub a clean schedules response.
    stubProviderSchedulesOk();

    final Long originalVersion = amendableClaim1().getVersion();

    // Prior twin matching CLAIM_1's post-amendment feeCode plus its current UFN + UCN.
    seedPriorDuplicateClaim(
        b -> b.feeCode("NEWFEE").uniqueFileNumber(UNIQUE_FILE_NUMBER), SEEDED_UNIQUE_CLIENT_NUMBER);

    ClaimPatch patch = metadataPatch();
    patch.setFeeCode("NEWFEE");

    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, patch);

    verifyProviderSchedulesCalled(VerificationTimes.atLeast(1));
    assertRejectedAsDuplicate(result, DUPLICATE_IN_ANOTHER_SUBMISSION);
    assertNothingPersisted(originalVersion);
  }

  @Test
  @DisplayName("UFN amend into a prior-submission twin is rejected as a cross-submission duplicate")
  void ufnAmendCreatingCrossSubmissionDuplicateIsRejected() throws Exception {
    // Defensively stub the PDA call (harmless if the UFN change is not PDA-impacting).
    stubProviderSchedulesOk();

    final Long originalVersion = amendableClaim1().getVersion();

    // Prior twin matching CLAIM_1's post-amendment UFN plus its current feeCode + UCN.
    seedPriorDuplicateClaim(
        b -> b.feeCode(FEE_CODE).uniqueFileNumber("020225/002"), SEEDED_UNIQUE_CLIENT_NUMBER);

    ClaimPatch patch = metadataPatch();
    patch.setUniqueFileNumber("020225/002");

    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, patch);

    assertRejectedAsDuplicate(result, DUPLICATE_IN_ANOTHER_SUBMISSION);
    assertNothingPersisted(originalVersion);
  }

  @Test
  @DisplayName(
      "within-submission duplicate: UCN amend into a sibling in the same submission is rejected "
          + "as a same-submission duplicate")
  void ucnAmendCreatingWithinSubmissionDuplicateIsRejected() throws Exception {
    final Long originalVersion = amendableClaim1().getVersion();

    // Mark the current submission validation-succeeded so its claims are eligible comparison rows,
    // then seed a sibling in it matching CLAIM_1 on feeCode + UFN with the UCN we amend to.
    Submission current = submissionRepository.findById(SUBMISSION_1_ID).orElseThrow();
    current.setStatus(SubmissionStatus.VALIDATION_SUCCEEDED);
    submissionRepository.saveAndFlush(current);

    UUID siblingId =
        seedComparisonClaim(
            SUBMISSION_1_ID,
            ClaimStatus.VALID,
            b -> b.feeCode(FEE_CODE).uniqueFileNumber(UNIQUE_FILE_NUMBER));
    seedClient(siblingId, OTHER_UCN, "Dup", "Licate");

    ClaimPatch patch = metadataPatch();
    patch.setUniqueClientNumber(OTHER_UCN);

    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, patch);

    verifyProviderSchedulesCalled(VerificationTimes.exactly(0));
    assertRejectedAsDuplicate(result, DUPLICATE_IN_SAME_SUBMISSION);
    assertNothingPersisted(originalVersion);
  }

  // ===========================================================================
  // No duplicate created => amendment commits (204) and is persisted.
  // ===========================================================================

  @Test
  @DisplayName(
      "self-exclusion: a neutral amend with no other matching claim commits (the claim is never a "
          + "duplicate of itself)")
  void neutralAmendWithNoOtherClaimCommits() throws Exception {
    final Long originalVersion = amendableClaim1().getVersion();

    // No comparison claim seeded: the only row matching CLAIM_1's keys is its own persisted row, so
    // a correct self-exclusion must let this neutral (non-key) amendment commit.
    ClaimPatch patch = metadataPatch();
    patch.setClientSurname("Self-Exclusion");

    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, patch);

    verifyProviderSchedulesCalled(VerificationTimes.exactly(0));
    assertCommitted(result, originalVersion);
  }

  @Test
  @DisplayName("a VOID prior-submission twin does not make the amended claim a duplicate")
  void voidPriorTwinIsIgnored() throws Exception {
    amendableClaim1();

    // Prior twin matches on every key but is VOID, so it must not participate in duplicate
    // matching.
    seedPriorDuplicateClaimWithStatus(
        ClaimStatus.VOID, b -> b.feeCode(FEE_CODE).uniqueFileNumber(UNIQUE_FILE_NUMBER), OTHER_UCN);

    ClaimPatch patch = metadataPatch();
    patch.setUniqueClientNumber(OTHER_UCN);

    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, patch);

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NO_CONTENT.value());
  }

  @Test
  @DisplayName(
      "an INVALID-status prior-submission twin does not make the amended claim a duplicate")
  void ineligibleStatusPriorTwinIsIgnored() throws Exception {
    amendableClaim1();

    // Prior twin matches on every key but is INVALID (not VALID/READY_TO_PROCESS), so it is
    // ignored.
    seedPriorDuplicateClaimWithStatus(
        ClaimStatus.INVALID,
        b -> b.feeCode(FEE_CODE).uniqueFileNumber(UNIQUE_FILE_NUMBER),
        OTHER_UCN);

    ClaimPatch patch = metadataPatch();
    patch.setUniqueClientNumber(OTHER_UCN);

    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, patch);

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NO_CONTENT.value());
  }

  @Test
  @DisplayName("a prior-submission claim with a different UFN is not a duplicate")
  void differentUfnPriorClaimIsNotDuplicate() throws Exception {
    amendableClaim1();

    // Prior claim shares feeCode + UCN but a different UFN, so amending UCN does not create a
    // match.
    seedPriorDuplicateClaim(b -> b.feeCode(FEE_CODE).uniqueFileNumber("999999/999"), OTHER_UCN);

    ClaimPatch patch = metadataPatch();
    patch.setUniqueClientNumber(OTHER_UCN);

    MvcResult result = performPatch(SUBMISSION_1_ID, CLAIM_1_ID, patch);

    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NO_CONTENT.value());
  }

  // ---------------------------------------------------------------------------
  // Fixtures / assertions
  // ---------------------------------------------------------------------------

  /** Puts the richly-seeded {@code CLAIM_1} into the amendable (VALID) state and returns it. */
  private Claim amendableClaim1() {
    Claim claim = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    claim.setStatus(ClaimStatus.VALID);
    return claimRepository.saveAndFlush(claim);
  }

  /**
   * Seeds a VALID claim (with a Client carrying {@code ucn}) in a prior, validation-succeeded
   * submission for CLAIM_1's office, so it is an eligible cross-submission comparison row.
   */
  private void seedPriorDuplicateClaim(Consumer<Claim.ClaimBuilder> keys, String ucn) {
    seedPriorDuplicateClaimWithStatus(ClaimStatus.VALID, keys, ucn);
  }

  private void seedPriorDuplicateClaimWithStatus(
      ClaimStatus status, Consumer<Claim.ClaimBuilder> keys, String ucn) {
    UUID priorSubmission =
        createSubmissionWithStatus(
            OFFICE_ACCOUNT_NUMBER_1,
            AreaOfLaw.LEGAL_HELP,
            PRIOR_PERIOD,
            SubmissionStatus.VALIDATION_SUCCEEDED);
    UUID priorClaim = seedComparisonClaim(priorSubmission, status, keys);
    seedClient(priorClaim, ucn, "Dup", "Licate");
  }

  private UUID createSubmissionWithStatus(
      String office, AreaOfLaw areaOfLaw, String period, SubmissionStatus status) {
    UUID id = Uuid7.timeBasedUuid();
    Submission submission =
        Submission.builder()
            .id(id)
            .bulkSubmissionId(bulkSubmission.getId())
            .officeAccountNumber(office)
            .submissionPeriod(period)
            .areaOfLaw(areaOfLaw)
            .status(status)
            .createdByUserId(CREATED_BY)
            .providerUserId(bulkSubmission.getCreatedByUserId())
            .numberOfClaims(0)
            .createdOn(CREATED_ON)
            .build();
    return submissionRepository.saveAndFlush(submission).getId();
  }

  private UUID seedComparisonClaim(
      UUID submissionId, ClaimStatus status, Consumer<Claim.ClaimBuilder> keys) {
    Claim.ClaimBuilder builder =
        Claim.builder()
            .id(Uuid7.timeBasedUuid())
            .submission(submissionRepository.getReferenceById(submissionId))
            .status(status)
            .lineNumber(99)
            .caseReferenceNumber("CMP-CRN")
            .matterTypeCode("MTC")
            .createdByUserId(CREATED_BY)
            .createdOn(CREATED_ON);
    keys.accept(builder);
    return claimRepository.saveAndFlush(builder.build()).getId();
  }

  private void seedClient(UUID claimId, String ucn, String forename, String surname) {
    Client client =
        Client.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(claimRepository.getReferenceById(claimId))
            .clientForename(forename)
            .clientSurname(surname)
            .uniqueClientNumber(ucn)
            .createdByUserId(CREATED_BY)
            .createdOn(CREATED_ON)
            .build();
    clientRepository.saveAndFlush(client);
  }

  /** Asserts a 400 whose body carries the given duplicate code. */
  private void assertRejectedAsDuplicate(MvcResult result, String expectedCode) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(result.getResponse().getContentAsString())
        .as("amendment response should contain %s", expectedCode)
        .contains(expectedCode);
  }

  /** Asserts the rejected amendment left no trace: no audit row, flag/version unchanged. */
  private void assertNothingPersisted(Long originalVersion) {
    assertThat(claimAmendmentRepository.findByClaimIdOrderByIdDesc(CLAIM_1_ID)).isEmpty();
    Claim after = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    assertThat(after.isAmended()).isFalse();
    assertThat(after.getVersion()).isEqualTo(originalVersion);
  }

  /** Asserts a committed (204) amendment: an audit row exists and the claim is marked amended. */
  private void assertCommitted(MvcResult result, Long originalVersion) {
    assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NO_CONTENT.value());
    assertThat(claimAmendmentRepository.findByClaimIdOrderByIdDesc(CLAIM_1_ID)).hasSize(1);
    Claim after = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    assertThat(after.isAmended()).isTrue();
    assertThat(after.getVersion()).isGreaterThan(originalVersion);
  }
}
