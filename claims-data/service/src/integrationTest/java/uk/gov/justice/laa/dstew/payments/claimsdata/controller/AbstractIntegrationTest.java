package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.SqsTestConfig;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/** This is used to isolate the common configuration for integration testing in a single class. */
@ActiveProfiles("test")
@SpringBootTest
@Import(SqsTestConfig.class)
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

  protected static final UUID VALIDATION_ID_1 = Uuid7.timeBasedUuid();
  protected static final UUID VALIDATION_ID_2 = Uuid7.timeBasedUuid();
  protected static final Instant CREATED_ON =
      LocalDate.of(2025, 9, 17).atStartOfDay().toInstant(ZoneOffset.UTC);

  protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Autowired protected ValidationMessageLogRepository validationMessageLogRepository;
  @Autowired protected BulkSubmissionRepository bulkSubmissionRepository;
  @Autowired protected SubmissionRepository submissionRepository;
  @Autowired protected ClaimRepository claimRepository;
  @Autowired protected ClientRepository clientRepository;
  @Autowired protected MockMvc mockMvc;

  @ServiceConnection
  static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:latest");

  static {
    postgresContainer.start();
  }

  public Submission setupSubmissionTestData() {
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
            .createdOn(CREATED_ON)
            .updatedOn(CREATED_ON)
            .build();
    bulkSubmissionRepository.save(bulkSubmission);

    var submission =
        Submission.builder()
            .id(SUBMISSION_ID)
            .bulkSubmissionId(BULK_SUBMISSION_ID)
            .officeAccountNumber("OFFICE-001")
            .submissionPeriod("SEP-25")
            .areaOfLaw("CIVIL")
            .status(SubmissionStatus.CREATED)
            .scheduleNumber("OFFICE-001/CIVIL")
            .createdByUserId(USER_ID)
            .createdOn(CREATED_ON)
            .build();
    return submissionRepository.save(submission);
  }

  public void setupClaimsTestData(Submission submission) {
    var claim1 =
        Claim.builder()
            .id(CLAIM_1_ID)
            .submission(submission)
            .status(ClaimStatus.INVALID)
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
            .id(CLAIM_2_ID)
            .submission(submission)
            .status(ClaimStatus.VALID)
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
                .id(Uuid7.timeBasedUuid())
                .claim(claim1)
                .clientForename("Alice")
                .clientSurname("Smith")
                .createdByUserId(USER_ID)
                .createdOn(CREATED_ON)
                .build(),
            Client.builder()
                .id(Uuid7.timeBasedUuid())
                .claim(claim2)
                .clientForename("Bob")
                .clientSurname("Jones")
                .createdByUserId(USER_ID)
                .createdOn(CREATED_ON)
                .build()));
  }

  public void setupValidationMessageLogTestData() {
    validationMessageLogRepository.deleteAll();
    var submission = setupSubmissionTestData();
    setupClaimsTestData(submission);

    validationMessageLogRepository.saveAll(
        List.of(
            new ValidationMessageLog(
                VALIDATION_ID_1,
                SUBMISSION_ID,
                CLAIM_1_ID,
                ValidationMessageType.ERROR,
                "SYSTEM",
                "Missing case reference",
                "Field `caseReferenceNumber` is required",
                CREATED_ON),
            new ValidationMessageLog(
                VALIDATION_ID_2,
                SUBMISSION_ID,
                CLAIM_2_ID,
                ValidationMessageType.WARNING,
                "SYSTEM",
                "Missing UFN",
                "Field `uniqueFileNumber` is required",
                CREATED_ON)));
  }
}
