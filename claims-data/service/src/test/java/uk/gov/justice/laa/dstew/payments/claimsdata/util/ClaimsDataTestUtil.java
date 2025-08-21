package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import uk.gov.justice.laa.dstew.payments.claimsdata.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ClaimsDataTestUtil {

    public static final String API_URI_PREFIX = "/api/v0";

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
            .adjournedHearingFee(new BigDecimal("0.08"))
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
            .excessTravelCosts(new BigDecimal("0.10"))
            .medConcludedDate(LocalDate.of(2000, 1, 8));
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
}
