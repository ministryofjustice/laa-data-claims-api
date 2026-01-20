package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimCase;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.MatterStart;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionMatterStart;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionOutcome;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CategoryCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetailsOffice;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetailsSchedule;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MediationType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionBase;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;

public class ClaimsDataTestUtil {

  public static final UUID SUBMISSION_ID = Uuid7.timeBasedUuid();
  public static final UUID BULK_SUBMISSION_ID = Uuid7.timeBasedUuid();
  public static final UUID CLAIM_1_ID = Uuid7.timeBasedUuid();
  public static final UUID CLIENT_1_ID = Uuid7.timeBasedUuid();
  public static final UUID MESSAGE_ID = Uuid7.timeBasedUuid();
  public static final UUID MATTER_START_ID = Uuid7.timeBasedUuid();
  public static final OffsetDateTime SUBMITTED_DATE =
      OffsetDateTime.of(2025, 5, 20, 0, 0, 0, 0, ZoneOffset.UTC);
  public static final String OFFICE_ACCOUNT_NUMBER = "OFF_123";
  public static final AreaOfLaw AREA_OF_LAW = AreaOfLaw.LEGAL_HELP;
  public static final String CRIME_SCHEDULE_NUMBER = "OFF_123/CRIME";
  public static final String SUBMISSION_PERIOD = "APR-2025";
  public static final String USER_ID = "12345";
  public static final String PROVIDER_USER_ID = "12345";
  public static final String API_USER_ID = "test-user";
  public static final String BULK_SUBMISSION_CREATED_BY_USER_ID = "a-provider-user-id";
  public static final String FEE_CODE = "FEE_123";
  public static final String UNIQUE_FILE_NUMBER = "UFN_123";
  public static final String UNIQUE_CLIENT_NUMBER = "UCN_123";
  public static final String UNIQUE_CASE_ID = "UC_ID_123";
  public static final String MATTER_TYPE_CODE = "MTC_123";
  public static final Integer LINE_NUMBER = 123;
  public static final String CASE_REFERENCE = "CASE_123";
  public static final String SCHEDULE_REFERENCE = "SCH_123";
  public static final String PROCUREMENT_AREA_CODE = "PAC_123";
  public static final String ACCESS_POINT_CODE = "APC_123";
  public static final String DELIVERY_LOCATION = "London";
  public static final String CLIENT_FORENAME = "Forename";
  public static final String CLIENT_SURNAME = "Surname";
  public static final String CLIENT_DOB = "1980-12-12";
  public static final String CLIENT_POSTCODE = "AB12 1AA";
  public static final String CLIENT_TYPE_CODE = "CT_CODE_123";
  public static final String HOME_OFFICE_CLIENT_NUMBER = "HOC_123";
  // must match application-test.yml for test-runner token
  public static final String AUTHORIZATION_TOKEN = "f67f968e-b479-4e61-b66e-f57984931e56";
  public static final String AUTHORIZATION_HEADER = "Authorization";
  public static final List<SubmissionStatus> SUBMISSION_STATUSES =
      List.of(
          SubmissionStatus.CREATED,
          SubmissionStatus.READY_FOR_VALIDATION,
          SubmissionStatus.VALIDATION_IN_PROGRESS);

  public ClaimsDataTestUtil() {
    throw new IllegalStateException("Cannot instantiate utility class");
  }

  public static GetBulkSubmission200ResponseDetailsSchedule getBulkSubmissionSchedule() {
    return new GetBulkSubmission200ResponseDetailsSchedule()
        .submissionPeriod("submissionPeriod")
        .areaOfLaw(AREA_OF_LAW.getValue())
        .scheduleNum("scheduleNum");
  }

  public static GetBulkSubmission200ResponseDetailsOffice getBulkSubmissionOffice() {
    return new GetBulkSubmission200ResponseDetailsOffice().account("account");
  }

