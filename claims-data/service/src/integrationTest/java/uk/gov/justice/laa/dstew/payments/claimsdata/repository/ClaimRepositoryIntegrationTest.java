package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetails;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.specification.ClaimSpecification;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;
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

  @Autowired private ValidationMessageLogRepository validationMessageLogRepository;
  @Autowired private BulkSubmissionRepository bulkSubmissionRepository;
  @Autowired private SubmissionRepository submissionRepository;
  @Autowired private ClaimRepository claimRepository;
  @Autowired private ClientRepository clientRepository;

  private Submission submission1;
  private Submission submission2;
  private Submission submission3;
  private Claim claim1;
  private Claim claim2;
  private Claim claim3;
  private Client client1;
  private Client client2;
  private Client client3;

  /**
   * This is to set the testing data such as the bulk submission, submissions, claims and clients
   * which will be saved in the shared test container's database for the execution of the
   * integration tests. This callback method gets executed before every test method. This will
   * ensure that each test runs with an empty and clean database and circumvent any kind of test
   * pollution.
   */
  @BeforeEach
  public void setup() {
    validationMessageLogRepository.deleteAll();
    clientRepository.deleteAll();
    claimRepository.deleteAll();
    submissionRepository.deleteAll();
    bulkSubmissionRepository.deleteAll();

    var bulkSubmission =
        BulkSubmission.builder()
            .id(BULK_SUBMISSION_ID)
            .data(new GetBulkSubmission200ResponseDetails())
            .status(BulkSubmissionStatus.READY_FOR_PARSING)
            .createdByUserId(USER_ID)
            .createdOn(LocalDate.of(2024, 4, 10).atStartOfDay().toInstant(ZoneOffset.UTC))
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
            .createdByUserId(USER_ID)
            .build();
    submission2 =
        Submission.builder()
            .id(SUBMISSION_2_ID)
            .bulkSubmissionId(bulkSubmission.getId())
            .officeAccountNumber("office2")
            .submissionPeriod("APR-24")
            .areaOfLaw("CRIME")
            .status(SubmissionStatus.READY_FOR_VALIDATION)
            .createdByUserId(USER_ID)
            .build();
    submission3 =
        Submission.builder()
            .id(SUBMISSION_3_ID)
            .bulkSubmissionId(bulkSubmission.getId())
            .officeAccountNumber("office3")
            .submissionPeriod("DEC-24")
            .areaOfLaw("CRIME")
            .status(SubmissionStatus.VALIDATION_SUCCEEDED)
            .createdByUserId(USER_ID)
            .build();
    submissionRepository.saveAll(List.of(submission1, submission2, submission3));

    claim1 = ClaimsDataTestUtil.getClaimBuilder().submission(submission1).build();
    claim2 = ClaimsDataTestUtil.getClaimBuilder().id(CLAIM_2_ID).submission(submission1).build();
    claim3 =
        Claim.builder()
            .id(Uuid7.timeBasedUuid())
            .submission(submission3)
            .uniqueFileNumber("UFN_333")
            .lineNumber(333)
            .matterTypeCode("MTC_333")
            .caseStartDate(LocalDate.now().minusDays(365))
            .caseConcludedDate(LocalDate.now().minusDays(30))
            .feeCode("FEE_333")
            .status(ClaimStatus.INVALID)
            .createdByUserId(USER_ID)
            .createdOn(SUBMITTED_DATE.toInstant())
            .build();
    claimRepository.saveAll(List.of(claim1, claim2, claim3));

    client1 = ClaimsDataTestUtil.getClientBuilder().claim(claim1).build();
    client2 = ClaimsDataTestUtil.getClientBuilder().claim(claim2).build();
    client2.setId(CLIENT_2_ID);
    client3 =
        Client.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(claim3)
            .uniqueClientNumber("UCN_333")
            .createdByUserId(USER_ID)
            .createdOn(SUBMITTED_DATE.toInstant())
            .build();
    clientRepository.saveAll(List.of(client1, client2, client3));
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
                List.of(ClaimStatus.INVALID)),
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
      List<ClaimStatus> claimStatuses) {
    Page<Claim> result =
        claimRepository.findAll(
            ClaimSpecification.filterBy(
                officeCode,
                submissionId,
                submissionStatuses,
                feeCode,
                uniqueFileNumber,
                uniqueClientNumber,
                claimStatuses),
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
                "office3",
                null,
                List.of(
                    SubmissionStatus.CREATED,
                    SubmissionStatus.REPLACED,
                    SubmissionStatus.VALIDATION_SUCCEEDED),
                null,
                null,
                null,
                List.of(ClaimStatus.READY_TO_PROCESS, ClaimStatus.VALID, ClaimStatus.INVALID)),
            Pageable.ofSize(10).withPage(0));

    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent().getFirst())
        .usingRecursiveComparison()
        .ignoringFields(IGNORE_FIELD_SUBMISSION, IGNORE_FIELD_CREATED_ON, IGNORE_FIELD_UPDATED_ON)
        .isEqualTo(claim3);
    assertThat(result.getContent().getFirst().getCaseReferenceNumber()).isNull();
    assertThat(result.getContent().getFirst().getScheduleReference()).isNull();
  }

  @Test
  @DisplayName("Should only get one Claim using empty list params")
  void shouldOnlyGetOneClaimWhenUsingEmptyListParams() {
    Page<Claim> result =
        claimRepository.findAll(
            ClaimSpecification.filterBy("office3", null, List.of(), null, null, null, List.of()),
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
                List.of(ClaimStatus.READY_TO_PROCESS)),
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
        Arguments.of("office3", null, null, null, null, null, null),
        Arguments.of("office3", SUBMISSION_3_ID.toString(), null, null, null, null, null),
        Arguments.of(
            "office3",
            null,
            List.of(SubmissionStatus.VALIDATION_SUCCEEDED),
            null,
            null,
            null,
            null),
        Arguments.of("office3", null, null, "FEE_333", null, null, null),
        Arguments.of("office3", null, null, null, "UFN_333", null, null),
        Arguments.of("office3", null, null, null, null, "UCN_333", null),
        Arguments.of("office3", null, null, null, null, null, List.of(ClaimStatus.INVALID)));
  }
}
