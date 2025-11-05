package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AREA_OF_LAW;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.BULK_SUBMISSION_CREATED_BY_USER_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.BULK_SUBMISSION_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CASE_REFERENCE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_2_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_3_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_4_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CRIME_SCHEDULE_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.FEE_CODE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.MATTER_TYPE_CODE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.OFFICE_ACCOUNT_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SCHEDULE_REFERENCE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_2_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_PERIOD;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMITTED_DATE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.UNIQUE_FILE_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.USER_ID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.SqsTestConfig;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimCase;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetails;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.BulkSubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimCaseRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClientRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.MatterStartRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationMessageLogRepository;
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
  protected static final String INVALID_AUTH_TOKEN = "INVALID_AUTH_TOKEN";
  protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Autowired protected ValidationMessageLogRepository validationMessageLogRepository;
  @Autowired protected BulkSubmissionRepository bulkSubmissionRepository;
  @Autowired protected SubmissionRepository submissionRepository;
  @Autowired protected ClaimRepository claimRepository;
  @Autowired protected ClaimSummaryFeeRepository claimSummaryFeeRepository;
  @Autowired protected ClientRepository clientRepository;
  @Autowired protected CalculatedFeeDetailRepository calculatedFeeDetailRepository;
  @Autowired protected MatterStartRepository matterStartRepository;
  @Autowired protected ClaimCaseRepository claimCaseRepository;
  @Autowired protected MockMvc mockMvc;

  protected BulkSubmission bulkSubmission;
  protected Submission submission1;
  protected Submission submission2;
  protected Claim claim1;
  protected Claim claim2;
  protected Claim claim3;
  protected Claim claim4;
  protected CalculatedFeeDetail calculatedFeeDetail1;
  protected CalculatedFeeDetail calculatedFeeDetail2;

  @BeforeAll
  static void beforeAll() {
    OBJECT_MAPPER.registerModule(new JavaTimeModule());
  }

  @ServiceConnection
  static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:latest");

  static {
    postgresContainer.start();
  }

  protected void clearIntegrationData() {
    validationMessageLogRepository.deleteAll();
    calculatedFeeDetailRepository.deleteAll();
    claimCaseRepository.deleteAll();
    clientRepository.deleteAll();
    claimSummaryFeeRepository.deleteAll();
    matterStartRepository.deleteAll();
    claimRepository.deleteAll();
    submissionRepository.deleteAll();
    bulkSubmissionRepository.deleteAll();
  }

  void createBulkSubmission() {
    bulkSubmission =
        BulkSubmission.builder()
            .id(BULK_SUBMISSION_ID)
            .data(new GetBulkSubmission200ResponseDetails())
            .status(BulkSubmissionStatus.READY_FOR_PARSING)
            .createdByUserId(BULK_SUBMISSION_CREATED_BY_USER_ID)
            .createdOn(CREATED_ON)
            .updatedOn(CREATED_ON)
            .build();
    bulkSubmissionRepository.save(bulkSubmission);
  }

  public Submission getSubmissionTestData() {
    clearIntegrationData();
    createBulkSubmission();

    var submission =
        Submission.builder()
            .id(SUBMISSION_ID)
            .bulkSubmissionId(BULK_SUBMISSION_ID)
            .officeAccountNumber(OFFICE_ACCOUNT_NUMBER)
            .submissionPeriod(SUBMISSION_PERIOD)
            .areaOfLaw(AREA_OF_LAW)
            .status(SubmissionStatus.CREATED)
            .crimeLowerScheduleNumber(CRIME_SCHEDULE_NUMBER)
            .createdByUserId(USER_ID)
            .createdOn(CREATED_ON)
            .providerUserId(bulkSubmission.getCreatedByUserId())
            .numberOfClaims(0)
            .build();
    return submissionRepository.save(submission);
  }

  void createSubmissionsData() {
    clearIntegrationData();
    createBulkSubmission();

    submission1 =
        Submission.builder()
            .id(SUBMISSION_1_ID)
            .bulkSubmissionId(bulkSubmission.getId())
            .officeAccountNumber("office1")
            .submissionPeriod("JAN-25")
            .areaOfLaw(AreaOfLaw.LEGAL_HELP)
            .status(SubmissionStatus.CREATED)
            .createdByUserId(USER_ID)
            .providerUserId(bulkSubmission.getCreatedByUserId())
            .numberOfClaims(0)
            .build();
    submission2 =
        Submission.builder()
            .id(SUBMISSION_2_ID)
            .bulkSubmissionId(bulkSubmission.getId())
            .officeAccountNumber("office2")
            .submissionPeriod("APR-24")
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .status(SubmissionStatus.VALIDATION_SUCCEEDED)
            .createdByUserId(USER_ID)
            .providerUserId(bulkSubmission.getCreatedByUserId())
            .build();

    submissionRepository.saveAll(List.of(submission1, submission2));
  }

  public void createClaimsTestData() {
    createSubmissionsData();

    claim1 =
        Claim.builder()
            .id(CLAIM_1_ID)
            .submission(submission1)
            .status(ClaimStatus.READY_TO_PROCESS)
            .scheduleReference(SCHEDULE_REFERENCE)
            .lineNumber(1)
            .caseReferenceNumber(CASE_REFERENCE)
            .feeCode(FEE_CODE)
            .uniqueFileNumber(UNIQUE_FILE_NUMBER)
            .caseStartDate(LocalDate.of(2025, 8, 1))
            .caseConcludedDate(LocalDate.of(2025, 8, 10))
            .matterTypeCode(MATTER_TYPE_CODE)
            .createdByUserId(USER_ID)
            .createdOn(CREATED_ON)
            .totalValue(BigDecimal.valueOf(100))
            .build();

    claim2 =
        Claim.builder()
            .id(CLAIM_2_ID)
            .submission(submission1)
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
    claim3 =
        Claim.builder()
            .id(CLAIM_3_ID)
            .submission(submission2)
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
    claim4 =
        Claim.builder()
            .id(CLAIM_4_ID)
            .submission(submission1)
            .status(ClaimStatus.READY_TO_PROCESS)
            .scheduleReference(SCHEDULE_REFERENCE)
            .lineNumber(1)
            .caseReferenceNumber(CASE_REFERENCE)
            .feeCode(FEE_CODE)
            .uniqueFileNumber(UNIQUE_FILE_NUMBER)
            .caseStartDate(LocalDate.of(2025, 8, 1))
            .caseConcludedDate(LocalDate.of(2025, 8, 10))
            .matterTypeCode(MATTER_TYPE_CODE)
            .createdByUserId(USER_ID)
            .createdOn(CREATED_ON)
            .totalValue(BigDecimal.valueOf(100))
            .build();
    claimRepository.saveAll(List.of(claim1, claim2, claim3, claim4));

    var createdDateTime = CREATED_ON.atOffset(ZoneOffset.UTC);
    var summaryFee1 =
        ClaimSummaryFee.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(claim1)
            .adviceTime(120)
            .travelTime(45)
            .waitingTime(30)
            .netProfitCostsAmount(BigDecimal.valueOf(250))
            .netDisbursementAmount(BigDecimal.valueOf(40))
            .netCounselCostsAmount(BigDecimal.valueOf(35))
            .disbursementsVatAmount(BigDecimal.valueOf(8))
            .travelWaitingCostsAmount(BigDecimal.valueOf(15))
            .netWaitingCostsAmount(BigDecimal.valueOf(12))
            .isVatApplicable(true)
            .isToleranceApplicable(false)
            .priorAuthorityReference("PA-REF-001")
            .isLondonRate(true)
            .adjournedHearingFeeAmount(2)
            .isAdditionalTravelPayment(true)
            .costsDamagesRecoveredAmount(BigDecimal.valueOf(75))
            .meetingsAttendedCode("MEET-A")
            .detentionTravelWaitingCostsAmount(BigDecimal.valueOf(11))
            .jrFormFillingAmount(BigDecimal.valueOf(9))
            .isEligibleClient(true)
            .courtLocationCode("CRT-001")
            .adviceTypeCode("ADV-001")
            .medicalReportsCount(2)
            .isIrcSurgery(false)
            .surgeryDate(LocalDate.of(2025, 7, 15))
            .surgeryClientsCount(3)
            .surgeryMattersCount(1)
            .cmrhOralCount(1)
            .cmrhTelephoneCount(0)
            .aitHearingCentreCode("AIT-001")
            .isSubstantiveHearing(true)
            .hoInterview(1)
            .localAuthorityNumber("LA-001")
            .createdByUserId(USER_ID)
            .createdOn(createdDateTime)
            .build();

    var summaryFee2 =
        ClaimSummaryFee.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(claim2)
            .adviceTime(60)
            .travelTime(30)
            .waitingTime(15)
            .netProfitCostsAmount(BigDecimal.valueOf(150))
            .netDisbursementAmount(BigDecimal.valueOf(25))
            .netCounselCostsAmount(BigDecimal.valueOf(20))
            .disbursementsVatAmount(BigDecimal.valueOf(5))
            .travelWaitingCostsAmount(BigDecimal.valueOf(10))
            .netWaitingCostsAmount(BigDecimal.valueOf(6))
            .isVatApplicable(false)
            .isToleranceApplicable(true)
            .priorAuthorityReference("PA-REF-002")
            .isLondonRate(false)
            .adjournedHearingFeeAmount(1)
            .isAdditionalTravelPayment(false)
            .costsDamagesRecoveredAmount(BigDecimal.valueOf(50))
            .meetingsAttendedCode("MEET-B")
            .detentionTravelWaitingCostsAmount(BigDecimal.valueOf(7))
            .jrFormFillingAmount(BigDecimal.valueOf(4))
            .isEligibleClient(false)
            .courtLocationCode("CRT-002")
            .adviceTypeCode("ADV-002")
            .medicalReportsCount(1)
            .isIrcSurgery(true)
            .surgeryDate(LocalDate.of(2025, 7, 20))
            .surgeryClientsCount(2)
            .surgeryMattersCount(2)
            .cmrhOralCount(0)
            .cmrhTelephoneCount(2)
            .aitHearingCentreCode("AIT-002")
            .isSubstantiveHearing(false)
            .hoInterview(2)
            .localAuthorityNumber("LA-002")
            .createdByUserId(USER_ID)
            .createdOn(createdDateTime)
            .build();

    claimSummaryFeeRepository.saveAll(List.of(summaryFee1, summaryFee2));

    calculatedFeeDetail1 =
        CalculatedFeeDetail.builder()
            .id(Uuid7.timeBasedUuid())
            .claimSummaryFee(summaryFee1)
            .claim(claim1)
            .feeCode("CALC-FEE-1")
            .feeType(FeeCalculationType.DISB_ONLY)
            .feeCodeDescription("Calculated fee for claim 1")
            .categoryOfLaw("IMMIGRATION")
            .totalAmount(BigDecimal.valueOf(125))
            .vatIndicator(true)
            .vatRateApplied(new BigDecimal("0.20"))
            .calculatedVatAmount(BigDecimal.valueOf(25))
            .disbursementAmount(BigDecimal.valueOf(15))
            .requestedNetDisbursementAmount(BigDecimal.valueOf(13))
            .disbursementVatAmount(BigDecimal.valueOf(2))
            .hourlyTotalAmount(BigDecimal.valueOf(60))
            .fixedFeeAmount(BigDecimal.valueOf(40))
            .netProfitCostsAmount(BigDecimal.valueOf(80))
            .requestedNetProfitCostsAmount(BigDecimal.valueOf(70))
            .netCostOfCounselAmount(BigDecimal.valueOf(35))
            .netTravelCostsAmount(BigDecimal.valueOf(20))
            .netWaitingCostsAmount(BigDecimal.valueOf(10))
            .detentionTravelAndWaitingCostsAmount(BigDecimal.valueOf(5))
            .jrFormFillingAmount(BigDecimal.valueOf(3))
            .travelAndWaitingCostsAmount(BigDecimal.valueOf(7))
            .boltOnTotalFeeAmount(BigDecimal.valueOf(12))
            .boltOnAdjournedHearingCount(1)
            .boltOnAdjournedHearingFee(new BigDecimal("2.5"))
            .boltOnCmrhTelephoneCount(2)
            .boltOnCmrhTelephoneFee(new BigDecimal("3.5"))
            .boltOnCmrhOralCount(1)
            .boltOnCmrhOralFee(new BigDecimal("4.5"))
            .boltOnHomeOfficeInterviewCount(1)
            .boltOnHomeOfficeInterviewFee(new BigDecimal("6.5"))
            .boltOnSubstantiveHearingFee(new BigDecimal("8.5"))
            .escapeCaseFlag(false)
            .schemeId("SCHEME-1")
            .createdByUserId(USER_ID)
            .createdOn(createdDateTime)
            .build();

    calculatedFeeDetail2 =
        CalculatedFeeDetail.builder()
            .id(Uuid7.timeBasedUuid())
            .claimSummaryFee(summaryFee2)
            .claim(claim2)
            .feeCode("CALC-FEE-2")
            .feeType(FeeCalculationType.FIXED)
            .feeCodeDescription("Calculated fee for claim 2")
            .categoryOfLaw("CRIME")
            .totalAmount(BigDecimal.valueOf(95))
            .vatIndicator(false)
            .vatRateApplied(BigDecimal.ZERO)
            .calculatedVatAmount(BigDecimal.ZERO)
            .disbursementAmount(BigDecimal.valueOf(12))
            .requestedNetDisbursementAmount(BigDecimal.valueOf(10))
            .disbursementVatAmount(BigDecimal.valueOf(1))
            .hourlyTotalAmount(BigDecimal.valueOf(40))
            .fixedFeeAmount(BigDecimal.valueOf(30))
            .netProfitCostsAmount(BigDecimal.valueOf(55))
            .requestedNetProfitCostsAmount(BigDecimal.valueOf(50))
            .netCostOfCounselAmount(BigDecimal.valueOf(18))
            .netTravelCostsAmount(BigDecimal.valueOf(12))
            .netWaitingCostsAmount(BigDecimal.valueOf(5))
            .detentionTravelAndWaitingCostsAmount(BigDecimal.valueOf(4))
            .jrFormFillingAmount(BigDecimal.valueOf(2))
            .travelAndWaitingCostsAmount(BigDecimal.valueOf(6))
            .boltOnTotalFeeAmount(BigDecimal.valueOf(9))
            .boltOnAdjournedHearingCount(0)
            .boltOnAdjournedHearingFee(BigDecimal.ZERO)
            .boltOnCmrhTelephoneCount(1)
            .boltOnCmrhTelephoneFee(new BigDecimal("1.5"))
            .boltOnCmrhOralCount(0)
            .boltOnCmrhOralFee(BigDecimal.ZERO)
            .boltOnHomeOfficeInterviewCount(0)
            .boltOnHomeOfficeInterviewFee(BigDecimal.ZERO)
            .boltOnSubstantiveHearingFee(BigDecimal.ZERO)
            .escapeCaseFlag(true)
            .schemeId("SCHEME-2")
            .createdByUserId(USER_ID)
            .createdOn(createdDateTime)
            .build();
    calculatedFeeDetailRepository.saveAll(List.of(calculatedFeeDetail1, calculatedFeeDetail2));

    clientRepository.saveAll(
        List.of(
            Client.builder()
                .id(Uuid7.timeBasedUuid())
                .claim(claim1)
                .clientForename("Alice")
                .clientSurname("Smith")
                .uniqueClientNumber("UCN_111")
                .createdByUserId(USER_ID)
                .createdOn(CREATED_ON)
                .build(),
            Client.builder()
                .id(Uuid7.timeBasedUuid())
                .claim(claim3)
                .clientForename("Bob")
                .clientSurname("Jones")
                .uniqueClientNumber("UCN_333")
                .createdByUserId(USER_ID)
                .createdOn(CREATED_ON)
                .build()));

    claimCaseRepository.saveAll(
        List.of(
            ClaimCase.builder()
                .id(Uuid7.timeBasedUuid())
                .claim(claim1)
                .caseId("CASE_ID_1")
                .uniqueCaseId("UC_ID_1")
                .caseStageCode("CASE_STAGE_CODE")
                .stageReachedCode("STAGE_REACHED_CODE")
                .standardFeeCategoryCode("STD_FEE_CAT_CODE_1")
                .outcomeCode("OUTCOME_CODE_1")
                .designatedAccreditedRepresentativeCode("DAR_CODE_1")
                .isPostalApplicationAccepted(true)
                .isClient2PostalApplicationAccepted(true)
                .mentalHealthTribunalReference("MHT_REF_1")
                .isNrmAdvice(true)
                .followOnWork("FOLLOW_1")
                .transferDate(LocalDate.of(2025, 7, 20))
                .exemptionCriteriaSatisfied("ALL")
                .exceptionalCaseFundingReference("ECF_REF_1")
                .isLegacyCase(true)
                .createdByUserId(USER_ID)
                .createdOn(CREATED_ON)
                .build(),
            ClaimCase.builder()
                .id(Uuid7.timeBasedUuid())
                .claim(claim3)
                .caseId("CASE_ID_2")
                .uniqueCaseId("UC_ID_2")
                .caseStageCode("CASE_STAGE_CODE")
                .stageReachedCode("STAGE_REACHED_CODE")
                .standardFeeCategoryCode("STD_FEE_CAT_CODE_2")
                .outcomeCode(null)
                .designatedAccreditedRepresentativeCode("DAR_CODE_2")
                .isPostalApplicationAccepted(false)
                .isClient2PostalApplicationAccepted(false)
                .mentalHealthTribunalReference("MHT_REF_2")
                .isNrmAdvice(false)
                .followOnWork("FOLLOW_2")
                .transferDate(LocalDate.of(2025, 10, 20))
                .exemptionCriteriaSatisfied("ALL")
                .exceptionalCaseFundingReference("ECF_REF_2")
                .isLegacyCase(false)
                .createdByUserId(USER_ID)
                .createdOn(CREATED_ON)
                .build()));
  }

  public void createValidationMessageLogTestData() {
    validationMessageLogRepository.deleteAll();
    createClaimsTestData();

    validationMessageLogRepository.saveAll(
        List.of(
            new ValidationMessageLog(
                VALIDATION_ID_1,
                SUBMISSION_1_ID,
                CLAIM_1_ID,
                ValidationMessageType.ERROR,
                "SYSTEM",
                "Missing case reference",
                "Field `caseReferenceNumber` is required",
                CREATED_ON),
            new ValidationMessageLog(
                VALIDATION_ID_2,
                SUBMISSION_1_ID,
                CLAIM_2_ID,
                ValidationMessageType.WARNING,
                "SYSTEM",
                "Missing UFN",
                "Field `uniqueFileNumber` is required",
                CREATED_ON)));
  }
}