  public static BulkSubmissionOutcome getBulkSubmissionOutcome(Boolean expectedValue) {
    return new BulkSubmissionOutcome()
        .matterType("matterType")
        .feeCode("feeCode")
        .caseRefNumber("caseRefNumber")
        .caseStartDate("01/01/2000")
        .caseId("caseId")
        .caseStageLevel("caseStageLevel")
        .ufn("ufn")
        .procurementArea("procurementArea")
        .accessPoint("accessPoint")
        .clientForename("clientForename")
        .clientSurname("clientSurname")
        .clientDateOfBirth("02/01/2000")
        .ucn("ucn")
        .claRefNumber("claRefNumber")
        .claExemption("claExemption")
        .gender("gender")
        .ethnicity("ethnicity")
        .disability("disability")
        .clientPostCode("clientPostcode")
        .workConcludedDate("03/01/2000")
        .adviceTime(1)
        .travelTime(2)
        .waitingTime(3)
        .profitCost(new BigDecimal("0.01"))
        .valueOfCosts(new BigDecimal("0.02"))
        .disbursementsAmount(new BigDecimal("0.03"))
        .counselCost(new BigDecimal("0.04"))
        .disbursementsVat(new BigDecimal("0.05"))
        .travelWaitingCosts(new BigDecimal("0.06"))
        .vatIndicator(expectedValue)
        .londonNonlondonRate(expectedValue)
        .clientType("clientType")
        .toleranceIndicator(expectedValue)
        .travelCosts(new BigDecimal("0.07"))
        .outcomeCode("outcomeCode")
        .legacyCase(expectedValue)
        .claimType("claimType")
        .adjournedHearingFee(8)
        .typeOfAdvice("typeOfAdvice")
        .postalApplAccp(expectedValue)
        .scheduleRef("scheduleRef")
        .cmrhOral("cmrhOral")
        .cmrhTelephone("cmrhTelephone")
        .aitHearingCentre("aitHearingCentre")
        .substantiveHearing(expectedValue)
        .hoInterview(8)
        .hoUcn("hoUcn")
        .transferDate("04/01/2000")
        .detentionTravelWaitingCosts(new BigDecimal("0.09"))
        .deliveryLocation("deliveryLocation")
        .priorAuthorityRef("priorAuthorityRef")
        .jrFormFilling(new BigDecimal("12.34"))
        .additionalTravelPayment(expectedValue)
        .meetingsAttended("meetingsAttended")
        .medicalReportsClaimed(4)
        .desiAccRep(5)
        .mhtRefNumber("mhtRefNumber")
        .stageReached("stageReached")
        .followOnWork("followOnWork")
        .nationalRefMechanismAdvice(expectedValue)
        .exemptionCriteriaSatisfied("exemptionCriteriaSatisfied")
        .exclCaseFundingRef("exclCaseFundingRef")
        .noOfClients(6)
        .noOfSurgeryClients(7)
        .ircSurgery(expectedValue)
        .surgeryDate("05/01/2000")
        .lineNumber("lineNumber")
        .crimeMatterType("crimeMatterType")
        .feeScheme("feeScheme")
        .repOrderDate("06/01/2000")
        .noOfSuspects(8)
        .noOfPoliceStation(9)
        .policeStation("policeStation")
        .dsccNumber("dsccNumber")
        .maatId("maatId")
        .dutySolicitor(expectedValue)
        .youthCourt(expectedValue)
        .schemeId("schemeId")
        .numberOfMediationSessions(10)
        .mediationTime(11)
        .outreach("outreach")
        .referral("referral")
        .clientLegallyAided(expectedValue)
        .client2Forename("client2Forename")
        .client2Surname("client2Surname")
        .client2DateOfBirth("07/01/2000")
        .client2Ucn("client2Ucn")
        .client2PostCode("client2Postcode")
        .client2Gender("client2Gender")
        .client2Ethnicity("client2Ethnicity")
        .client2Disability("client2Disability")
        .client2LegallyAided(expectedValue)
        .uniqueCaseId("uniqueCaseId")
        .standardFeeCat("standardFeeCat")
        .client2PostalApplAccp(expectedValue)
        .costsDamagesRecovered(new BigDecimal("56.78"))
        .eligibleClient(expectedValue)
        .courtLocationHpcds("courtLocationHpcds")
        .localAuthorityNumber("localAuthorityNumber")
        .paNumber("paNumber")
        .excessTravelCosts(new BigDecimal("0.10"))
        .medConcludedDate("08/01/2000");
  }

  public static BulkSubmissionMatterStart getBulkSubmissionMatterStart() {
    return new BulkSubmissionMatterStart()
        .scheduleRef("scheduleRef")
        .categoryCode(CategoryCode.HOU)
        .procurementArea("procurementArea")
        .accessPoint("accessPoint")
        .deliveryLocation("deliveryLocation")
        .numberOfMatterStarts(3);
  }

  public static List<Map<String, String>> getImmigrationClrRows() {
    return List.of(Map.of("CLR_FIELD", "value", "CLR_FIELD2", "value2"));
  }

