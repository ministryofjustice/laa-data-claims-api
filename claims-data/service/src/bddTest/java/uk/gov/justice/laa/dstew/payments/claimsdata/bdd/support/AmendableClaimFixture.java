package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.support;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Fluent test-fixture builder that seeds the minimum entity graph an amendment BDD scenario needs
 * to reach {@code PATCH /api/v1/submissions/{submissionId}/claims/{claimId}} and drive it to a
 * commit-visible outcome:
 *
 * <ol>
 *   <li>a {@link Submission} (LEGAL_HELP / CREATED / provider actor),
 *   <li>a {@link Claim} on that submission (VALID, feeCode / hasAssessment / version configurable),
 *   <li>a {@link ClaimSummaryFee} parent row (required as an FK target for the {@link
 *       CalculatedFeeDetail} baseline),
 *   <li>a {@link CalculatedFeeDetail} baseline row bound to the claim — the "before state" that
 *       {@code ClaimAmendmentStateService.retrieveAmendmentState} reads from {@link
 *       CalculatedFeeDetailRepository#findFirstByClaimIdOrderByCreatedOnDescIdDesc(UUID)}.
 * </ol>
 *
 * <p>Optionally the builder can also seed a second {@link Claim} on the same submission with a
 * matching UCN/UFN pair, for duplicate-check scenarios (DSTEW-1769).
 *
 * <p><b>Usage</b>
 *
 * <pre>{@code
 * // In a step class, autowire the builder and:
 * AmendableClaimFixture.Seeded seeded =
 *     fixture.legalHelpValid()
 *            .withVersion(3)
 *            .withFeeCode("CAPA")
 *            .withAssessment()
 *            .seed();
 *
 * UUID submissionId = seeded.submissionId();
 * UUID claimId      = seeded.claimId();
 * long baseline     = seeded.baselineVersion();
 * }</pre>
 *
 * <p>Ticket: DSTEW-2301.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AmendableClaimFixture {

  private static final String SEED_ACTOR = "bdd-DSTEW-2301";
  private static final String DEFAULT_OFFICE = "0U099L";
  private static final String DEFAULT_FEE_CODE = "CAPA";
  // NOTE: UCN is intentionally NOT stored here. The Claim entity does not carry a UCN field —
  // UCNs live on the linked Client entity. When DSTEW-1769 needs UCN-based duplicate scenarios
  // it will add UCN handling via Client seeding, not via a shared constant on this class.
  private static final String DEFAULT_UFN = "010725/001";
  private static final String DEFAULT_CASE_REF = "CRN-2301";

  private final SubmissionRepository submissionRepository;
  private final ClaimRepository claimRepository;
  private final ClaimSummaryFeeRepository claimSummaryFeeRepository;
  private final CalculatedFeeDetailRepository calculatedFeeDetailRepository;

  /**
   * Seeds a fresh submission + claim + summary-fee + baseline CalculatedFeeDetail using default
   * values. Returns a chainable {@link Builder} so callers can layer on customisations before
   * calling {@link Builder#seed()}.
   */
  public Builder legalHelpValid() {
    return new Builder()
        .withAreaOfLaw(AreaOfLaw.LEGAL_HELP)
        .withFeeCode(DEFAULT_FEE_CODE)
        .withStatus(ClaimStatus.VALID)
        .withVersion(0)
        .withUfn(DEFAULT_UFN);
  }

  /** Immutable-style fluent builder. */
  public class Builder {

    private AreaOfLaw areaOfLaw = AreaOfLaw.LEGAL_HELP;
    private String feeCode = DEFAULT_FEE_CODE;
    private ClaimStatus status = ClaimStatus.VALID;
    private long targetVersion = 0L;
    private boolean assessment = false;
    private String ufn = DEFAULT_UFN;
    private String duplicateSiblingUfn;

    public Builder withAreaOfLaw(AreaOfLaw value) {
      this.areaOfLaw = value;
      return this;
    }

    public Builder withFeeCode(String value) {
      this.feeCode = value;
      return this;
    }

    public Builder withStatus(ClaimStatus value) {
      this.status = value;
      return this;
    }

    /**
     * Target {@code claim.version} after seeding. Values &gt; 0 are achieved by re-saving the claim
     * that many times so JPA's {@code @Version} auto-increments to the requested target.
     */
    public Builder withVersion(long value) {
      if (value < 0) {
        throw new IllegalArgumentException("version must be >= 0");
      }
      this.targetVersion = value;
      return this;
    }

    public Builder withAssessment() {
      this.assessment = true;
      return this;
    }

    public Builder withUfn(String value) {
      this.ufn = value;
      return this;
    }

    /**
     * Seeds a second claim on the same submission with the given UFN — used by duplicate-check
     * scenarios (DSTEW-1769). The sibling claim is fully independent of the primary claim (its own
     * summary-fee + baseline CFD are NOT seeded, since duplicate detection only needs the claim row
     * itself). Client-UCN handling is out of scope for T2 and will be added when DSTEW-1769 needs
     * it — the {@code Claim} entity does not carry a UCN field; it lives on the linked {@code
     * Client} entity.
     */
    public Builder withDuplicateSibling(String siblingUfn) {
      this.duplicateSiblingUfn = siblingUfn;
      return this;
    }

    /** Executes the seed. */
    @Transactional
    public Seeded seed() {
      Submission submission = seedSubmission(areaOfLaw);
      Claim claim = seedClaim(submission, feeCode, status, assessment, ufn);
      seedClaimSummaryFeeAndBaselineCfd(claim, feeCode);

      if (targetVersion > 0) {
        claim = advanceClaimVersion(claim, targetVersion);
      }

      if (duplicateSiblingUfn != null) {
        seedClaim(submission, feeCode, ClaimStatus.VALID, false, duplicateSiblingUfn);
      }

      log.info(
          "[DSTEW-2301] Seeded amendable claim {} on submission {} at version {}",
          claim.getId(),
          submission.getId(),
          claim.getVersion());
      return new Seeded(submission.getId(), claim.getId(), claim.getVersion());
    }
  }

  // ---------------------------------------------------------------------------
  // Seed helpers (package-private so a future extension can reuse them)
  // ---------------------------------------------------------------------------

  Submission seedSubmission(AreaOfLaw areaOfLaw) {
    return submissionRepository.saveAndFlush(
        Submission.builder()
            .id(Uuid7.timeBasedUuid())
            .officeAccountNumber(DEFAULT_OFFICE)
            .submissionPeriod(nextSubmissionPeriod())
            .areaOfLaw(areaOfLaw)
            .status(SubmissionStatus.CREATED)
            .createdByUserId(SEED_ACTOR)
            .providerUserId(SEED_ACTOR)
            .createdOn(Instant.now())
            .build());
  }

  Claim seedClaim(
      Submission submission, String feeCode, ClaimStatus status, boolean assessment, String ufn) {
    return claimRepository.saveAndFlush(
        Claim.builder()
            .id(Uuid7.timeBasedUuid())
            .submission(submission)
            .status(status)
            .feeCode(feeCode)
            .lineNumber(1)
            .matterTypeCode("MAT01")
            .uniqueFileNumber(ufn)
            .caseReferenceNumber(DEFAULT_CASE_REF)
            .caseStartDate(LocalDate.of(2025, Month.JULY, 1))
            .caseConcludedDate(LocalDate.of(2025, Month.JULY, 31))
            .hasAssessment(assessment)
            .createdByUserId(SEED_ACTOR)
            .build());
  }

  /**
   * Seeds a {@link ClaimSummaryFee} + a baseline {@link CalculatedFeeDetail} for the claim. The
   * summary-fee row is the FK target the CFD requires. The baseline CFD is what {@code
   * ClaimAmendmentStateService} reads to build the before-state snapshot on the amendment PATCH
   * path.
   */
  void seedClaimSummaryFeeAndBaselineCfd(Claim claim, String feeCode) {
    ClaimSummaryFee summaryFee =
        claimSummaryFeeRepository.saveAndFlush(
            ClaimSummaryFee.builder()
                .id(Uuid7.timeBasedUuid())
                .claim(claim)
                .createdByUserId(SEED_ACTOR)
                .createdOn(Instant.now())
                .build());

    calculatedFeeDetailRepository.saveAndFlush(
        CalculatedFeeDetail.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(claim)
            .claimSummaryFee(summaryFee)
            .feeCode(feeCode)
            .createdByUserId(SEED_ACTOR)
            .createdOn(Instant.now())
            .build());
  }

  /**
   * Bumps {@code claim.version} to the target by repeatedly re-saving the claim. Each {@code
   * saveAndFlush} bumps the JPA {@code @Version} by 1 — there is no legal path to set the version
   * directly.
   */
  private Claim advanceClaimVersion(Claim claim, long targetVersion) {
    Claim current = claim;
    while (current.getVersion() == null || current.getVersion() < targetVersion) {
      current.setUpdatedByUserId(SEED_ACTOR);
      current = claimRepository.saveAndFlush(current);
    }
    return current;
  }

  private String nextSubmissionPeriod() {
    // MMM-uuuu format. Any past-but-plausible month works for the harness; per-scenario uniqueness
    // isn't required because the fixture always seeds a fresh (submission_id, office) pair.
    return "JUL-2025";
  }

  /** Result of {@link Builder#seed()}. Records the IDs the caller needs to drive the PATCH call. */
  public record Seeded(UUID submissionId, UUID claimId, Long baselineVersion) {}
}
