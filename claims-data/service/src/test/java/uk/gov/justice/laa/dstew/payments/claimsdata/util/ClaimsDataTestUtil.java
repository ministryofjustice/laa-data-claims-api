package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionMatterStart;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionOutcome;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CategoryCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetails;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetailsOffice;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetailsSchedule;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionBase;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;

public class ClaimsDataTestUtil {

  public static final String API_URI_PREFIX = "/api/v0";
  public static final UUID SUBMISSION_ID = Uuid7.timeBasedUuid();
  public static final UUID SUBMISSION_1_ID = Uuid7.timeBasedUuid();
  public static final UUID SUBMISSION_2_ID = Uuid7.timeBasedUuid();
  public static final UUID SUBMISSION_3_ID = Uuid7.timeBasedUuid();
  public static final UUID BULK_SUBMISSION_ID = Uuid7.timeBasedUuid();
  public static final UUID CLAIM_1_ID = Uuid7.timeBasedUuid();
  public static final UUID CLAIM_2_ID = Uuid7.timeBasedUuid();
  public static final UUID CLAIM_3_ID = Uuid7.timeBasedUuid();
  public static final UUID CLAIM_4_ID = Uuid7.timeBasedUuid();
  public static final UUID CLIENT_1_ID = Uuid7.timeBasedUuid();
  public static final UUID CLIENT_2_ID = Uuid7.timeBasedUuid();
  public static final OffsetDateTime SUBMITTED_DATE =
      OffsetDateTime.of(2025, 5, 20, 0, 0, 0, 0, ZoneOffset.UTC);
  public static final String OFFICE_ACCOUNT_NUMBER = "OFF_123";
  public static final AreaOfLaw AREA_OF_LAW = AreaOfLaw.LEGAL_HELP;
  public static final String CRIME_SCHEDULE_NUMBER = "OFF_123/CRIME";
  public static final String SUBMISSION_PERIOD = "APR-2025";
  public static final String USER_ID = "12345";
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
        .jrFormFilling("jrFormFilling")
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
        .prisonLawPriorApproval("prisonLawPriorApproval")
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
        .costsDamagesRecovered("costsDamagesRecovered")
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