  public static ValidationMessageLog getValidationMessage(
      ValidationMessageType validationMessageType) {
    ValidationMessageLog validationMessageLog = new ValidationMessageLog();
    validationMessageLog.setId(MESSAGE_ID);
    validationMessageLog.setClaimId(CLAIM_1_ID);
    validationMessageLog.setSubmissionId(SUBMISSION_ID);
    validationMessageLog.setType(validationMessageType);
    validationMessageLog.setSource("data-claims-api");
    validationMessageLog.setDisplayMessage("Display message");
    validationMessageLog.setTechnicalMessage("Technical message");
    validationMessageLog.setCreatedOn(SUBMITTED_DATE.toInstant());
    return validationMessageLog;
  }

  public static Submission getSubmission() {
    return Submission.builder()
        .id(SUBMISSION_ID)
        .bulkSubmissionId(BULK_SUBMISSION_ID)
        .officeAccountNumber(OFFICE_ACCOUNT_NUMBER)
        .submissionPeriod(SUBMISSION_PERIOD)
        .areaOfLaw(AREA_OF_LAW)
        .status(SubmissionStatus.CREATED)
        .crimeLowerScheduleNumber(CRIME_SCHEDULE_NUMBER)
        .previousSubmissionId(SUBMISSION_ID)
        .isNilSubmission(false)
        .numberOfClaims(5)
        .createdOn(SUBMITTED_DATE.toInstant())
        .createdByUserId(USER_ID)
        .providerUserId(PROVIDER_USER_ID)
        .legalHelpSubmissionReference("ABC")
        .mediationSubmissionReference("ABC")
        .build();
  }

  public static SubmissionBase getSubmissionBase() {
    return SubmissionBase.builder()
        .submissionId(SUBMISSION_ID)
        .bulkSubmissionId(BULK_SUBMISSION_ID)
        .officeAccountNumber(OFFICE_ACCOUNT_NUMBER)
        .submissionPeriod(SUBMISSION_PERIOD)
        .areaOfLaw(AREA_OF_LAW)
        .status(SubmissionStatus.CREATED)
        .crimeLowerScheduleNumber(CRIME_SCHEDULE_NUMBER)
        .previousSubmissionId(SUBMISSION_ID)
        .isNilSubmission(false)
        .numberOfClaims(5)
        .submitted(SUBMITTED_DATE)
        .build();
  }

  public static Claim getClaim() {
    return Claim.builder()
        .id(CLAIM_1_ID)
        .submission(getSubmission())
        .scheduleReference(SCHEDULE_REFERENCE)
        .caseReferenceNumber(CASE_REFERENCE)
        .uniqueFileNumber(UNIQUE_FILE_NUMBER)
        .lineNumber(LINE_NUMBER)
        .matterTypeCode(MATTER_TYPE_CODE)
        .crimeMatterTypeCode(MATTER_TYPE_CODE)
        .dsccNumber("ABC")
        .caseStartDate(LocalDate.now().minusDays(365))
        .caseConcludedDate(LocalDate.now().minusDays(30))
        .feeCode(FEE_CODE)
        .feeSchemeCode("ABC")
        .dutySolicitor(false)
        .youthCourt(false)
        .maatId("ABC")
        .mediationSessionsCount(1)
        .mediationTimeMinutes(1)
        .outreachLocation("ABC")
        .policeStationCourtAttendancesCount(1)
        .policeStationCourtPrisonId("ABC")
        .prisonLawPriorApprovalNumber("ABC")
        .referralSource("ABC")
        .representationOrderDate(LocalDate.of(2023, 1, 1))
        .schemeId("ABC")
        .suspectsDefendantsCount(1)
        .status(ClaimStatus.READY_TO_PROCESS)
        .procurementAreaCode(PROCUREMENT_AREA_CODE)
        .accessPointCode(ACCESS_POINT_CODE)
        .deliveryLocation(DELIVERY_LOCATION)
        .createdByUserId(USER_ID)
        .createdOn(SUBMITTED_DATE.toInstant())
        .build();
  }

