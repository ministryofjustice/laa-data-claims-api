package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionMatterStart;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionOutcome;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetails;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetailsOffice;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetailsSchedule;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionBase;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;

public class ClaimsDataTestUtil {

  public static final String API_URI_PREFIX = "/api/v0";
  public static final UUID SUBMISSION_ID = UUID.randomUUID();
  public static final UUID SUBMISSION_1_ID = UUID.randomUUID();
  public static final UUID SUBMISSION_2_ID = UUID.randomUUID();
  public static final UUID SUBMISSION_3_ID = UUID.randomUUID();
  public static final UUID BULK_SUBMISSION_ID = UUID.randomUUID();
  public static final UUID CLAIM_1_ID = UUID.randomUUID();
  public static final UUID CLAIM_2_ID = UUID.randomUUID();
  public static final UUID CLIENT_1_ID = UUID.randomUUID();
  public static final UUID CLIENT_2_ID = UUID.randomUUID();
  public static final OffsetDateTime SUBMITTED_DATE =
      OffsetDateTime.of(2025, 5, 20, 0, 0, 0, 0, ZoneOffset.UTC);
  public static final String OFFICE_ACCOUNT_NUMBER = "OFF_123";
  public static final String AREA_OF_LAW = "CIVIL";
  public static final String SCHEDULE_NUMBER = "OFF_123/CIVIL";
  public static final String SUBMISSION_PERIOD = "APR-24";
  public static final String USER_ID = "12345";
  public static final String FEE_CODE = "FEE_123";
  public static final String UNIQUE_FILE_NUMBER = "UFN_123";
  public static final String UNIQUE_CLIENT_NUMBER = "UCN_123";
  public static final String MATTER_TYPE_CODE = "MTC_123";
  public static final Integer LINE_NUMBER = 123;
  public static final String CASE_REFERENCE = "CASE_123";
  public static final String PROCURAMENT_AREA_CODE = "PAC_123";
  public static final String ACCESS_POINT_CODE = "APC_123";
  public static final String DELIVERY_LOCATION = "London";
  public static final String CLIENT_FORENAME = "Forename";
  public static final String CLIENT_SURNAME = "Surname";
  public static final String CLIENT_DOB = "1980-12-12";
  public static final String CLIENT_POSTCODE = "AB12 1AA";
  public static final String CLIENT_TYPE_CODE = "CT_CODE_123";
  public static final String HOME_OFFICE_CLIENT_NUMBER = "HOC_123";

  public ClaimsDataTestUtil() {
    throw new IllegalStateException("Cannot instantiate utility class");
  }

  public static GetBulkSubmission200ResponseDetailsSchedule getBulkSubmissionSchedule() {
    return new GetBulkSubmission200ResponseDetailsSchedule()
        .submissionPeriod("submissionPeriod")
        .areaOfLaw("areaOfLaw")
        .scheduleNum("scheduleNum");
  }

  public static GetBulkSubmission200ResponseDetailsOffice getBulkSubmissionOffice() {
    return new GetBulkSubmission200ResponseDetailsOffice().account("account");
  }

