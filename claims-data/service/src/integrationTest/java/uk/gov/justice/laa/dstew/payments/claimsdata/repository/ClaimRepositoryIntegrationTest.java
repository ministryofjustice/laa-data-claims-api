package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CASE_REFERENCE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SCHEDULE_REFERENCE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_2_ID;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.specification.ClaimSpecification;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * This contains integration tests to verify the filtering logic implemented in the {@link
 * ClaimSpecification} and used by the {@link ClaimRepository}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("ClaimRepository Integration Test")
public class ClaimRepositoryIntegrationTest extends AbstractIntegrationTest {

  private static final String IGNORE_FIELD_SUBMISSION = "submission";
  private static final String IGNORE_FIELD_CREATED_ON = "createdOn";
  private static final String IGNORE_FIELD_UPDATED_ON = "updatedOn";

  /**
   * This is to set the testing data such as the bulk submission, submissions, claims and clients
   * which will be saved in the shared test container's database for the execution of the
   * integration tests. This callback method gets executed before every test method. This will
   * ensure that each test runs with an empty and clean database and circumvent any kind of test
   * pollution.
   */
  @BeforeEach
  public void setup() {
    clearIntegrationData();
    createClaimsTestData();
  }

  @Test
  @DisplayName("Should not get any Claim")
  void shouldNotGetAnyClaim() {
    Page<Claim> result =
        claimRepository.findAll(
            ClaimSpecification.filterBy(
                "office_test",
                Uuid7.timeBasedUuid().toString(),
                List.of(SubmissionStatus.REPLACED),
                "fee-code",
                "unique-file-number",
                "unique-client-number",
                "unique-case-id",
                List.of(ClaimStatus.INVALID),
                "APR-2024",
                "CASE_123"),
            Pageable.ofSize(10).withPage(0));

    assertThat(result.getContent()).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("getClaimsSearchQueryParams")
  @DisplayName("Should only get one Claim using all query params")
  void shouldOnlyGetOneClaimWhenUsingAllQueryParams(
      String officeCode,
      String submissionId,
      List<SubmissionStatus> submissionStatuses,
      String feeCode,
      String uniqueFileNumber,
      String uniqueClientNumber,
      String uniqueCaseId,
      List<ClaimStatus> claimStatuses,
      String submissionPeriod,
      String caseReferenceNumber) {
    Page<Claim> result =
        claimRepository.findAll(
            ClaimSpecification.filterBy(
                officeCode,
                submissionId,
                submissionStatuses,
                feeCode,
                uniqueFileNumber,
                uniqueClientNumber,
                uniqueCaseId,
                claimStatuses,
                submissionPeriod,
                caseReferenceNumber),
            Pageable.ofSize(10).withPage(0));

    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent().getFirst())
        .usingRecursiveComparison()
        .ignoringFields(IGNORE_FIELD_SUBMISSION, IGNORE_FIELD_CREATED_ON, IGNORE_FIELD_UPDATED_ON)
        .isEqualTo(claim3);
  }

  @Test
  @DisplayName("Should only get one Claim using only list params")
  void shouldOnlyGetOneClaimWhenUsingOnlyListParams() {
    Page<Claim> result =
        claimRepository.findAll(
            ClaimSpecification.filterBy(
                "office2",
                null,
                List.of(
                    SubmissionStatus.CREATED,
                    SubmissionStatus.REPLACED,
                    SubmissionStatus.VALIDATION_SUCCEEDED),
                null,
                null,
                null,
                null,
                List.of(ClaimStatus.READY_TO_PROCESS, ClaimStatus.VALID, ClaimStatus.INVALID),
                null,
                null),
            Pageable.ofSize(10).withPage(0));

    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent().getFirst())
        .usingRecursiveComparison()
        .ignoringFields(IGNORE_FIELD_SUBMISSION, IGNORE_FIELD_CREATED_ON, IGNORE_FIELD_UPDATED_ON)
        .isEqualTo(claim3);
    assertThat(result.getContent().getFirst().getCaseReferenceNumber()).isEqualTo(CASE_REFERENCE);
    assertThat(result.getContent().getFirst().getScheduleReference()).isNull();
  }

  @Test
  @DisplayName("Should only get one Claim using empty list params")
  void shouldOnlyGetOneClaimWhenUsingEmptyListParams() {
    Page<Claim> result =
        claimRepository.findAll(
            ClaimSpecification.filterBy(
                "office2", null, List.of(), null, null, null, null, List.of(), null, null),
            Pageable.ofSize(10).withPage(0));

    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent().getFirst())
        .usingRecursiveComparison()
        .ignoringFields(IGNORE_FIELD_SUBMISSION, IGNORE_FIELD_CREATED_ON, IGNORE_FIELD_UPDATED_ON)
        .isEqualTo(claim3);
  }

  @Test
  @DisplayName("Should get two Claim records matching the same query params")
  void shouldGetTwoClaimsMatchingTheSameQueryParams() {
    Page<Claim> result =
        claimRepository.findAll(
            ClaimSpecification.filterBy(
                "office1",
                null,
                null,
                "FEE_123",
                "UFN_123",
                null,
                null,
                List.of(ClaimStatus.READY_TO_PROCESS),
                null,
                null),
            Pageable.ofSize(10).withPage(0));

    assertThat(result.getTotalElements()).isEqualTo(2);
    var actualClaim1 = result.getContent().getFirst();
    var actualClaim2 = result.getContent().get(1);
    assertThat(actualClaim1)
        .usingRecursiveComparison()
        .ignoringFields(IGNORE_FIELD_SUBMISSION, IGNORE_FIELD_CREATED_ON, IGNORE_FIELD_UPDATED_ON)
        .isEqualTo(claim1);
    assertThat(actualClaim1)
        .usingRecursiveComparison()
        .ignoringFields(
            "id", IGNORE_FIELD_SUBMISSION, IGNORE_FIELD_CREATED_ON, IGNORE_FIELD_UPDATED_ON)
        .isEqualTo(actualClaim2);
    assertThat(actualClaim1.getCaseReferenceNumber()).isEqualTo(CASE_REFERENCE);
    assertThat(actualClaim1.getScheduleReference()).isEqualTo(SCHEDULE_REFERENCE);
  }

  /**
   * This is a util method to provide arguments to test the {@link ClaimSpecification} filter based
   * on different query params.
   *
   * @return a stream of {@link Arguments} representing the query params for the Claim search.
   */
  public static Stream<Arguments> getClaimsSearchQueryParams() {
    return Stream.of(
        Arguments.of("office2", null, null, null, null, null, null, null, null, null),
        Arguments.of(
            "office2", SUBMISSION_2_ID.toString(), null, null, null, null, null, null, null, null),
        Arguments.of(
            "office2",
            null,
            List.of(SubmissionStatus.VALIDATION_SUCCEEDED),
            null,
            null,
            null,
            null,
            null,
            null,
            null),
        Arguments.of("office2", null, null, "FEE_333", null, null, null, null, null, null),
        Arguments.of("office2", null, null, null, "UFN_333", null, null, null, null, null),
        Arguments.of("office2", null, null, null, null, "UCN_333", null, null, null, null),
        Arguments.of("office2", null, null, null, null, null, "UC_ID_2", null, null, null),
        Arguments.of(
            "office2",
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(ClaimStatus.INVALID),
            null,
            null),
        Arguments.of("office2", null, null, null, null, null, null, null, "APR-2024", null),
        Arguments.of("office2", null, null, null, null, null, null, null, null, "CASE_123"));
  }
}
