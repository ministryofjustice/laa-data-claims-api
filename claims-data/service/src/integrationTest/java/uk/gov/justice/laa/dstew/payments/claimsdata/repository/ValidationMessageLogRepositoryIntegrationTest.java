package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.USER_ID;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetails;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;

@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("ValidationMessageLogRepository Integration Test")
public class ValidationMessageLogRepositoryIntegrationTest extends AbstractIntegrationTest {

  private static final UUID SUBMISSION_ID = UUID.randomUUID();
  private static final UUID CLAIM_ID_1 = UUID.randomUUID();
  private static final UUID CLAIM_ID_2 = UUID.randomUUID();
  private static final UUID VALIDATION_ID_1 = UUID.randomUUID();
  private static final UUID VALIDATION_ID_2 = UUID.randomUUID();
  private static final Instant CREATED_ON =
      LocalDate.of(2025, 9, 17).atStartOfDay().toInstant(ZoneOffset.UTC);

  @Autowired private ValidationMessageLogRepository validationMessageLogRepository;
  @Autowired private BulkSubmissionRepository bulkSubmissionRepository;
  @Autowired private SubmissionRepository submissionRepository;
  @Autowired private ClaimRepository claimRepository;
  @Autowired private ClientRepository clientRepository;

  @BeforeEach
  void setup() {
    validationMessageLogRepository.deleteAll();
    clientRepository.deleteAll();
    claimRepository.deleteAll();
    submissionRepository.deleteAll();
    bulkSubmissionRepository.deleteAll();

    var bulkSubmission =
        BulkSubmission.builder()
            .data(new GetBulkSubmission200ResponseDetails())
            .status(BulkSubmissionStatus.READY_FOR_PARSING)
            .createdByUserId(USER_ID)
            .createdOn(CREATED_ON)
            .updatedOn(CREATED_ON)
            .build();
    bulkSubmissionRepository.save(bulkSubmission);

    var submission =
        Submission.builder()
            .id(SUBMISSION_ID)
            .bulkSubmissionId(bulkSubmission.getId())
            .officeAccountNumber("OFFICE-001")
            .submissionPeriod("SEP-25")
            .areaOfLaw("CIVIL")
            .status(SubmissionStatus.CREATED)
            .scheduleNumber("OFFICE-001/CIVIL")
            .createdByUserId(USER_ID)
            .createdOn(CREATED_ON)
            .build();
    submissionRepository.save(submission);

    var claim1 =
        Claim.builder()
            .id(CLAIM_ID_1)
            .submission(submission)
            .status(ClaimStatus.INVALID.toString())
            .scheduleReference("SCHED-001")
            .lineNumber(1)
            .caseReferenceNumber("CASE-001")
            .uniqueFileNumber("UFN-001")
            .caseStartDate(LocalDate.of(2025, 8, 1))
            .caseConcludedDate(LocalDate.of(2025, 8, 10))
            .matterTypeCode("MAT1")
            .createdByUserId(USER_ID)
            .createdOn(CREATED_ON)
            .totalValue(BigDecimal.valueOf(100))
            .build();

    var claim2 =
        Claim.builder()
            .id(CLAIM_ID_2)
            .submission(submission)
            .status(ClaimStatus.VALID.toString())
            .scheduleReference("SCHED-002")
            .lineNumber(2)
            .caseReferenceNumber("CASE-002")
            .uniqueFileNumber("UFN-002")
            .caseStartDate(LocalDate.of(2025, 8, 5))
            .caseConcludedDate(LocalDate.of(2025, 8, 12))
            .matterTypeCode("MAT2")
            .createdByUserId(USER_ID)
            .createdOn(CREATED_ON)
            .totalValue(BigDecimal.valueOf(200))
            .build();

    claimRepository.saveAll(List.of(claim1, claim2));

    clientRepository.saveAll(
        List.of(
            Client.builder()
                .id(UUID.randomUUID())
                .claim(claim1)
                .clientForename("Alice")
                .clientSurname("Smith")
                .createdByUserId(USER_ID)
                .createdOn(CREATED_ON)
                .build(),
            Client.builder()
                .id(UUID.randomUUID())
                .claim(claim2)
                .clientForename("Bob")
                .clientSurname("Jones")
                .createdByUserId(USER_ID)
                .createdOn(CREATED_ON)
                .build()));

    validationMessageLogRepository.saveAll(
        List.of(
            new ValidationMessageLog(
                VALIDATION_ID_1,
                SUBMISSION_ID,
                CLAIM_ID_1,
                ValidationMessageType.ERROR,
                "SYSTEM",
                "Missing case reference",
                "Field `caseReferenceNumber` is required",
                CREATED_ON),
            new ValidationMessageLog(
                VALIDATION_ID_2,
                SUBMISSION_ID,
                CLAIM_ID_2,
                ValidationMessageType.WARNING,
                "SYSTEM",
                "Missing UFN",
                "Field `uniqueFileNumber` is required",
                CREATED_ON)));
  }

  @Test
  @DisplayName("Should count distinct claim IDs by submission ID")
  void shouldCountDistinctClaimIdsBySubmissionId() {
    long count =
        validationMessageLogRepository.countDistinctClaimIdsBySubmissionIdAndType(
            SUBMISSION_ID, null);
    assertThat(count).isEqualTo(2L);
  }

  @ParameterizedTest(name = "Should find validation messages for type={0}")
  @MethodSource("validationTypeProvider")
  @DisplayName("Should find validation messages by type")
  void shouldFindValidationMessagesByType(
      ValidationMessageType type, UUID claimId, String displayMessage) {
    Pageable pageable = PageRequest.of(0, 10);

    ValidationMessageLog probe = new ValidationMessageLog();
    probe.setSubmissionId(SUBMISSION_ID);
    probe.setClaimId(claimId);
    probe.setType(type);
    probe.setSource("SYSTEM");

    Page<ValidationMessageLog> result =
        validationMessageLogRepository.findAll(Example.of(probe), pageable);

    assertThat(result.getTotalElements()).isEqualTo(1);
    var message = result.getContent().getFirst();
    assertThat(message.getClaimId()).isEqualTo(claimId);
    assertThat(message.getSubmissionId()).isEqualTo(SUBMISSION_ID);
    assertThat(message.getType()).isEqualTo(type);
    assertThat(message.getSource()).isEqualTo("SYSTEM");
    assertThat(message.getDisplayMessage()).isEqualTo(displayMessage);
  }

  private static Stream<org.junit.jupiter.params.provider.Arguments> validationTypeProvider() {
    return Stream.of(
        org.junit.jupiter.params.provider.Arguments.of(
            ValidationMessageType.ERROR, CLAIM_ID_1, "Missing case reference"),
        org.junit.jupiter.params.provider.Arguments.of(
            ValidationMessageType.WARNING, CLAIM_ID_2, "Missing UFN"));
  }
}
