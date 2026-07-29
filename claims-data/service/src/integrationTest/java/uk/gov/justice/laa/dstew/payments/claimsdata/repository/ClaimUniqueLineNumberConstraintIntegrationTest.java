package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.MATTER_TYPE_CODE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_2_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.USER_ID;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Verifies the database-level unique constraint {@code uq_claim_submission_line_number} added in
 * Flyway migration {@code V44}, which enforces the business rule that a claim's {@code line_number}
 * must be unique within a submission.
 *
 * <p>These tests deliberately run without {@code @Transactional}: each repository {@code
 * saveAndFlush} commits in its own transaction, so the database-state assertions after a rejected
 * duplicate reflect what is actually persisted rather than an about-to-be-rolled-back transaction.
 */
@DisplayName("Claim unique (submission_id, line_number) constraint integration test")
class ClaimUniqueLineNumberConstraintIntegrationTest extends AbstractIntegrationTest {

  private static final String CONSTRAINT_NAME = "uq_claim_submission_line_number";

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void seedSubmissions() {
    // Seeds the bulk submission plus submission1 (office 1) and submission2 (office 2); no claims.
    seedSubmissionsData();
  }

  @Test
  @DisplayName(
      "migration verification: the unique constraint exists on (submission_id, line_number)")
  void constraintExistsOnSubmissionIdAndLineNumber() {
    Integer constraintCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_constraint WHERE conname = ?", Integer.class, CONSTRAINT_NAME);
    assertThat(constraintCount).isEqualTo(1);

    // Confirm the constraint covers exactly submission_id and line_number, in order.
    String columns =
        jdbcTemplate.queryForObject(
            """
            SELECT string_agg(a.attname, ',' ORDER BY k.ord)
            FROM pg_constraint c
            JOIN unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord) ON TRUE
            JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum
            WHERE c.conname = ? AND c.contype = 'u'
            """,
            String.class,
            CONSTRAINT_NAME);
    assertThat(columns).isEqualTo("submission_id,line_number");
  }

  @Test
  @DisplayName("allowed: same submission with different line numbers persists (A/1, A/2)")
  void sameSubmissionDifferentLineNumbersIsAllowed() {
    claimRepository.saveAndFlush(claim(SUBMISSION_1_ID, 1));
    claimRepository.saveAndFlush(claim(SUBMISSION_1_ID, 2));

    assertThat(claimRepository.findBySubmissionId(SUBMISSION_1_ID)).hasSize(2);
  }

  @Test
  @DisplayName("allowed: different submissions with the same line number persists (A/1, B/1)")
  void differentSubmissionsSameLineNumberIsAllowed() {
    claimRepository.saveAndFlush(claim(SUBMISSION_1_ID, 1));
    claimRepository.saveAndFlush(claim(SUBMISSION_2_ID, 1));

    assertThat(claimRepository.findBySubmissionId(SUBMISSION_1_ID)).hasSize(1);
    assertThat(claimRepository.findBySubmissionId(SUBMISSION_2_ID)).hasSize(1);
  }

  @Test
  @DisplayName(
      "rejected: same submission and line number fails with a uniqueness violation (A/1, A/1)")
  void sameSubmissionSameLineNumberIsRejected() {
    claimRepository.saveAndFlush(claim(SUBMISSION_1_ID, 1));

    Claim duplicate = claim(SUBMISSION_1_ID, 1);

    assertThatThrownBy(() -> claimRepository.saveAndFlush(duplicate))
        .isInstanceOf(DataIntegrityViolationException.class);

    // Database state after the rejected duplicate: only the first row survives.
    assertThat(claimRepository.findBySubmissionId(SUBMISSION_1_ID)).hasSize(1);
  }

  private Claim claim(UUID submissionId, int lineNumber) {
    return Claim.builder()
        .id(Uuid7.timeBasedUuid())
        .submission(submissionRepository.getReferenceById(submissionId))
        .status(ClaimStatus.READY_TO_PROCESS)
        .lineNumber(lineNumber)
        .caseReferenceNumber("UQ-CRN-" + lineNumber)
        .matterTypeCode(MATTER_TYPE_CODE)
        .createdByUserId(USER_ID)
        .createdOn(CREATED_ON)
        .build();
  }
}
