package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.OFFICE_ACCOUNT_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.USER_ID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Verifies the database-level partial unique index {@code uq_submission_live_office_aol_period}
 * (Flyway {@code V46}) is the race-safe back-stop for the "one live submission per office, area of
 * law and period" rule, exercised here specifically for the {@code VALIDATED_PENDING_APPROVAL}
 * status added in {@code V48}. Because that status is not one of the superseded statuses ({@code
 * VALIDATION_FAILED}, {@code REPLACED}), a submission holding it is "live" and must block a second
 * live submission for the same key.
 *
 * <p>These tests write directly through the repository, deliberately bypassing {@code
 * SubmissionService}'s application-level fail-fast pre-check, so it is the database index alone
 * that is under test. They run without {@code @Transactional} so each {@code saveAndFlush} commits
 * in its own transaction and the rejection reflects real persisted state.
 */
@DisplayName("Live submission unique (office, area_of_law, period) constraint integration test")
class SubmissionLiveUniqueConstraintIntegrationTest extends AbstractIntegrationTest {

  private static final String INDEX_NAME = "uq_submission_live_office_aol_period";
  private static final String PERIOD = "APR-2025";

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("migration verification: the partial unique index exists")
  void constraintExists() {
    Integer indexCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_indexes WHERE indexname = ?", Integer.class, INDEX_NAME);

    assertThat(indexCount).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "rejected: a new live submission when a VALIDATED_PENDING_APPROVAL submission shares the "
          + "office, area of law and period")
  void validatedPendingApprovalBlocksNewLiveSubmission() {
    submissionRepository.saveAndFlush(liveSubmission(SubmissionStatus.VALIDATED_PENDING_APPROVAL));

    Submission duplicate = liveSubmission(SubmissionStatus.CREATED);

    assertThatThrownBy(() -> submissionRepository.saveAndFlush(duplicate))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(submissionRepository.findAll())
        .as("only the original VALIDATED_PENDING_APPROVAL submission survives")
        .hasSize(1)
        .first()
        .extracting(Submission::getStatus)
        .isEqualTo(SubmissionStatus.VALIDATED_PENDING_APPROVAL);
  }

  @Test
  @DisplayName(
      "allowed: a new live submission when the existing VALIDATED_PENDING_APPROVAL submission "
          + "differs on period")
  void validatedPendingApprovalWithDifferentPeriodIsAllowed() {
    submissionRepository.saveAndFlush(liveSubmission(SubmissionStatus.VALIDATED_PENDING_APPROVAL));

    Submission differentPeriod = liveSubmission(SubmissionStatus.CREATED);
    differentPeriod.setSubmissionPeriod("MAY-2025");

    submissionRepository.saveAndFlush(differentPeriod);

    assertThat(submissionRepository.findAll()).hasSize(2);
  }

  private Submission liveSubmission(SubmissionStatus status) {
    return Submission.builder()
        .id(Uuid7.timeBasedUuid())
        .bulkSubmissionId(null)
        .officeAccountNumber(OFFICE_ACCOUNT_NUMBER)
        .submissionPeriod(PERIOD)
        .areaOfLaw(AreaOfLaw.CRIME_LOWER)
        .status(status)
        // The index only covers rows created after the ${live_submission_uniqueness_cutoff}
        // placeholder, which defaults to the epoch locally, so any modern timestamp is in scope.
        .createdOn(CREATED_ON)
        .createdByUserId(USER_ID)
        .providerUserId(USER_ID)
        .build();
  }
}