  public static Client getClient() {
    return Client.builder()
        .id(CLIENT_1_ID)
        .claim(getClaim())
        .clientForename(CLIENT_FORENAME)
        .clientSurname(CLIENT_SURNAME)
        .disabilityCode("123")
        .ethnicityCode("123")
        .genderCode("M")
        .clientDateOfBirth(LocalDate.parse(CLIENT_DOB))
        .uniqueClientNumber(UNIQUE_CLIENT_NUMBER)
        .clientPostcode(CLIENT_POSTCODE)
        .isLegallyAided(true)
        .clientTypeCode(CLIENT_TYPE_CODE)
        .homeOfficeClientNumber(HOME_OFFICE_CLIENT_NUMBER)
        .createdByUserId(USER_ID)
        .createdOn(SUBMITTED_DATE.toInstant())
        .client2DateOfBirth(LocalDate.parse(CLIENT_DOB))
        .client2Forename(CLIENT_FORENAME)
        .client2Surname(CLIENT_SURNAME)
        .client2DisabilityCode("123")
        .client2EthnicityCode("123")
        .client2GenderCode("M")
        .client2IsLegallyAided(true)
        .client2Postcode(CLIENT_POSTCODE)
        .client2Ucn(UNIQUE_CLIENT_NUMBER)
        .claReferenceNumber("ABC")
        .claExemptionCode("ABC")
        .build();
  }

  public static ClaimResponse getClaimResponse() {
    return ClaimResponse.builder()
        .id(String.valueOf(CLAIM_1_ID))
        .submissionId(String.valueOf(SUBMISSION_ID))
        .scheduleReference(SCHEDULE_REFERENCE)
        .caseReferenceNumber(CASE_REFERENCE)
        .uniqueFileNumber(UNIQUE_FILE_NUMBER)
        .caseStartDate(String.valueOf(LocalDate.now().minusDays(365)))
        .caseConcludedDate(String.valueOf(LocalDate.now().minusDays(30)))
        .feeCode(FEE_CODE)
        .procurementAreaCode(PROCUREMENT_AREA_CODE)
        .accessPointCode(ACCESS_POINT_CODE)
        .deliveryLocation(DELIVERY_LOCATION)
        .clientForename(CLIENT_FORENAME)
        .clientSurname(CLIENT_SURNAME)
        .clientDateOfBirth(CLIENT_DOB)
        .uniqueClientNumber(UNIQUE_CLIENT_NUMBER)
        .clientPostcode(CLIENT_POSTCODE)
        .isLegallyAided(true)
        .clientTypeCode(CLIENT_TYPE_CODE)
        .homeOfficeClientNumber(HOME_OFFICE_CLIENT_NUMBER)
        .submissionPeriod(SUBMISSION_PERIOD)
        .isPostalApplicationAccepted(true)
        .isClient2PostalApplicationAccepted(true)
        .genderCode("M")
        .isDutySolicitor(true)
        .isLegacyCase(false)
        .isNrmAdvice(false)
        .isYouthCourt(false)
        .maatId("123")
        .mediationSessionsCount(1)
        .mediationTimeMinutes(1)
        .mentalHealthTribunalReference("ABC")
        .outcomeCode("ABC")
        .outreachLocation("ABC")
        .policeStationCourtAttendancesCount(1)
        .policeStationCourtPrisonId("ABC")
        .prisonLawPriorApprovalNumber("ABC")
        .referralSource("ABC")
        .representationOrderDate("ABC")
        .schemeId("ABC")
        .stageReachedCode("ABC")
        .standardFeeCategoryCode("ABC")
        .suspectsDefendantsCount(1)
        .totalWarnings(1)
        .transferDate("ABC")
        .uniqueCaseId("ABC")
        .build();
  }

  public static CalculatedFeeDetail getCalculatedFeeDetail() {

    return CalculatedFeeDetail.builder()
        .id(UUID.randomUUID())
        .claim(getClaim())
        .feeCode("FEE001")
        .feeCodeDescription("Fee description")
        .feeType(FeeCalculationType.DISB_ONLY)
        .categoryOfLaw("LAW")
        .totalAmount(new BigDecimal("100.00"))
        .vatIndicator(Boolean.TRUE)
        .vatRateApplied(new BigDecimal("20.00"))
        .calculatedVatAmount(new BigDecimal("20.00"))
        .disbursementAmount(new BigDecimal("10.00"))
        .requestedNetDisbursementAmount(new BigDecimal("9.00"))
        .disbursementVatAmount(new BigDecimal("1.00"))
        .hourlyTotalAmount(new BigDecimal("50.00"))
        .fixedFeeAmount(new BigDecimal("30.00"))
        .netProfitCostsAmount(new BigDecimal("40.00"))
        .requestedNetProfitCostsAmount(new BigDecimal("35.00"))
        .netCostOfCounselAmount(new BigDecimal("25.00"))
        .netTravelCostsAmount(new BigDecimal("15.00"))
        .netWaitingCostsAmount(new BigDecimal("5.00"))
        .detentionTravelAndWaitingCostsAmount(new BigDecimal("3.00"))
        .jrFormFillingAmount(new BigDecimal("2.00"))
        .travelAndWaitingCostsAmount(new BigDecimal("4.00"))
        .boltOnTotalFeeAmount(new BigDecimal("6.00"))
        .boltOnAdjournedHearingCount(1)
        .boltOnAdjournedHearingFee(new BigDecimal("1.50"))
        .boltOnCmrhTelephoneCount(2)
        .boltOnCmrhTelephoneFee(new BigDecimal("2.50"))
        .boltOnCmrhOralCount(3)
        .boltOnCmrhOralFee(new BigDecimal("3.50"))
        .boltOnHomeOfficeInterviewCount(4)
        .boltOnHomeOfficeInterviewFee(new BigDecimal("4.50"))
        .boltOnSubstantiveHearingFee(new BigDecimal("7.30"))
        .escapeCaseFlag(Boolean.TRUE)
        .schemeId("SCHEME-01")
        .claimSummaryFee(getClaimSummaryFee())
        .build();
  }

