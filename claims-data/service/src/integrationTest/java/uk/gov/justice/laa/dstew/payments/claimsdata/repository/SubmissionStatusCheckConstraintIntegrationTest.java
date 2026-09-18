package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.BULK_SUBMISSION_CREATED_BY_USER_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.MATTER_TYPE_CODE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.OFFICE_ACCOUNT_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.USER_ID;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetails;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Verifies the database-level status {@code CHECK} constraints ({@code chk_submission_status},
 * {@code chk_claim_status} and {@code chk_bulk_submission_status}) that Flyway migration {@code
 * V48} widened to include the {@code VALIDATED_PENDING_APPROVAL} lifecycle status.
 *
 * <p>Two behaviours are proven per table: every legal enum value (including the newly added {@code
 * VALIDATED_PENDING_APPROVAL}) can be persisted, and an unknown status string is rejected.
 * Iterating with {@code @EnumSource} means any future status added to the model is automatically
 * exercised, so a missing matching {@code CHECK}-constraint migration is caught rather than
 * silently skipped.
 *
 * <p>These tests deliberately run without {@code @Transactional}: each repository {@code
 * saveAndFlush} commits in its own transaction, so a rejected write reflects the real database
 * constraint rather than an about-to-be-rolled-back transaction. The unknown-value cases go through
 * a native {@code UPDATE} because the generated enum types cannot represent an illegal value.
 */
@DisplayName("Submission/claim/bulk_submission status CHECK constraint integration test")
class SubmissionStatusCheckConstraintIntegrationTest extends AbstractIntegrationTest {

  private static final String UNKNOWN_STATUS = "NOT_A_REAL_STATUS";

  @Autowired private JdbcTemplate jdbcTemplate;

  @ParameterizedTest
  @EnumSource(SubmissionStatus.class)
  @DisplayName("allowed: every submission status value persists")
  void submissionAcceptsEveryStatus(SubmissionStatus status) {
    UUID id = Uuid7.timeBasedUuid();

    submissionRepository.saveAndFlush(submission(id, status));

    assertThat(submissionRepository.findById(id))
        .as("submission persisted with status %s", status)
        .isPresent()
        .get()
        .extracting(Submission::getStatus)
        .isEqualTo(status);
  }

  @ParameterizedTest
  @EnumSource(ClaimStatus.class)
  @DisplayName("allowed: every claim status value persists")
  void claimAcceptsEveryStatus(ClaimStatus status) {
    UUID submissionId = Uuid7.timeBasedUuid();
    submissionRepository.saveAndFlush(submission(submissionId, SubmissionStatus.CREATED));

    UUID claimId = Uuid7.timeBasedUuid();
    claimRepository.saveAndFlush(claim(claimId, submissionId, status));

    assertThat(claimRepository.findById(claimId))
        .as("claim persisted with status %s", status)
        .isPresent()
        .get()
        .extracting(Claim::getStatus)
        .isEqualTo(status);
  }

  @ParameterizedTest
  @EnumSource(BulkSubmissionStatus.class)
  @DisplayName("allowed: every bulk submission status value persists")
  void bulkSubmissionAcceptsEveryStatus(BulkSubmissionStatus status) {
    UUID id = Uuid7.timeBasedUuid();

    bulkSubmissionRepository.saveAndFlush(bulkSubmission(id, status));

    assertThat(bulkSubmissionRepository.findById(id))
        .as("bulk submission persisted with status %s", status)
        .isPresent()
        .get()
        .extracting(BulkSubmission::getStatus)
        .isEqualTo(status);
  }

  @Test
  @DisplayName("rejected: an unknown submission status violates chk_submission_status")
  void submissionRejectsUnknownStatus() {
    UUID id = Uuid7.timeBasedUuid();
    submissionRepository.saveAndFlush(submission(id, SubmissionStatus.VALIDATED_PENDING_APPROVAL));

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE claims.submission SET status = ? WHERE id = ?", UNKNOWN_STATUS, id))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("rejected: an unknown claim status violates chk_claim_status")
  void claimRejectsUnknownStatus() {
    UUID submissionId = Uuid7.timeBasedUuid();
    submissionRepository.saveAndFlush(submission(submissionId, SubmissionStatus.CREATED));
    UUID claimId = Uuid7.timeBasedUuid();
    claimRepository.saveAndFlush(
        claim(claimId, submissionId, ClaimStatus.VALIDATED_PENDING_APPROVAL));

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE claims.claim SET status = ? WHERE id = ?", UNKNOWN_STATUS, claimId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("rejected: an unknown bulk submission status violates chk_bulk_submission_status")
  void bulkSubmissionRejectsUnknownStatus() {
    UUID id = Uuid7.timeBasedUuid();
    bulkSubmissionRepository.saveAndFlush(
        bulkSubmission(id, BulkSubmissionStatus.VALIDATED_PENDING_APPROVAL));

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE claims.bulk_submission SET status = ? WHERE id = ?",
                    UNKNOWN_STATUS,
                    id))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private Submission submission(UUID id, SubmissionStatus status) {
    return Submission.builder()
        .id(id)
        .bulkSubmissionId(null)
        .officeAccountNumber(OFFICE_ACCOUNT_NUMBER)
        // A valid MON-YYYY period is required because the entity's submissionPeriodSortKey
        // @Formula runs TO_DATE(submission_period, 'MON-YYYY') on every read. Each parameterized
        // case runs against a freshly cleaned database, so a shared period never trips the
        // uq_submission_live_office_aol_period index this test is not concerned with.
        .submissionPeriod("APR-2025")
        .areaOfLaw(AreaOfLaw.CRIME_LOWER)
        .status(status)
        .createdByUserId(USER_ID)
        .providerUserId(USER_ID)
        .createdOn(CREATED_ON)
        .build();
  }

  private Claim claim(UUID id, UUID submissionId, ClaimStatus status) {
    return Claim.builder()
        .id(id)
        .submission(submissionRepository.getReferenceById(submissionId))
        .status(status)
        .lineNumber(1)
        .caseReferenceNumber("STATUS-CRN-" + id)
        .matterTypeCode(MATTER_TYPE_CODE)
        .createdByUserId(USER_ID)
        .createdOn(CREATED_ON)
        .build();
  }

  private BulkSubmission bulkSubmission(UUID id, BulkSubmissionStatus status) {
    return BulkSubmission.builder()
        .id(id)
        .data(new GetBulkSubmission200ResponseDetails())
        .status(status)
        .createdByUserId(BULK_SUBMISSION_CREATED_BY_USER_ID)
        .createdOn(CREATED_ON)
        .updatedOn(CREATED_ON)
        .build();
  }
}