  public static BulkSubmissionOutcome getBulkSubmissionOutcome() {
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
        .vatIndicator(Boolean.TRUE)
        .londonNonlondonRate(Boolean.FALSE)
        .clientType("clientType")
        .toleranceIndicator(Boolean.TRUE)
        .travelCosts(new BigDecimal("0.07"))
        .outcomeCode("outcomeCode")
        .legacyCase(Boolean.FALSE)
        .claimType("claimType")
        .adjournedHearingFee(8)
        .typeOfAdvice("typeOfAdvice")
        .postalApplAccp(Boolean.TRUE)
        .scheduleRef("scheduleRef")
        .cmrhOral("cmrhOral")
        .cmrhTelephone("cmrhTelephone")
        .aitHearingCentre("aitHearingCentre")
        .substantiveHearing(Boolean.FALSE)
        .hoInterview(8)
        .hoUcn("hoUcn")
        .transferDate("04/01/2000")
        .detentionTravelWaitingCosts(new BigDecimal("0.09"))
        .deliveryLocation("deliveryLocation")
        .priorAuthorityRef("priorAuthorityRef")
        .jrFormFilling("jrFormFilling")
        .additionalTravelPayment(Boolean.TRUE)
        .meetingsAttended("meetingsAttended")
        .medicalReportsClaimed(4)
        .desiAccRep(5)
        .mhtRefNumber("mhtRefNumber")
        .stageReached("stageReached")
        .followOnWork("followOnWork")
        .nationalRefMechanismAdvice("nationalRefMechanismAdvice")
        .exemptionCriteriaSatisfied("exemptionCriteriaSatisfied")
        .exclCaseFundingRef("exclCaseFundingRef")
        .noOfClients(6)
        .noOfSurgeryClients(7)
        .ircSurgery("ircSurgery")
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
        .dutySolicitor("dutySolicitor")
        .youthCourt("youthCourt")
        .schemeId("schemeId")
        .numberOfMediationSessions(10)
        .mediationTime(11)
        .outreach("outreach")
        .referral("referral")
        .clientLegallyAided("clientLegallyAided")
        .client2Forename("client2Forename")
        .client2Surname("client2Surname")
        .client2DateOfBirth("07/01/2000")
        .client2Ucn("client2Ucn")
        .client2PostCode("client2Postcode")
        .client2Gender("client2Gender")
        .client2Ethnicity("client2Ethnicity")
        .client2Disability("client2Disability")
        .client2LegallyAided("client2LegallyAided")
        .uniqueCaseId("uniqueCaseId")
        .standardFeeCat("standardFeeCat")
        .client2PostalApplAccp("client2PostalApplAccp")
        .costsDamagesRecovered("costsDamagesRecovered")
        .eligibleClient("eligibleClient")
        .courtLocation("courtLocation")
        .localAuthorityNumber("localAuthorityNumber")
        .paNumber("paNumber")
        .excessTravelCosts(new BigDecimal("0.10"))
        .medConcludedDate("08/01/2000");
  }

  public static BulkSubmissionMatterStart getBulkSubmissionMatterStart() {
    return new BulkSubmissionMatterStart()
        .scheduleRef("scheduleRef")
        .categoryCode("categoryCode")
        .procurementArea("procurementArea")
        .accessPoint("accessPoint")
        .deliveryLocation("deliveryLocation");
  }

  public static GetBulkSubmission200ResponseDetails getBulkSubmission200ResponseDetails() {
    var expectedSchedule = ClaimsDataTestUtil.getBulkSubmissionSchedule();
    var expectedOffice = ClaimsDataTestUtil.getBulkSubmissionOffice();
    var expectedOutcomes = List.of(ClaimsDataTestUtil.getBulkSubmissionOutcome());
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
        .scheduleNumber(SCHEDULE_NUMBER)
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
        .scheduleNumber(SCHEDULE_NUMBER)
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
        .scheduleReference(SCHEDULE_NUMBER)
        .caseReferenceNumber(CASE_REFERENCE)
        .uniqueFileNumber(UNIQUE_FILE_NUMBER)
        .lineNumber(LINE_NUMBER)
        .matterTypeCode(MATTER_TYPE_CODE)
        .caseStartDate(LocalDate.now().minusDays(365))
        .caseConcludedDate(LocalDate.now().minusDays(30))
        .feeCode(FEE_CODE)
        .status(ClaimStatus.READY_TO_PROCESS)
        .procurementAreaCode(PROCURAMENT_AREA_CODE)
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
        .scheduleReference(SCHEDULE_NUMBER)
        .caseReferenceNumber(CASE_REFERENCE)
        .uniqueFileNumber(UNIQUE_FILE_NUMBER)
        .caseStartDate(String.valueOf(LocalDate.now().minusDays(365)))
        .caseConcludedDate(String.valueOf(LocalDate.now().minusDays(30)))
        .feeCode(FEE_CODE)
        .procurementAreaCode(PROCURAMENT_AREA_CODE)
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
        .build();
  }
}
