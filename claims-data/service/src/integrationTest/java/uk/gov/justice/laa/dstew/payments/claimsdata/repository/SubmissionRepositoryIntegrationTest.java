package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AREA_OF_LAW;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.BULK_SUBMISSION_CREATED_BY_USER_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.BULK_SUBMISSION_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_2_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_3_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_STATUSES;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.USER_ID;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetails;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.specification.SubmissionSpecification;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.SubmissionService;

/**
 * This contains integration tests to verify the filtering logic implemented in the {@link
 * SubmissionSpecification} and used by the {@link SubmissionRepository}.
 */
@Slf4j
@Isolated
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("SubmissionRepository Integration Test")
public class SubmissionRepositoryIntegrationTest extends AbstractIntegrationTest {

  private static final Instant FIRST_JANUARY_2025 =
      LocalDate.of(2025, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
  private static final Instant TENTH_APRIL_2024 =
      LocalDate.of(2024, 4, 10).atStartOfDay().toInstant(ZoneOffset.UTC);
  private static final Instant ELEVENTH_APRIL_2024 =
      LocalDate.of(2024, 4, 11).atStartOfDay().toInstant(ZoneOffset.UTC);
  private static final String[] IGNORED_FIELDS = {
    "updatedOn", "officeAccountNumberSortKey", "submissionPeriodSortKey"
  };

  @Autowired private JdbcTemplate jdbcTemplate;

  private Submission submission1;
  private Submission submission2;

  @AfterEach
  public void cleanupInvalidSubmission() {
    // Use a native SQL delete to remove any submission inserted with an invalid period,
    // bypassing JPA so the @Formula is never evaluated during cleanup. This runs before
    // the next @BeforeEach abstractSetup() calls deleteAll(). Safe to run after every test
    // as the DELETE is a no-op when the row doesn't exist.
    jdbcTemplate.update("DELETE FROM claims.submission WHERE id = ?", SUBMISSION_3_ID);
  }

  /**
   * This is to set the testing data such as the bulk submission and the corresponding submissions
   * which will be saved in the shared test container's database for the execution of the
   * integration tests. This callback method gets executed before every test method. This will
   * ensure that each test runs with an empty and clean database and circumvent any kind of test
   * pollution.
   */
  @BeforeEach
  public void setup() {

    var bulkSubmission =
        BulkSubmission.builder()
            .id(BULK_SUBMISSION_ID)
            .data(new GetBulkSubmission200ResponseDetails())
            .status(BulkSubmissionStatus.READY_FOR_PARSING)
            .createdByUserId(BULK_SUBMISSION_CREATED_BY_USER_ID)
            .createdOn(TENTH_APRIL_2024)
            .updatedOn(FIRST_JANUARY_2025)
            .build();
    bulkSubmissionRepository.save(bulkSubmission);

    submission1 =
        Submission.builder()
            .id(SUBMISSION_1_ID)
            .bulkSubmissionId(bulkSubmission.getId())
            .officeAccountNumber("office1")
            .submissionPeriod("JAN-2025")
            .areaOfLaw(AreaOfLaw.LEGAL_HELP)
            .status(SubmissionStatus.CREATED)
            .crimeLowerScheduleNumber("office1/CRIME")
            .legalHelpSubmissionReference("office1/LEGAL")
            .mediationSubmissionReference("office1/MEDIATION")
            .previousSubmissionId(SUBMISSION_1_ID)
            .isNilSubmission(false)
            .numberOfClaims(5)
            .createdByUserId(USER_ID)
            .providerUserId(bulkSubmission.getCreatedByUserId())
            .createdOn(TENTH_APRIL_2024)
            .build();
    submission2 =
        Submission.builder()
            .id(SUBMISSION_2_ID)
            .bulkSubmissionId(bulkSubmission.getId())
            .officeAccountNumber("office2")
            .submissionPeriod("APR-2024")
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .status(SubmissionStatus.REPLACED)
            .crimeLowerScheduleNumber("office2/CRIME")
            .previousSubmissionId(SUBMISSION_2_ID)
            .isNilSubmission(true)
            .numberOfClaims(3)
            .createdByUserId(USER_ID)
            .providerUserId(bulkSubmission.getCreatedByUserId())
            .createdOn(ELEVENTH_APRIL_2024)
            .build();

    submissionRepository.saveAll(List.of(submission1, submission2));
  }

  @Test
  @DisplayName("Should not get any Submission")
  void shouldNotGetAnySubmission() {
    Page<Submission> result =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(List.of("office5")),
            Pageable.ofSize(10).withPage(0));

    assertThat(result.getContent()).isEmpty();
  }

