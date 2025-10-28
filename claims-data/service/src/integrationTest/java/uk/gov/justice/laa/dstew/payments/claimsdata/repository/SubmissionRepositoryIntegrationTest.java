package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AREA_OF_LAW;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.BULK_SUBMISSION_CREATED_BY_USER_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.BULK_SUBMISSION_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_2_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_3_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_STATUSES;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.USER_ID;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionAreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetails;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.specification.SubmissionSpecification;

/**
 * This contains integration tests to verify the filtering logic implemented in the {@link
 * SubmissionSpecification} and used by the {@link SubmissionRepository}.
 */
@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("SubmissionRepository Integration Test")
public class SubmissionRepositoryIntegrationTest extends AbstractIntegrationTest {

  private static final Instant FIRST_JANUARY_2025 =
      LocalDate.of(2025, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
  private static final Instant TENTH_APRIL_2024 =
      LocalDate.of(2024, 4, 10).atStartOfDay().toInstant(ZoneOffset.UTC);
  private static final String IGNORE_FIELD_UPDATE_ON = "updatedOn";

  private Submission submission1;
  private Submission submission2;

  /**
   * This is to set the testing data such as the bulk submission and the corresponding submissions
   * which will be saved in the shared test container's database for the execution of the
   * integration tests. This callback method gets executed before every test method. This will
   * ensure that each test runs with an empty and clean database and circumvent any kind of test
   * pollution.
   */
  @BeforeEach
  public void setup() {
    clearIntegrationData();

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
            .submissionPeriod("JAN-25")
            .areaOfLaw(BulkSubmissionAreaOfLaw.LEGAL_HELP)
            .status(SubmissionStatus.CREATED)
            .crimeLowerScheduleNumber("office1/CRIME")
            .legalHelpSubmissionReference("office1/LEGAL")
            .mediationSubmissionReference("office1/MEDIATION")
            .previousSubmissionId(SUBMISSION_1_ID)
            .isNilSubmission(false)
            .numberOfClaims(5)
            .createdByUserId(USER_ID)
            .providerUserId(bulkSubmission.getCreatedByUserId())
            .build();
    submission2 =
        Submission.builder()
            .id(SUBMISSION_2_ID)
            .bulkSubmissionId(bulkSubmission.getId())
            .officeAccountNumber("office2")
            .submissionPeriod("APR-24")
            .areaOfLaw(BulkSubmissionAreaOfLaw.CRIME_LOWER)
            .status(SubmissionStatus.REPLACED)
            .crimeLowerScheduleNumber("office2/CRIME")
            .previousSubmissionId(SUBMISSION_2_ID)
            .isNilSubmission(true)
            .numberOfClaims(3)
            .createdByUserId(USER_ID)
            .providerUserId(bulkSubmission.getCreatedByUserId())
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
        .ignoringFields(IGNORE_FIELD_UPDATE_ON)
        .isEqualTo(submission1);
  }

  @Test
  @DisplayName("Should get two Submissions for the matching offices")
  void shouldGetTwoSubmissionsForTheMatchingOffices() {
    submission1.setCreatedOn(FIRST_JANUARY_2025);
    submission2.setCreatedOn(TENTH_APRIL_2024);
    submissionRepository.saveAll(List.of(submission1, submission2));

    Page<Submission> result =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(
                List.of("office1", "office2", "office5")),
            Pageable.ofSize(10).withPage(0));

    assertThat(result.getTotalElements()).isEqualTo(2);
    assertThat(result.getContent().getFirst())
        .usingRecursiveComparison()
        .ignoringFields(IGNORE_FIELD_UPDATE_ON)
        .isEqualTo(submission1);
    assertThat(result.getContent().get(1))
        .usingRecursiveComparison()
        .ignoringFields(IGNORE_FIELD_UPDATE_ON)
        .isEqualTo(submission2);
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
        .ignoringFields(IGNORE_FIELD_UPDATE_ON)
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
        .ignoringFields(IGNORE_FIELD_UPDATE_ON)
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
        .ignoringFields(IGNORE_FIELD_UPDATE_ON)
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
        .ignoringFields(IGNORE_FIELD_UPDATE_ON)
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
        .ignoringFields(IGNORE_FIELD_UPDATE_ON)
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
      "Should return result if area of law, submission period and office account number match the existing database")
  @Test
  void areaOfLawAndSubmissionPeriod() {
    var actualResults =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(List.of("office1")),
            Pageable.ofSize(10).withPage(0));

    assertThat(actualResults.getContent()).hasSize(1);
    assertThat(actualResults.getContent())
        .extracting("areaOfLaw", "submissionPeriod", "officeAccountNumber")
        .isEqualTo((List.of(tuple(AREA_OF_LAW, "JAN-25", "office1"))));
  }

  @DisplayName("Should not return result if area of law does not match the existing database")
  @Test
  void areaOfLawAndSubmissionPeriodNotMatch() {
    var actualResults =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(List.of("office1"))
                .and(SubmissionSpecification.areaOfLawEqual(BulkSubmissionAreaOfLaw.MEDIATION))
                .and(SubmissionSpecification.submissionPeriodEqual("JAN-25")),
            Pageable.ofSize(10).withPage(0));

    assertThat(actualResults.getContent()).hasSize(0);
  }

  @DisplayName("Should not return result if submission period does not match the existing database")
  @Test
  void submissionPeriodNotMatch() {

    var actualResults =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(List.of("office1"))
                .and(SubmissionSpecification.areaOfLawEqual(BulkSubmissionAreaOfLaw.LEGAL_HELP))
                .and(SubmissionSpecification.submissionPeriodEqual("JAN-29")),
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
                .and(SubmissionSpecification.submissionPeriodEqual("JAN-25")),
            Pageable.ofSize(10).withPage(0));

    assertThat(actualResults.getContent()).hasSize(1);
  }

  @DisplayName("Should  return result even if submission period is null")
  @Test
  void submissionPeriodIsNull() {

    var actualResults =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(List.of("office1"))
                .and(SubmissionSpecification.areaOfLawEqual(BulkSubmissionAreaOfLaw.LEGAL_HELP))
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
                .and(SubmissionSpecification.areaOfLawEqual(BulkSubmissionAreaOfLaw.LEGAL_HELP))
                .and(SubmissionSpecification.submissionPeriodEqual(null)),
            Pageable.ofSize(10).withPage(0));

    assertThat(actualResults.getContent()).hasSize(1);
  }

  @DisplayName("Should return result even if submission statuses is empty")
  @Test
  void shouldStillGetSubmissionWhenStatusesFiltersIsEmpty() {

    var actualResults =
        submissionRepository.findAll(
            SubmissionSpecification.filterByOfficeAccountNumberIn(List.of("office1"))
                .and(SubmissionSpecification.submissionStatusIn(Collections.emptyList()))
                .and(SubmissionSpecification.areaOfLawEqual(BulkSubmissionAreaOfLaw.LEGAL_HELP))
                .and(SubmissionSpecification.submissionPeriodEqual(null)),
            Pageable.ofSize(10).withPage(0));

    assertThat(actualResults.getContent()).hasSize(1);
  }
}
