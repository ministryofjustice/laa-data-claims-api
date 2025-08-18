package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.XmlOffice;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.XmlOutcome;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.XmlSchedule;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.XmlSubmission;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class BulkSubmissionMapperTests {

  @InjectMocks
  private final BulkSubmissionMapper bulkSubmissionMapper = new BulkSubmissionMapperImpl();

  @Spy
  private GlobalStringMapper globalStringMapper = new GlobalStringMapperImpl();

  @Test
  @DisplayName("Should throw an exception if the submission file type is not supported")
  void throwsException() {
    FileSubmission csvSubmission = mock(FileSubmission.class);

    assertThrows(
            IllegalArgumentException.class,
            () -> bulkSubmissionMapper.toBulkSubmissionDetails(csvSubmission),
            "Unsupported submission type");
  }

  @Test
  @DisplayName("Should map csv submission to bulk submission")
  void shouldMapCsvSubmissionToBulkSubmission() {
    FileSubmission submission =
        new CsvSubmission(
            new CsvOffice("account"),
            new CsvSchedule("submissionPeriod", "areaOfLaw", "scheduleNum"),
            List.of(
                new CsvOutcome(
                    "matterType",
                    "feeCode",
                    "caseRefNumber",
                    "01/01/2000",
                    "caseId",
                    "caseStageLevel",
                    "ufn",
                    "procurementArea",
                    "accessPoint",
                    "clientForename",
                    "clientSurname",
                    "02/01/2000",
                    "ucn",
                    "claRefNumber",
                    "claExemption",
                    "gender",
                    "ethnicity",
                    "disability",
                    "clientPostCode",
                    "03/01/2000",
                    "1",
                    "2",
                    "3",
                    "0.01",
                    "0.02",
                    "0.03",
                    "0.04",
                    "0.05",
                    "0.06",
                    "Y",
                    "N",
                    "clientType",
                    "Y",
                    "0.07",
                    "outcomeCode",
                    "N",
                    "claimType",
                    "0.08",
                    "typeOfAdvice",
                    "Y",
                    "scheduleRef",
                    "cmrhOral",
                    "cmrhTelephone",
                    "aitHearingCentre",
                    "N",
                    "hoInterview",
                    "hoUcn",
                    "04/01/2000",
                    "0.09",
                    "deliveryLocation",
                    "priorAuthorityRef",
                    "jrFormFilling",
                    "Y",
                    "meetingsAttended",
                    "4",
                    "5",
                    "mhtRefNumber",
                    "stageReached",
                    "followOnWork",
                    "nationalRefMechanismAdvice",
                    "exemptionCriteriaSatisfied",
                    "exclCaseFundingRef",
                    "6",
                    "7",
                    "ircSurgery",
                    "05/01/2000",
                    "lineNumber",
                    "crimeMatterType",
                    "feeScheme",
                    "06/01/2000",
                    "8",
                    "9",
                    "policeStation",
                    "dsccNumber",
                    "maatId",
                    "prisonLawPriorApproval",
                    "dutySolicitor",
                    "youthCourt",
                    "schemeId",
                    "10",
                    "11",
                    "outreach",
                    "referral",
                    "clientLegallyAided",
                    "client2Forename",
                    "client2Surname",
                    "07/01/2000",
                    "client2Ucn",
                    "client2PostCode",
                    "client2Gender",
                    "client2Ethnicity",
                    "client2Disability",
                    "client2LegallyAided",
                    "uniqueCaseId",
                    "standardFeeCat",
                    "client2PostalApplAccp",
                    "costsDamagesRecovered",
                    "eligibleClient",
                    "courtLocation",
                    "localAuthorityNumber",
                    "paNumber",
                    "0.10",
                    "08/01/2000")),
            List.of(
                new CsvMatterStarts(
                    "scheduleRef",
                    "procurementArea",
                    "accessPoint",
                    "categoryCode",
                    "deliveryLocation")));

    GetBulkSubmission200ResponseDetails expected = getExpectedBulkSubmissionDetails(true);

    GetBulkSubmission200ResponseDetails actual = bulkSubmissionMapper.toBulkSubmissionDetails(submission);

    assertEquals(expected, actual);
  }

  @Test
  @DisplayName("Should map xml submission to bulk submission")
  void shouldMapXmlSubmissionToBulkSubmission() {
    FileSubmission submission =
        new XmlSubmission(
            null,
            new XmlOffice(
                "account",
                new XmlSchedule(
                    "submissionPeriod",
                    "areaOfLaw",
                    "scheduleNum",
                    List.of(
                        new XmlOutcome(
                            "matterType",
                            "feeCode",
                            "caseRefNumber",
                            "01/01/2000",
                            "caseId",
                            "caseStageLevel",
                            "ufn",
                            "procurementArea",
                            "accessPoint",
                            "clientForename",
                            "clientSurname",
                            "02/01/2000",
                            "ucn",
                            "claRefNumber",
                            "claExemption",
                            "gender",
                            "ethnicity",
                            "disability",
                            "clientPostCode",
                            "03/01/2000",
                            "1",
                            "2",
                            "3",
                            "0.01",
                            "0.02",
                            "0.03",
                            "0.04",
                            "0.05",
                            "0.06",
                            "Y",
                            "N",
                            "clientType",
                            "Y",
                            "0.07",
                            "outcomeCode",
                            "N",
                            "claimType",
                            "0.08",
                            "typeOfAdvice",
                            "Y",
                            "scheduleRef",
                            "cmrhOral",
                            "cmrhTelephone",
                            "aitHearingCentre",
                            "N",
                            "hoInterview",
                            "hoUcn",
                            "04/01/2000",
                            "0.09",
                            "deliveryLocation",
                            "priorAuthorityRef",
                            "jrFormFilling",
                            "Y",
                            "meetingsAttended",
                            "4",
                            "5",
                            "mhtRefNumber",
                            "stageReached",
                            "followOnWork",
                            "nationalRefMechanismAdvice",
                            "exemptionCriteriaSatisfied",
                            "exclCaseFundingRef",
                            "6",
                            "7",
                            "ircSurgery",
                            "05/01/2000",
                            "lineNumber",
                            "crimeMatterType",
                            "feeScheme",
                            "06/01/2000",
                            "8",
                            "9",
                            "policeStation",
                            "dsccNumber",
                            "maatId",
                            "prisonLawPriorApproval",
                            "dutySolicitor",
                            "youthCourt",
                            "schemeId",
                            "10",
                            "11",
                            "outreach",
                            "referral",
                            "clientLegallyAided",
                            "client2Forename",
                            "client2Surname",
                            "07/01/2000",
                            "client2Ucn",
                            "client2PostCode",
                            "client2Gender",
                            "client2Ethnicity",
                            "client2Disability",
                            "client2LegallyAided",
                            "uniqueCaseId",
                            "standardFeeCat",
                            "client2PostalApplAccp",
                            "costsDamagesRecovered",
                            "eligibleClient",
                            "courtLocation",
                            "localAuthorityNumber",
                            "paNumber",
                            "0.10",
                            "08/01/2000")))));

    GetBulkSubmission200ResponseDetails expected = getExpectedBulkSubmissionDetails(false);

    GetBulkSubmission200ResponseDetails actual = bulkSubmissionMapper.toBulkSubmissionDetails(submission);

    assertEquals(expected, actual);
  }

  private GetBulkSubmission200ResponseDetails getExpectedBulkSubmissionDetails(boolean includeMatterStarts) {
    List<BulkSubmissionMatterStart> matterStarts =
        includeMatterStarts
            ? List.of(
                new BulkSubmissionMatterStart()
                        .scheduleRef("scheduleRef")
                        .categoryCode("categoryCode")
                        .procurementArea("procurementArea")
                        .accessPoint("accessPoint")
                        .deliveryLocation("deliveryLocation"))
            : List.of();

    return new GetBulkSubmission200ResponseDetails()
            .office(new GetBulkSubmission200ResponseDetailsOffice().account("account"))
            .schedule(new GetBulkSubmission200ResponseDetailsSchedule()
                            .submissionPeriod("submissionPeriod")
                            .areaOfLaw("areaOfLaw")
                            .scheduleNum("scheduleNum"))
            .outcomes(List.of(
                    new BulkSubmissionOutcome()
                            .matterType("matterType")
                            .feeCode("feeCode")
                            .caseRefNumber("caseRefNumber")
                            .caseStartDate(LocalDate.of(2000, 1, 1))
                            .caseId("caseId")
                            .caseStageLevel("caseStageLevel")
                            .ufn("ufn")
                            .procurementArea("procurementArea")
                            .accessPoint("accessPoint")
                            .clientForename("clientForename")
                            .clientSurname("clientSurname")
                            .clientDateOfBirth(LocalDate.of(2000, 1, 2))
                            .ucn("ucn")
                            .claRefNumber("claRefNumber")
                            .claExemption("claExemption")
                            .gender("gender")
                            .ethnicity("ethnicity")
                            .disability("disability")
                            .clientPostCode("clientPostCode")
                            .workConcludedDate(LocalDate.of(2000, 1, 3))
                            .adviceTime(1)
                            .travelTime(2)
                            .waitingTime(3)
                            .profitCost(0.01)
                            .valueOfCosts(0.02)
                            .disbursementsAmount(0.03)
                            .counselCost(0.04)
                            .disbursementsVat(0.05)
                            .travelWaitingCosts(0.06)
                            .vatIndicator(Boolean.TRUE)
                            .londonNonlondonRate(Boolean.FALSE)
                            .clientType("clientType")
                            .toleranceIndicator(Boolean.TRUE)
                            .travelCosts(0.07)
                            .outcomeCode("outcomeCode")
                            .legacyCase(Boolean.FALSE)
                            .claimType("claimType")
                            .adjournedHearingFee(0.08)
                            .typeOfAdvice("typeOfAdvice")
                            .postalApplAccp(Boolean.TRUE)
                            .scheduleRef("scheduleRef")
                            .cmrhOral("cmrhOral")
                            .cmrhTelephone("cmrhTelephone")
                            .aitHearingCentre("aitHearingCentre")
                            .substantiveHearing(Boolean.FALSE)
                            .hoInterview("hoInterview")
                            .hoUcn("hoUcn")
                            .transferDate(LocalDate.of(2000, 1, 4))
                            .detentionTravelWaitingCosts(0.09)
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
                            .surgeryDate(LocalDate.of(2000, 1, 5))
                            .lineNumber("lineNumber")
                            .crimeMatterType("crimeMatterType")
                            .feeScheme("feeScheme")
                            .repOrderDate(LocalDate.of(2000, 1, 6))
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
                            .client2DateOfBirth(LocalDate.of(2000, 1, 7))
                            .client2Ucn("client2Ucn")
                            .client2PostCode("client2PostCode")
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
                            .excessTravelCosts(0.10)
                            .medConcludedDate(LocalDate.of(2000, 1, 8))))
            .matterStarts(matterStarts);
  }
}
