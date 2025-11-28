package uk.gov.justice.laa.dstew.payments.claimsdata.model.xml;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import uk.gov.justice.laa.dstew.payments.claimsdata.converter.XmlOutcomeDeserializer;

/**
 * Record holding submission outcome details. <br>
 * <br>
 * Uses a custom {@link XmlOutcomeDeserializer} to handle mapping of repeated named child tags to
 * Java object fields. <br>
 * <br>
 * e.g.
 *
 * <pre>{@code
 * <outcome matterType="matter type">
 *   <outcomeItem name="FEE_CODE">fee code</outcomeItem>
 *   <outcomeItem name="CASE_REF_NUMBER">case ref number</outcomeItem>
 * </outcome>
 * }</pre>
 *
 * <br>
 * <br>
 * Becomes
 *
 * <pre>{@code
 * XmlOutcome:
 *  matterType: "matter type"
 *  feeCode: "fee code"
 *  caseRefNumber: "case ref number"
 * }</pre>
 */
@JacksonXmlRootElement(localName = "outcome")
@JsonDeserialize(using = XmlOutcomeDeserializer.class)
public record XmlOutcome(
    String matterType,
    String feeCode,
    String caseRefNumber,
    String caseStartDate,
    String caseId,
    String caseStageLevel,
    String ufn,
    String procurementArea,
    String accessPoint,
    String clientForename,
    String clientSurname,
    String clientDateOfBirth,
    String ucn,
    String claRefNumber,
    String claExemption,
    String gender,
    String ethnicity,
    String disability,
    String clientPostCode,
    String workConcludedDate,
    String adviceTime,
    String travelTime,
    String waitingTime,
    String profitCost,
    String valueOfCosts,
    String disbursementsAmount,
    String counselCost,
    String disbursementsVat,
    String travelWaitingCosts,
    String vatIndicator,
    String londonNonlondonRate,
    String clientType,
    String toleranceIndicator,
    String travelCosts,
    String outcomeCode,
    String legacyCase,
    String claimType,
    String adjournedHearingFee,
    String typeOfAdvice,
    String postalApplAccp,
    String scheduleRef,
    String cmrhOral,
    String cmrhTelephone,
    String aitHearingCentre,
    String substantiveHearing,
    String hoInterview,
    String hoUcn,
    String transferDate,
    String detentionTravelWaitingCosts,
    String deliveryLocation,
    String priorAuthorityRef,
    String jrFormFilling,
    String additionalTravelPayment,
    String meetingsAttended,
    String medicalReportsClaimed,
    String desiAccRep,
    String mhtRefNumber,
    String stageReached,
    String followOnWork,
    String nationalRefMechanismAdvice,
    String exemptionCriteriaSatisfied,
    String exclCaseFundingRef,
    String noOfClients,
    String noOfSurgeryClients,
    String ircSurgery,
    String surgeryDate,
    String lineNumber,
    String crimeMatterType,
    String feeScheme,
    String repOrderDate,
    String noOfSuspects,
    String noOfPoliceStation,
    String policeStation,
    String dsccNumber,
    String maatId,
    String prisonLawPriorApproval,
    String dutySolicitor,
    String youthCourt,
    String schemeId,
    String numberOfMediationSessions,
    String mediationTime,
    String outreach,
    String referral,
    String clientLegallyAided,
    String client2Forename,
    String client2Surname,
    String client2DateOfBirth,
    String client2Ucn,
    String client2PostCode,
    String client2Gender,
    String client2Ethnicity,
    String client2Disability,
    String client2LegallyAided,
    String uniqueCaseId,
    String standardFeeCat,
    String client2PostalApplAccp,
    String costsDamagesRecovered,
    String eligibleClientIndicator,
    String courtLocationHpcds,
    String localAuthorityNumber,
    String paNumber,
    String excessTravelCosts,
    String medConcludedDate) {
  /**
   * Default constructor for XmlOutcome.
   */
  public XmlOutcome() {
    this(
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null);
  }
}