  @Test
  @DisplayName("Should only get one Submission for the matching office")
  void shouldOnlyGetOneSubmissionForTheMatchingOffice() {
    submission1.setCreatedOn(FIRST_JANUARY_2025);
    submission2.setCreatedOn(TENTH_APRIL_2024);
    submissionRepository.saveAll(List.of(submission1, submission2));

    Page<Submission> result =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(List.of("office1", "office5")),
            Pageable.ofSize(10).withPage(0));

    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent().getFirst())
        .usingRecursiveComparison()
        .ignoringFields(IGNORED_FIELDS)
        .isEqualTo(submission1);
  }

  @Test
  @DisplayName("Should get two Submissions for the matching offices")
  void shouldGetTwoSubmissionsForTheMatchingOffices() {
    submission1.setAreaOfLaw(AreaOfLaw.CRIME_LOWER);
    submission2.setAreaOfLaw(AreaOfLaw.CRIME_LOWER);
    submissionRepository.saveAll(List.of(submission1, submission2));

    Page<Submission> result =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(
                List.of("office1", "office2", "office5")),
            Pageable.ofSize(10).withPage(0));

    assertThat(result.getTotalElements()).isEqualTo(2);
    assertThat(result.getContent())
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields(IGNORED_FIELDS)
        .containsExactlyInAnyOrder(submission1, submission2);
  }

  @Test
  @DisplayName("Should only get one Submission for the matching id")
  void shouldOnlyGetOneSubmissionForTheMatchingId() {
    submission1.setCreatedOn(FIRST_JANUARY_2025);
    submission2.setCreatedOn(TENTH_APRIL_2024);
    submissionRepository.saveAll(List.of(submission1, submission2));

    Page<Submission> result =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(List.of("office1", "office2"))
                .and(SubmissionSpecification.submissionIdEqualTo(String.valueOf(SUBMISSION_1_ID))),
            Pageable.ofSize(10).withPage(0));

    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent().getFirst())
        .usingRecursiveComparison()
        .ignoringFields(IGNORED_FIELDS)
        .isEqualTo(submission1);
  }

  @Test
  @DisplayName("Should not get any Submission for no matching id")
  void shouldNotGetAnySubmissionForNoMatchingId() {
    Page<Submission> result =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(List.of("office1", "office2"))
                .and(SubmissionSpecification.submissionIdEqualTo(String.valueOf(SUBMISSION_3_ID))),
            Pageable.ofSize(10).withPage(0));

    assertThat(result.getContent()).isEmpty();
  }

  @Test
  @DisplayName("Should save and get submission with no bulkSubmissionId")
  void shouldPersistSubmissionWhenBulkSubmissionIdIsNull() {
    submission1.setBulkSubmissionId(null);
    submissionRepository.save(submission1);
    Submission saved = submissionRepository.findById(submission1.getId()).orElseThrow();
    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getId()).isEqualTo(submission1.getId());
    assertThat(saved.getBulkSubmissionId()).isNull();
  }

  @Test
  @DisplayName("Should save and get submission with bulkSubmissionId")
  void shouldPersistSubmissionWhenBulkSubmissionIdIsNotNull() {
    submissionRepository.save(submission1);
    Submission saved = submissionRepository.findById(submission1.getId()).orElseThrow();
    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getId()).isEqualTo(submission1.getId());
    assertThat(saved.getBulkSubmissionId()).isNotNull();
    assertThat(saved.getBulkSubmissionId()).isEqualTo(submission1.getBulkSubmissionId());
  }

  @Test
  @DisplayName("Should only get one Submission for the matching submitted date from")
  void shouldOnlyGetOneSubmissionForTheMatchingSubmittedDateFrom() {
    submission1.setCreatedOn(FIRST_JANUARY_2025);
    submission2.setCreatedOn(TENTH_APRIL_2024);
    submissionRepository.saveAll(List.of(submission1, submission2));

    Page<Submission> result =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(List.of("office1", "office2"))
                .and(SubmissionSpecification.createdOnOrAfter(LocalDate.of(2024, 12, 21))),
            Pageable.ofSize(10).withPage(0));

    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent().getFirst())
        .usingRecursiveComparison()
        .ignoringFields(IGNORED_FIELDS)
        .isEqualTo(submission1);
  }

  @Test
  @DisplayName("Should only get one Submission for the matching submitted date from inclusive")
  void shouldOnlyGetOneSubmissionForTheMatchingSubmittedDateFromInclusive() {
    submission1.setCreatedOn(FIRST_JANUARY_2025);
    submission2.setCreatedOn(TENTH_APRIL_2024);
    submissionRepository.saveAll(List.of(submission1, submission2));

    Page<Submission> result =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(List.of("office1", "office2"))
                .and(SubmissionSpecification.createdOnOrAfter(LocalDate.of(2025, 1, 1))),
            Pageable.ofSize(10).withPage(0));

    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent().getFirst())
        .usingRecursiveComparison()
        .ignoringFields(IGNORED_FIELDS)
        .isEqualTo(submission1);
  }

  @Test
  @DisplayName("Should only get one Submission for the matching submitted date to")
  void shouldOnlyGetOneSubmissionForTheMatchingSubmittedDateTo() {
    submission1.setCreatedOn(FIRST_JANUARY_2025);
    submission2.setCreatedOn(TENTH_APRIL_2024);
    submissionRepository.saveAll(List.of(submission1, submission2));

    Page<Submission> result =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(List.of("office1", "office2"))
                .and(SubmissionSpecification.createdOnOrBefore(LocalDate.of(2024, 7, 14))),
            Pageable.ofSize(10).withPage(0));

    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent().getFirst())
        .usingRecursiveComparison()
        .ignoringFields(IGNORED_FIELDS)
        .isEqualTo(submission2);
  }

  @Test
  @DisplayName("Should only get one Submission for the matching submitted date to inclusive")
  void shouldOnlyGetOneSubmissionForTheMatchingSubmittedDateToInclusive() {
    submission1.setCreatedOn(FIRST_JANUARY_2025);
    submission2.setCreatedOn(TENTH_APRIL_2024);
    submissionRepository.saveAll(List.of(submission1, submission2));

    Page<Submission> result =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(List.of("office1", "office2"))
                .and(SubmissionSpecification.createdOnOrBefore(LocalDate.of(2024, 4, 10))),
            Pageable.ofSize(10).withPage(0));

    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent().getFirst())
        .usingRecursiveComparison()
        .ignoringFields(IGNORED_FIELDS)
        .isEqualTo(submission2);
  }

  @Test
  @DisplayName("Should get two Submissions for the matching submitted date in between")
  void shouldGetTwoSubmissionsForTheMatchingSubmittedDateInBetween() {
    submission1.setCreatedOn(FIRST_JANUARY_2025);
    submission2.setCreatedOn(TENTH_APRIL_2024);
    submissionRepository.saveAll(List.of(submission1, submission2));

    Page<Submission> result =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(List.of("office1", "office2"))
                .and(SubmissionSpecification.createdOnOrAfter(LocalDate.of(2024, 4, 1)))
                .and(SubmissionSpecification.createdOnOrBefore(LocalDate.of(2025, 3, 31))),
            Pageable.ofSize(10).withPage(0));

    assertThat(result.getTotalElements()).isEqualTo(2);

    assertThat(result.getContent().stream().filter(sub -> sub.getId().equals(SUBMISSION_1_ID)))
        .extracting("id")
        .containsExactly(SUBMISSION_1_ID);

    assertThat(result.getContent().stream().filter(sub -> sub.getId().equals(SUBMISSION_2_ID)))
        .extracting("id")
        .containsExactly(SUBMISSION_2_ID);
  }

  @Test
  @DisplayName("Should not get any Submission for no matching submitted date in between")
  void shouldNotGetAnySubmissionForNoMatchingSubmittedDateInBetween() {
    Page<Submission> result =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(List.of("office1", "office2"))
                .and(SubmissionSpecification.createdOnOrAfter(LocalDate.of(2025, 1, 2)))
                .and(SubmissionSpecification.createdOnOrBefore(LocalDate.of(2025, 3, 31))),
            Pageable.ofSize(10).withPage(0));

    assertThat(result.getContent()).isEmpty();
  }

  @DisplayName(
      "Should return result if area of law, submission period and office account number match the"
          + " existing database")
  @Test
  void areaOfLawAndSubmissionPeriod() {
    var actualResults =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(List.of("office1")),
            Pageable.ofSize(10).withPage(0));

    assertThat(actualResults.getContent()).hasSize(1);
    assertThat(actualResults.getContent())
        .extracting("areaOfLaw", "submissionPeriod", "officeAccountNumber")
        .isEqualTo((List.of(tuple(AREA_OF_LAW, "JAN-2025", "office1"))));
  }

  @DisplayName("Should not return result if area of law does not match the existing database")
  @Test
  void areaOfLawAndSubmissionPeriodNotMatch() {
    var actualResults =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(List.of("office1"))
                .and(SubmissionSpecification.areaOfLawEqual(AreaOfLaw.MEDIATION))
                .and(SubmissionSpecification.submissionPeriodEqual("JAN-2025")),
            Pageable.ofSize(10).withPage(0));

    assertThat(actualResults.getContent()).hasSize(0);
  }

  @DisplayName("Should not return result if submission period does not match the existing database")
  @Test
  void submissionPeriodNotMatch() {

    var actualResults =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(List.of("office1"))
                .and(SubmissionSpecification.areaOfLawEqual(AreaOfLaw.LEGAL_HELP))
                .and(SubmissionSpecification.submissionPeriodEqual("JAN-2029")),
            Pageable.ofSize(10).withPage(0));

    assertThat(actualResults.getContent()).hasSize(0);
  }

  @DisplayName("Should  return result even if area of law is null")
  @Test
  void areaOfLawIsNull() {

    var actualResults =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(List.of("office1"))
                .and(SubmissionSpecification.areaOfLawEqual(null))
                .and(SubmissionSpecification.submissionPeriodEqual("JAN-2025")),
            Pageable.ofSize(10).withPage(0));

    assertThat(actualResults.getContent()).hasSize(1);
  }

  @DisplayName("Should  return result even if submission period is null")
  @Test
  void submissionPeriodIsNull() {

    var actualResults =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(List.of("office1"))
                .and(SubmissionSpecification.areaOfLawEqual(AreaOfLaw.LEGAL_HELP))
                .and(SubmissionSpecification.submissionPeriodEqual(null)),
            Pageable.ofSize(10).withPage(0));

    assertThat(actualResults.getContent()).hasSize(1);
  }

  @Test
  @DisplayName("Should not get any Submission for no matching statuses")
  void shouldNotGetAnySubmissionForNoMatchingStatuses() {
    Page<Submission> result =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(List.of("office1", "office2"))
                .and(
                    SubmissionSpecification.submissionStatusIn(
                        List.of(SubmissionStatus.VALIDATION_FAILED))),
            Pageable.ofSize(10).withPage(0));

    assertThat(result.getContent()).isEmpty();
  }

  @Test
  @DisplayName("Should get only one Submission if matching statuses")
  void shouldGetOnlyOneSubmissionForMatchingStatuses() {
    Page<Submission> result =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(List.of("office1", "office2"))
                .and(SubmissionSpecification.submissionStatusIn(SUBMISSION_STATUSES)),
            Pageable.ofSize(10).withPage(0));

    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent().getFirst().getId()).isEqualTo(submission1.getId());
  }

  @DisplayName("Should return result even if submission statuses is null")
  @Test
  void shouldStillGetSubmissionWhenStatusesFiltersIsNull() {

    var actualResults =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(List.of("office1"))
                .and(SubmissionSpecification.submissionStatusIn(null))
                .and(SubmissionSpecification.areaOfLawEqual(AreaOfLaw.LEGAL_HELP))
                .and(SubmissionSpecification.submissionPeriodEqual(null)),
            Pageable.ofSize(10).withPage(0));

    assertThat(actualResults.getContent()).hasSize(1);
  }

  @DisplayName(
      "submissionPeriodSortKey @Formula converts MON-YYYY period to YYYYMM chronological sort key")
  @Test
  void submissionPeriodSortKeyProducesCorrectYearMonthSortKey() {
    // Verifies that the @Formula correctly converts e.g. "APR-2025" → "202504",
    // which enables chronological ordering (alphabetical ordering would give wrong results).
    var submission =
        Submission.builder()
            .id(SUBMISSION_3_ID)
            .bulkSubmissionId(BULK_SUBMISSION_ID)
            .officeAccountNumber("office3")
            .submissionPeriod("APR-2025")
            .areaOfLaw(AreaOfLaw.LEGAL_HELP)
            .status(SubmissionStatus.CREATED)
            .createdByUserId(USER_ID)
            .providerUserId(USER_ID)
            .createdOn(TENTH_APRIL_2024)
            .build();
    submissionRepository.save(submission);

    Submission saved = submissionRepository.findById(SUBMISSION_3_ID).orElseThrow();
    assertThat(saved.getSubmissionPeriodSortKey()).isEqualTo("202504");
  }

  @DisplayName(
      "submissionPeriodSortKey @Formula causes DataIntegrityViolationException if an invalid month name is in the database")
  @Test
  void submissionPeriodSortKeyThrowsForInvalidMonthName() {
    // "ABC-2025" matches the expected MON-YYYY shape but "ABC" is not a valid PostgreSQL month
    // abbreviation. TO_DATE('ABC-2025', 'MON-YYYY') raises an error at SELECT time, which Spring
    // wraps as a DataIntegrityViolationException.
    //
    // Impact depends on context:
    // - Unsorted queries: only fails when the bad row appears in the fetched page's result set;
    //   other pages that don't include it will succeed.
    // - Queries sorted by submissionPeriod (which uses this formula in ORDER BY): PostgreSQL
    //   evaluates the expression across ALL matching rows before pagination, so a single bad row
    //   will cause every page of that sorted query to fail.
    //
    // Upstream validation in the event service prevents invalid values reaching the database,
    // but this test documents the failure mode should that guard ever be bypassed.
    var invalidSubmission =
        Submission.builder()
            .id(SUBMISSION_3_ID)
            .bulkSubmissionId(BULK_SUBMISSION_ID)
            .officeAccountNumber("office3")
            .submissionPeriod("ABC-2025")
            .areaOfLaw(AreaOfLaw.LEGAL_HELP)
            .status(SubmissionStatus.CREATED)
            .createdByUserId(USER_ID)
            .providerUserId(USER_ID)
            .createdOn(TENTH_APRIL_2024)
            .build();
    submissionRepository.save(invalidSubmission);

    assertThatThrownBy(() -> submissionRepository.findById(SUBMISSION_3_ID))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("invalid value")
        .hasMessageContaining("MON");
  }

  @DisplayName("Should return result even if submission statuses is empty")
  @Test
  void shouldStillGetSubmissionWhenStatusesFiltersIsEmpty() {

    var actualResults =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(List.of("office1"))
                .and(SubmissionSpecification.submissionStatusIn(Collections.emptyList()))
                .and(SubmissionSpecification.areaOfLawEqual(AreaOfLaw.LEGAL_HELP))
                .and(SubmissionSpecification.submissionPeriodEqual(null)),
            Pageable.ofSize(10).withPage(0));

    assertThat(actualResults.getContent()).hasSize(1);
  }

  @Nested
  @DisplayName("PDS - Submission Totals Recalculation After Claim Amendments (DSTEW-1644)")
  class SubmissionTotalsRecalculation {

    @Autowired private EntityManager entityManager;
    @Autowired private SubmissionService submissionService;

    @Test
    @Transactional
    @DisplayName(
        "Given a submission whose claims each have a single calculated-fee row, it sums normally")
    void singleRowPerClaimSumsNormally() {
      Submission submission = createIsolatedSubmission();
      Claim claim1 = createClaimForSubmission(submission, 1);
      Claim claim2 = createClaimForSubmission(submission, 2);
      Claim claim3 = createClaimForSubmission(submission, 3);

      createFeeDetail(claim1, BigDecimal.valueOf(100.00), OffsetDateTime.now(), null);
      createFeeDetail(claim2, BigDecimal.valueOf(200.00), OffsetDateTime.now(), null);
      createFeeDetail(claim3, BigDecimal.valueOf(50.00), OffsetDateTime.now(), null);

      entityManager.clear();

      BigDecimal calculatedTotalAmount =
          submissionRepository.getCalculatedTotalAmount(submission.getId());
      assertThat(calculatedTotalAmount).isEqualByComparingTo("350.00");
    }

    @Test
    @Transactional
    @DisplayName(
        "Given a submission with claims that have multiple calculated-fee rows, it sums only the latest row using created_on")
    void multipleRowsPerClaimSumsOnlyLatestByCreatedOn() {
      Submission submission = createIsolatedSubmission();
      Claim claimX = createClaimForSubmission(submission, 1);
      Claim claimY = createClaimForSubmission(submission, 2);

      // Claim X: Old row = 100.00, Latest row = 125.00
      createFeeDetail(claimX, BigDecimal.valueOf(100.00), OffsetDateTime.now().minusDays(2), null);
      createFeeDetail(claimX, BigDecimal.valueOf(125.00), OffsetDateTime.now(), null);

      // Claim Y: Only one row = 200.00
      createFeeDetail(claimY, BigDecimal.valueOf(200.00), OffsetDateTime.now().minusDays(1), null);

      entityManager.clear();
      Submission retrieved = submissionRepository.findById(submission.getId()).orElseThrow();

      // Expected: 125.00 (from X's latest) + 200.00 (from Y) = 325.00
      BigDecimal calculatedTotalAmount =
          submissionRepository.getCalculatedTotalAmount(retrieved.getId());
      assertThat(calculatedTotalAmount).isEqualByComparingTo("325.00");
    }

    @Test
    @Transactional
    @DisplayName(
        "Given two calculated-fee rows on the same claim have the same created_on, it uses greatest id as the tie-break")
    void multipleRowsPerClaimSameCreatedOnTieBreaksById() {
      Submission submission = createIsolatedSubmission();
      Claim claim = createClaimForSubmission(submission, 1);

      OffsetDateTime exactSameTime = OffsetDateTime.now();

      // Hardcode UUIDs to deterministically guarantee which ID is the "greatest"
      UUID lowerId = UUID.fromString("01900000-0000-7000-8000-000000000001");
      UUID greaterId = UUID.fromString("01900000-0000-7000-8000-000000000002");

      createFeeDetail(claim, BigDecimal.valueOf(100.00), exactSameTime, lowerId);
      createFeeDetail(claim, BigDecimal.valueOf(999.00), exactSameTime, greaterId);

      entityManager.clear();
      Submission retrieved = submissionRepository.findById(submission.getId()).orElseThrow();

      // Expected: 999.00 because greaterId wins the tie-break
      BigDecimal calculatedTotalAmount =
          submissionRepository.getCalculatedTotalAmount(retrieved.getId());
      assertThat(calculatedTotalAmount).isEqualByComparingTo("999.00");
    }

    @Test
    @Transactional
    @DisplayName(
        "Bulk Submissions: Given multiple submissions, it groups by submission and sums only the latest row per claim")
    void bulkGetCalculatedTotalAmountsSumsLatestPerSubmission() {
      // Setup Submission 1 with an amended claim
      Submission sub1 = createIsolatedSubmission();
      Claim sub1Claim = createClaimForSubmission(sub1, 1);
      createFeeDetail(
          sub1Claim, BigDecimal.valueOf(100.00), OffsetDateTime.now().minusDays(1), null);
      createFeeDetail(
          sub1Claim, BigDecimal.valueOf(150.00), OffsetDateTime.now(), null); // Latest for Sub 1

      // Setup Submission 2 with an amended claim
      Submission sub2 = createIsolatedSubmission();
      Claim sub2Claim = createClaimForSubmission(sub2, 1);
      createFeeDetail(
          sub2Claim, BigDecimal.valueOf(300.00), OffsetDateTime.now().minusDays(1), null);
      createFeeDetail(
          sub2Claim, BigDecimal.valueOf(450.00), OffsetDateTime.now(), null); // Latest for Sub 2

      // Force DB execution
      entityManager.clear();

      // Act
      List<SubmissionRepository.CalculatedTotalAmountProjection> results =
          submissionRepository.getCalculatedTotalAmounts(List.of(sub1.getId(), sub2.getId()));

      // Assert
      assertThat(results).hasSize(2);

      SubmissionRepository.CalculatedTotalAmountProjection result1 =
          results.stream()
              .filter(r -> r.getSubmissionId().equals(sub1.getId()))
              .findFirst()
              .orElseThrow();
      assertThat(result1.getTotal()).isEqualByComparingTo("150.00");

      SubmissionRepository.CalculatedTotalAmountProjection result2 =
          results.stream()
              .filter(r -> r.getSubmissionId().equals(sub2.getId()))
              .findFirst()
              .orElseThrow();
      assertThat(result2.getTotal()).isEqualByComparingTo("450.00");
    }

    @Test
    @Transactional
    @DisplayName(
        "Response shape is unchanged and only calculated-total differs for multi-row claims")
    void responseShapeIsUnchangedOnlyTotalDiffers() {
      // 1. Setup Classic Submission (1 Claim, 1 Fee Row)
      Submission classicSub = createIsolatedSubmission();
      Claim classicClaim = createClaimForSubmission(classicSub, 1);
      createFeeDetail(classicClaim, BigDecimal.valueOf(100.00), OffsetDateTime.now(), null);

      // 2. Setup Amended Submission (1 Claim, 2 Fee Rows)
      Submission amendedSub = createIsolatedSubmission();
      Claim amendedClaim = createClaimForSubmission(amendedSub, 1);
      createFeeDetail(
          amendedClaim, BigDecimal.valueOf(100.00), OffsetDateTime.now().minusDays(1), null);
      createFeeDetail(
          amendedClaim, BigDecimal.valueOf(250.00), OffsetDateTime.now(), null); // Latest

      entityManager.flush();
      entityManager.clear();

      // 3. Fetch full responses via the Service
      SubmissionResponse classicResponse = submissionService.getSubmission(classicSub.getId());
      SubmissionResponse amendedResponse = submissionService.getSubmission(amendedSub.getId());

      // 4. Assert the TOTALS differ exactly as expected
      assertThat(classicResponse.getCalculatedTotalAmount()).isEqualByComparingTo("100.00");
      assertThat(amendedResponse.getCalculatedTotalAmount()).isEqualByComparingTo("250.00");

      // 5. Assert the SHAPE and DATA remain absolutely identical
      // (proving the extra fee row didn't duplicate the claims list or alter the structure)
      assertThat(amendedResponse)
          .usingRecursiveComparison()
          .ignoringFields(
              "submissionId", // Ignore root ID
              "submitted", // Ignore millisecond creation differences
              "claims.claimId", // Ignore nested Claim IDs
              "calculatedTotalAmount" // The ONLY field allowed to differ
              )
          .isEqualTo(classicResponse);

      // Explicitly prove the one-to-many join didn't duplicate the claim in the array
      assertThat(amendedResponse.getClaims()).hasSize(1);

      // Explicitly prove the one-to-many join didn't duplicate the claim in the array
      assertThat(amendedResponse.getClaims()).hasSize(1);
    }

    @Test
    @Transactional
    @DisplayName(
        "Single Query: Returns null when a submission has claims but no calculated fee detail rows")
    void getCalculatedTotalAmountReturnsNullWhenNoFeeDetails() {
      // 1. Setup Submission with a Claim, but DO NOT create any CalculatedFeeDetail rows
      Submission submission = createIsolatedSubmission();
      createClaimForSubmission(submission, 1);

      entityManager.flush();
      entityManager.clear();

      // 2. Execute the single SQL query
      BigDecimal totalAmount = submissionRepository.getCalculatedTotalAmount(submission.getId());

      // 3. Assert it naturally evaluates to null
      assertThat(totalAmount).isNull();
    }

    @Test
    @Transactional
    @DisplayName(
        "Bulk Query: Omits submission (yielding an empty list) when it has claims but no calculated fee detail rows")
    void getCalculatedTotalAmountsOmitsSubmissionWhenNoFeeDetails() {
      // 1. Setup Submission with a Claim, but DO NOT create any CalculatedFeeDetail rows
      Submission submission = createIsolatedSubmission();
      createClaimForSubmission(submission, 1);

      entityManager.flush();
      entityManager.clear();

      // 2. Execute the bulk SQL query
      var totals = submissionRepository.getCalculatedTotalAmounts(List.of(submission.getId()));

      // 3. Assert the list is empty (because the INNER JOIN drops claims without fee details)
      // Note: The SubmissionService handles this empty list by omitting the ID from the map,
      // which ultimately maps to a null total in the response DTO.
      assertThat(totals).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName(
        "Single Query: Returns null when a submission has a fee detail but its total is null")
    void getCalculatedTotalAmountReturnsNullWhenFeeDetailTotalIsNull() {
      // 1. Setup Submission with a Claim
      Submission submission = createIsolatedSubmission();
      Claim claim = createClaimForSubmission(submission, 1);

      // 2. Create a fee detail row, but explicitly set the amount to null
      createFeeDetail(claim, null, OffsetDateTime.now(), null);

      entityManager.flush();
      entityManager.clear();

      // 3. Execute the SQL query
      BigDecimal totalAmount = submissionRepository.getCalculatedTotalAmount(submission.getId());

      // 4. Assert it evaluates to null (SUM of only NULLs evaluates to NULL)
      assertThat(totalAmount).isNull();
    }

    @Test
    @Transactional
    @DisplayName(
        "Single Query: Sums correctly when multiple claims exist and one has a null fee detail total")
    void getCalculatedTotalAmountSumsCorrectlyWhenOneFeeDetailIsNull() {
      // 1. Setup Submission
      Submission submission = createIsolatedSubmission();

      // 2. Claim 1 with a valid total (e.g., 100.00)
      Claim claim1 = createClaimForSubmission(submission, 1);
      createFeeDetail(claim1, BigDecimal.valueOf(100.00), OffsetDateTime.now(), null);

      // 3. Claim 2 with a NULL total
      Claim claim2 = createClaimForSubmission(submission, 2);
      createFeeDetail(claim2, null, OffsetDateTime.now(), null);

      entityManager.flush();
      entityManager.clear();

      // 4. Execute the SQL query
      BigDecimal totalAmount = submissionRepository.getCalculatedTotalAmount(submission.getId());

      // 5. Assert it ignores the NULL and sums the rest correctly
      assertThat(totalAmount).isEqualByComparingTo("100.00");
    }

    @Test
    @Transactional
    @DisplayName(
        "Bulk Query: Sums correctly for a submission when one of its claims has a null fee detail total")
    void getCalculatedTotalAmountsSumsCorrectlyWhenOneFeeDetailIsNull() {
      // 1. Setup Submission
      Submission submission = createIsolatedSubmission();

      // 2. Claim 1 with a valid total
      Claim claim1 = createClaimForSubmission(submission, 1);
      createFeeDetail(claim1, BigDecimal.valueOf(100.00), OffsetDateTime.now(), null);

      // 3. Claim 2 with a NULL total
      Claim claim2 = createClaimForSubmission(submission, 2);
      createFeeDetail(claim2, null, OffsetDateTime.now(), null);

      entityManager.flush();
      entityManager.clear();

      // 4. Execute the bulk SQL query
      var totals = submissionRepository.getCalculatedTotalAmounts(List.of(submission.getId()));

      // 5. Assert the list contains the submission and sums the valid rows, ignoring the null
      assertThat(totals).hasSize(1);
      assertThat(totals.get(0).getSubmissionId()).isEqualTo(submission.getId());
      assertThat(totals.get(0).getTotal()).isEqualByComparingTo("100.00");
    }
  }
}