  public static GetBulkSubmission200ResponseDetails getBulkSubmission200ResponseDetails() {
    var expectedSchedule = ClaimsDataTestUtil.getBulkSubmissionSchedule();
    var expectedOffice = ClaimsDataTestUtil.getBulkSubmissionOffice();
    var expectedOutcomes = List.of(ClaimsDataTestUtil.getBulkSubmissionOutcome(Boolean.TRUE));
    var expectedMatterStarts = List.of(ClaimsDataTestUtil.getBulkSubmissionMatterStart());

    var expectedDetails = new GetBulkSubmission200ResponseDetails();
    expectedDetails.schedule(expectedSchedule);
    expectedDetails.office(expectedOffice);
    expectedDetails.outcomes(expectedOutcomes);
    expectedDetails.matterStarts(expectedMatterStarts);

    return expectedDetails;
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

  public static Claim.ClaimBuilder getClaimBuilder() {
    return Claim.builder()
        .id(CLAIM_1_ID)
        .submission(getSubmission())
        .scheduleReference(SCHEDULE_REFERENCE)
        .caseReferenceNumber(CASE_REFERENCE)
        .uniqueFileNumber(UNIQUE_FILE_NUMBER)
        .lineNumber(LINE_NUMBER)
        .matterTypeCode(MATTER_TYPE_CODE)
        .caseStartDate(LocalDate.now().minusDays(365))
        .caseConcludedDate(LocalDate.now().minusDays(30))
        .feeCode(FEE_CODE)
        .status(ClaimStatus.READY_TO_PROCESS)
        .procurementAreaCode(PROCUREMENT_AREA_CODE)
        .accessPointCode(ACCESS_POINT_CODE)
        .deliveryLocation(DELIVERY_LOCATION)
        .createdByUserId(USER_ID)
        .createdOn(SUBMITTED_DATE.toInstant());
  }

  public static Client.ClientBuilder getClientBuilder() {
    return Client.builder()
        .id(CLIENT_1_ID)
        .claim(getClaimBuilder().build())
        .clientForename(CLIENT_FORENAME)
        .clientSurname(CLIENT_SURNAME)
        .clientDateOfBirth(LocalDate.parse(CLIENT_DOB))
        .uniqueClientNumber(UNIQUE_CLIENT_NUMBER)
        .clientPostcode(CLIENT_POSTCODE)
        .isLegallyAided(true)
        .clientTypeCode(CLIENT_TYPE_CODE)
        .homeOfficeClientNumber(HOME_OFFICE_CLIENT_NUMBER)
        .createdByUserId(USER_ID)
        .createdOn(SUBMITTED_DATE.toInstant());
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
        .build();
  }

  public static ClaimPost getClaimPost() {
    return new ClaimPost()
        .scheduleReference("213_REF")
        .lineNumber(123)
        .status(ClaimStatus.READY_TO_PROCESS)
        .caseReferenceNumber("CASE001")
        .uniqueFileNumber("UFN123")
        .caseStartDate("01/01/2020")
        .caseConcludedDate("02/01/2020")
        .matterTypeCode("MTC")
        .crimeMatterTypeCode("CMTC")
        .feeSchemeCode("FSC")
        .feeCode("FC")
        .procurementAreaCode("PAC")
        .accessPointCode("APC")
        .deliveryLocation("DEL")
        .representationOrderDate("01/01/2020")
        .suspectsDefendantsCount(3)
        .policeStationCourtAttendancesCount(4)
        .policeStationCourtPrisonId("PSCPI")
        .dsccNumber("DSCC_123")
        .maatId("987654321L")
        .prisonLawPriorApprovalNumber("PLPAN")
        .isDutySolicitor(true)
        .isYouthCourt(false)
        .schemeId("12")
        .mediationSessionsCount(2)
        .mediationTimeMinutes(90)
        .outreachLocation("OUT_LOC")
        .referralSource("REF_SRC")
        .totalValue(new BigDecimal("1523.89"))
        .clientForename("CLIENT_1_FORENAME")
        .clientSurname("CLIENT_1_SURNAME")
        .clientDateOfBirth("12/06/1978")
        .uniqueClientNumber("UCN_CL_1")
        .clientPostcode("CLIENT_1_POSTCODE")
        .genderCode("CLIENT_1_GENDER")
        .ethnicityCode("CLIENT_1_ETHNICITY")
        .disabilityCode("CLIENT_1_DISABILITY")
        .isLegallyAided(true)
        .clientTypeCode("CL1_CODE")
        .homeOfficeClientNumber("HO_CL1_NUMBER")
        .claReferenceNumber("CL1_REF_NUMBER")
        .claExemptionCode("CL1_EX_CODE")
        .client2Forename("CLIENT_2_FORENAME")
        .client2Surname("CLIENT_2_SURNAME")
        .client2DateOfBirth("5/1/2000")
        .client2Ucn("CLIENT_2_UCN")
        .client2Postcode("CLIENT_2_POSTCODE")
        .client2GenderCode("CLIENT_2_GENDER")
        .client2EthnicityCode("CLIENT_2_ETHNICITY")
        .client2DisabilityCode("CLIENT_2_DISABILITY")
        .client2IsLegallyAided(false)
        .caseId("CASE_ID")
        .uniqueCaseId("UC_ID")
        .caseStageCode("CASE_STAGE_CODE")
        .stageReachedCode("STAGE_REACHED_CODE")
        .standardFeeCategoryCode("STD_FEE_CAT_CODE")
        .outcomeCode("OUTCOME_CODE")
        .designatedAccreditedRepresentativeCode("DAR_CODE")
        .isPostalApplicationAccepted(true)
        .isClient2PostalApplicationAccepted(true)
        .mentalHealthTribunalReference("MHT_REF")
        .isNrmAdvice(true)
        .followOnWork("FOLLOW")
        .transferDate("3/7/2024")
        .exemptionCriteriaSatisfied("ALL")
        .exceptionalCaseFundingReference("ECF_REF")
        .isLegacyCase(true)
        .adviceTime(1)
        .travelTime(2)
        .waitingTime(3)
        .netProfitCostsAmount(new BigDecimal("1240.94"))
        .netDisbursementAmount(new BigDecimal("120.23"))
        .netCounselCostsAmount(new BigDecimal("354.12"))
        .disbursementsVatAmount(new BigDecimal("243.05"))
        .travelWaitingCostsAmount(new BigDecimal("48.76"))
        .netWaitingCostsAmount(new BigDecimal("87.12"))
        .isVatApplicable(true)
        .isToleranceApplicable(false)
        .priorAuthorityReference("PA_REF")
        .isLondonRate(true)
        .adjournedHearingFeeAmount(29)
        .isAdditionalTravelPayment(true)
        .costsDamagesRecoveredAmount(new BigDecimal("240.44"))
        .meetingsAttendedCode("MA_CODE")
        .detentionTravelWaitingCostsAmount(new BigDecimal("34.57"))
        .jrFormFillingAmount(new BigDecimal("67.85"))
        .isEligibleClient(true)
        .courtLocationCode("CL_CODE")
        .adviceTypeCode("AT_CODE")
        .medicalReportsCount(34)
        .isIrcSurgery(false)
        .surgeryDate("12/10/2024")
        .surgeryClientsCount(2)
        .surgeryMattersCount(7)
        .cmrhOralCount(5)
        .cmrhTelephoneCount(8)
        .aitHearingCentreCode("AHC_CODE")
        .isSubstantiveHearing(false)
        .hoInterview(4)
        .localAuthorityNumber("LA_NUMBER")
        .createdByUserId(API_USER_ID);
  }
}