  public static ClaimSummaryFee getClaimSummaryFee() {
    return ClaimSummaryFee.builder()
        .id(UUID.randomUUID())
        .adviceTime(10)
        .travelTime(20)
        .waitingTime(30)
        .netProfitCostsAmount(new BigDecimal("123.45"))
        .netDisbursementAmount(new BigDecimal("67.89"))
        .netCounselCostsAmount(new BigDecimal("10.11"))
        .disbursementsVatAmount(new BigDecimal("12.34"))
        .travelWaitingCostsAmount(new BigDecimal("15.67"))
        .netWaitingCostsAmount(new BigDecimal("18.90"))
        .isVatApplicable(Boolean.TRUE)
        .isToleranceApplicable(Boolean.FALSE)
        .priorAuthorityReference("PA-123")
        .isLondonRate(Boolean.TRUE)
        .adjournedHearingFeeAmount(5)
        .isAdditionalTravelPayment(Boolean.FALSE)
        .costsDamagesRecoveredAmount(new BigDecimal("21.00"))
        .meetingsAttendedCode("MEET1")
        .detentionTravelWaitingCostsAmount(new BigDecimal("22.00"))
        .jrFormFillingAmount(new BigDecimal("23.00"))
        .isEligibleClient(Boolean.TRUE)
        .courtLocationCode("COURT01")
        .adviceTypeCode("ADVICE01")
        .medicalReportsCount(3)
        .isIrcSurgery(Boolean.TRUE)
        .surgeryDate(LocalDate.of(2025, 1, 2))
        .surgeryClientsCount(4)
        .surgeryMattersCount(5)
        .cmrhOralCount(6)
        .cmrhTelephoneCount(7)
        .aitHearingCentreCode("AITHC01")
        .isSubstantiveHearing(Boolean.FALSE)
        .hoInterview(8)
        .localAuthorityNumber("LA-001")
        .build();
  }

  public static ClaimCase getClaimCase() {
    return ClaimCase.builder()
        .caseId("caseId")
        .caseStageCode("caseStageCode")
        .exceptionalCaseFundingReference("123ABC")
        .exemptionCriteriaSatisfied("123ABC")
        .designatedAccreditedRepresentativeCode("ABC")
        .followOnWork("ABC")
        .isPostalApplicationAccepted(false)
        .isClient2PostalApplicationAccepted(true)
        .isLegacyCase(true)
        .isNrmAdvice(true)
        .mentalHealthTribunalReference("ABC")
        .outcomeCode("ABC")
        .stageReachedCode("ABC")
        .standardFeeCategoryCode("ABC")
        .transferDate(LocalDate.of(2025, 1, 2))
        .uniqueCaseId("ABC")
        .build();
  }

  public static MatterStart getMatterStart() {
    return MatterStart.builder()
        .id(MATTER_START_ID)
        .submission(getSubmission())
        .scheduleReference("ABC")
        .categoryCode(CategoryCode.AAP.getValue())
        .procurementAreaCode("ABC")
        .accessPointCode("ABC")
        .deliveryLocation("ABC")
        .numberOfMatterStarts(1)
        .mediationType(MediationType.MDAC_ALL_ISSUES_CO)
        .createdByUserId("ABC")
        .createdOn(SUBMITTED_DATE.toInstant())
        .updatedByUserId("ABC")
        .updatedOn(SUBMITTED_DATE.toInstant())
        .build();
  }
}
