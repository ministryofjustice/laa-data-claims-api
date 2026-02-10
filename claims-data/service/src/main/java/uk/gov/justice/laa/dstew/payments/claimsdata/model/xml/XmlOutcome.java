package uk.gov.justice.laa.dstew.payments.claimsdata.model.xml;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import uk.gov.justice.laa.dstew.payments.claimsdata.annotation.XsdDocumentation;
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
@XmlRootElement(name = "outcome")
@XmlAccessorType(XmlAccessType.FIELD)
@JacksonXmlRootElement(localName = "outcome")
@JsonDeserialize(using = XmlOutcomeDeserializer.class)
public record XmlOutcome(
    @XmlAttribute @XsdDocumentation(description = "The type of matter (e.g., MEDI:MDCS)")
        String matterType,
    @XsdDocumentation(description = "Fee code for the claim") String feeCode,
    @XsdDocumentation(description = "Case reference number") String caseRefNumber,
    @XsdDocumentation(description = "Date when the case started") String caseStartDate,
    @XsdDocumentation(description = "Case identifier") String caseId,
    @XsdDocumentation(description = "Case stage level reached") String caseStageLevel,
    @XsdDocumentation(description = "Unique File Number") String ufn,
    @XsdDocumentation(description = "Procurement area") String procurementArea,
    @XsdDocumentation(description = "Access point for service delivery") String accessPoint,
    @XsdDocumentation(description = "Client's forename") String clientForename,
    @XsdDocumentation(description = "Client's surname") String clientSurname,
    @XsdDocumentation(description = "Client's date of birth") String clientDateOfBirth,
    @XsdDocumentation(description = "Unique Client Number") String ucn,
    @XsdDocumentation(description = "Community Legal Advice reference number") String claRefNumber,
    @XsdDocumentation(description = "Exemption under CLA") String claExemption,
    @XsdDocumentation(description = "Client's gender") String gender,
    @XsdDocumentation(description = "Client's ethnicity") String ethnicity,
    @XsdDocumentation(description = "Client's disability status") String disability,
    @XsdDocumentation(description = "Client's post code") String clientPostCode,
    @XsdDocumentation(description = "Date when work concluded") String workConcludedDate,
    @XsdDocumentation(description = "Advice time in hours") String adviceTime,
    @XsdDocumentation(description = "Travel time in hours") String travelTime,
    @XsdDocumentation(description = "Waiting time in hours") String waitingTime,
    @XsdDocumentation(description = "Profit costs amount") String profitCost,
    @XsdDocumentation(description = "Value of costs") String valueOfCosts,
    @XsdDocumentation(description = "Disbursements amount") String disbursementsAmount,
    @XsdDocumentation(description = "Counsel cost") String counselCost,
    @XsdDocumentation(description = "Disbursements VAT amount") String disbursementsVat,
    @XsdDocumentation(description = "Travel and waiting costs") String travelWaitingCosts,
    @XsdDocumentation(description = "VAT indicator") String vatIndicator,
    @XsdDocumentation(description = "London/non-London rate indicator") String londonNonlondonRate,
    @XsdDocumentation(description = "Type of client") String clientType,
    @XsdDocumentation(description = "Tolerance indicator") String toleranceIndicator,
    @XsdDocumentation(description = "Travel costs amount") String travelCosts,
    @XsdDocumentation(description = "Outcome code") String outcomeCode,
    @XsdDocumentation(description = "Legacy case indicator") String legacyCase,
    @XsdDocumentation(description = "Type of claim") String claimType,
    @XsdDocumentation(description = "Adjourned hearing fee") String adjournedHearingFee,
    @XsdDocumentation(description = "Type of advice given") String typeOfAdvice,
    @XsdDocumentation(description = "Postal application acceptance") String postalApplAccp,
    @XsdDocumentation(description = "Schedule reference") String scheduleRef,
    @XsdDocumentation(description = "Case Management Review Hearing - Oral indicator")
        String cmrhOral,
    @XsdDocumentation(description = "Case Management Review Hearing - Telephone indicator")
        String cmrhTelephone,
    @XsdDocumentation(description = "Asylum and Immigration Tribunal hearing centre")
        String aitHearingCentre,
    @XsdDocumentation(description = "Substantive hearing indicator") String substantiveHearing,
    @XsdDocumentation(description = "Home Office interview indicator") String hoInterview,
    @XsdDocumentation(description = "Home Office Unique Client Number") String hoUcn,
    @XsdDocumentation(description = "Case transfer date") String transferDate,
    @XsdDocumentation(description = "Detention travel and waiting costs")
        String detentionTravelWaitingCosts,
    @XsdDocumentation(description = "Service delivery location") String deliveryLocation,
    @XsdDocumentation(description = "Prior authority reference") String priorAuthorityRef,
    @XsdDocumentation(description = "Judicial Review form filling indicator") String jrFormFilling,
    @XsdDocumentation(description = "Additional travel payment") String additionalTravelPayment,
    @XsdDocumentation(description = "Meetings attended") String meetingsAttended,
    @XsdDocumentation(description = "Medical reports claimed") String medicalReportsClaimed,
    @XsdDocumentation(description = "DESI account representative") String desiAccRep,
    @XsdDocumentation(description = "Mental Health Tribunal reference number") String mhtRefNumber,
    @XsdDocumentation(description = "Stage reached in case") String stageReached,
    @XsdDocumentation(description = "Follow-on work indicator") String followOnWork,
    @XsdDocumentation(description = "National Referral Mechanism advice given")
        String nationalRefMechanismAdvice,
    @XsdDocumentation(description = "Exemption criteria satisfied indicator")
        String exemptionCriteriaSatisfied,
    @XsdDocumentation(description = "Exclusion case funding reference") String exclCaseFundingRef,
    @XsdDocumentation(description = "Number of clients") String noOfClients,
    @XsdDocumentation(description = "Number of surgery clients") String noOfSurgeryClients,
    @XsdDocumentation(description = "Immigration and Refugee Centre surgery indicator")
        String ircSurgery,
    @XsdDocumentation(description = "Surgery date") String surgeryDate,
    @XsdDocumentation(description = "Line number in submission") String lineNumber,
    @XsdDocumentation(description = "Crime matter type") String crimeMatterType,
    @XsdDocumentation(description = "Fee scheme code") String feeScheme,
    @XsdDocumentation(description = "Representation order date") String repOrderDate,
    @XsdDocumentation(description = "Number of suspects") String noOfSuspects,
    @XsdDocumentation(description = "Number of police stations") String noOfPoliceStation,
    @XsdDocumentation(description = "Police station code") String policeStation,
    @XsdDocumentation(description = "Departure from Usual Sentence Case court reference")
        String dsccNumber,
    @XsdDocumentation(description = "MAAT (Magistrates' Court Procedural Application Tool) ID")
        String maatId,
    @XsdDocumentation(description = "Duty solicitor indicator") String dutySolicitor,
    @XsdDocumentation(description = "Youth court indicator") String youthCourt,
    @XsdDocumentation(description = "Scheme ID") String schemeId,
    @XsdDocumentation(description = "Number of mediation sessions")
        String numberOfMediationSessions,
    @XsdDocumentation(description = "Mediation time in hours") String mediationTime,
    @XsdDocumentation(description = "Outreach code") String outreach,
    @XsdDocumentation(description = "Referral code") String referral,
    @XsdDocumentation(description = "Client legally aided indicator") String clientLegallyAided,
    @XsdDocumentation(description = "Second client's forename") String client2Forename,
    @XsdDocumentation(description = "Second client's surname") String client2Surname,
    @XsdDocumentation(description = "Second client's date of birth") String client2DateOfBirth,
    @XsdDocumentation(description = "Second client's Unique Client Number") String client2Ucn,
    @XsdDocumentation(description = "Second client's post code") String client2PostCode,
    @XsdDocumentation(description = "Second client's gender") String client2Gender,
    @XsdDocumentation(description = "Second client's ethnicity") String client2Ethnicity,
    @XsdDocumentation(description = "Second client's disability status") String client2Disability,
    @XsdDocumentation(description = "Second client legally aided indicator")
        String client2LegallyAided,
    @XsdDocumentation(description = "Unique case identifier") String uniqueCaseId,
    @XsdDocumentation(description = "Standard fee category") String standardFeeCat,
    @XsdDocumentation(description = "Second client postal application acceptance")
        String client2PostalApplAccp,
    @XsdDocumentation(description = "Costs and damages recovered amount")
        String costsDamagesRecovered,
    @XsdDocumentation(description = "Eligible client indicator") String eligibleClientIndicator,
    @XsdDocumentation(description = "Court location HPCDS code") String courtLocationHpcds,
    @XsdDocumentation(description = "Local authority number") String localAuthorityNumber,
    @XsdDocumentation(description = "Procurement area number") String paNumber,
    @XsdDocumentation(description = "Excess travel costs amount") String excessTravelCosts,
    @XsdDocumentation(description = "Date when mediation concluded") String medConcludedDate) {
  /** Default constructor for XmlOutcome. */
  public XmlOutcome() {
    this(
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null);
  }
}
