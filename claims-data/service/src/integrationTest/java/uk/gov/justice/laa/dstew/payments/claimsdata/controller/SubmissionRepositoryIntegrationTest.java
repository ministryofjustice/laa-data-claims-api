package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.USER_ID;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.PostgresTestConfig;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.SqsTestConfig;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetails;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.BulkSubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.specification.SubmissionSpecification;

/**
 * This contains integration tests to verify the filtering logic implemented in the {@link
 * SubmissionSpecification} and used by the {@link SubmissionRepository}.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({SqsTestConfig.class, PostgresTestConfig.class})
@AutoConfigureMockMvc
@Testcontainers
@Slf4j
@DisplayName("SubmissionRepository Integration Test")
public class SubmissionRepositoryIntegrationTest {

  private static final UUID SUBMISSION_1_ID = UUID.randomUUID();
  private static final UUID SUBMISSION_2_ID = UUID.randomUUID();
  private static final UUID SUBMISSION_3_ID = UUID.randomUUID();
  private static final Instant FIRST_JANUARY_2025 =
      LocalDate.of(2025, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
  private static final Instant TENTH_APRIL_2024 =
      LocalDate.of(2024, 4, 10).atStartOfDay().toInstant(ZoneOffset.UTC);
  private static final String IGNORE_FIELD_UPDATE_ON = "updatedOn";

  @Autowired private SubmissionRepository submissionRepository;

  @Autowired private BulkSubmissionRepository bulkSubmissionRepository;

  private Submission submission1;
  private Submission submission2;

  /**
   * This is to set the testing data such as the bulk submission and the corresponding submissions
   * which will be saved in the test container's database for the execution of the integration
   * tests. This callback method gets executed before every test method. This will ensure that each
   * test runs with an empty and clean database and circumvent any kind of test pollution.
   */
  @BeforeEach
  public void setup() {
    submissionRepository.deleteAll();
    bulkSubmissionRepository.deleteAll();

    var bulkSubmission =
        BulkSubmission.builder()
            .data(new GetBulkSubmission200ResponseDetails())
            .status(BulkSubmissionStatus.READY_FOR_PARSING)
            .createdByUserId(USER_ID)
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
            .areaOfLaw("CIVIL")
            .status(SubmissionStatus.CREATED)
            .scheduleNumber("office1/CIVIL")
            .previousSubmissionId(SUBMISSION_1_ID)
            .isNilSubmission(false)
            .numberOfClaims(5)
            .createdByUserId(USER_ID)
            .build();
    submission2 =
        Submission.builder()
            .id(SUBMISSION_2_ID)
            .bulkSubmissionId(bulkSubmission.getId())
            .officeAccountNumber("office2")
            .submissionPeriod("APR-24")
            .areaOfLaw("CRIME")
            .status(SubmissionStatus.CREATED)
            .scheduleNumber("office2/CRIME")
            .previousSubmissionId(SUBMISSION_2_ID)
            .isNilSubmission(true)
            .numberOfClaims(3)
            .createdByUserId(USER_ID)
            .build();

    submissionRepository.saveAll(List.of(submission1, submission2));
  }

  @Test
  @DisplayName("Should not get any Submission")
  void shouldNotGetAnySubmission() {
    Page<Submission> result =
        submissionRepository.findAll(
            SubmissionSpecification.filterBy(List.of("office5"), null, null, null),
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
            SubmissionSpecification.filterBy(List.of("office1", "office5"), null, null, null),
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
            SubmissionSpecification.filterBy(
                List.of("office1", "office2", "office5"), null, null, null),
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
            SubmissionSpecification.filterBy(
                List.of("office1", "office2"), String.valueOf(SUBMISSION_1_ID), null, null),
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
            SubmissionSpecification.filterBy(
                List.of("office1", "office2"), String.valueOf(SUBMISSION_3_ID), null, null),
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
            SubmissionSpecification.filterBy(
                List.of("office1", "office2"), null, LocalDate.of(2024, 12, 21), null),
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
            SubmissionSpecification.filterBy(
                List.of("office1", "office2"), null, LocalDate.of(2025, 1, 1), null),
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
            SubmissionSpecification.filterBy(
                List.of("office1", "office2"), null, null, LocalDate.of(2024, 7, 14)),
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
            SubmissionSpecification.filterBy(
                List.of("office1", "office2"), null, null, LocalDate.of(2024, 4, 10)),
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
            SubmissionSpecification.filterBy(
                List.of("office1", "office2"),
                null,
                LocalDate.of(2024, 4, 1),
                LocalDate.of(2025, 3, 31)),
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
  @DisplayName("Should not get any Submission for no matching submitted date in between")
  void shouldNotGetAnySubmissionForNoMatchingSubmittedDateInBetween() {
    Page<Submission> result =
        submissionRepository.findAll(
            SubmissionSpecification.filterBy(
                List.of("office1", "office2"),
                null,
                LocalDate.of(2025, 1, 2),
                LocalDate.of(2025, 3, 31)),
            Pageable.ofSize(10).withPage(0));

    assertThat(result.getContent()).isEmpty();
  }
}
