package uk.gov.justice.laa.dstew.payments.claimsdata.converter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.io.IOException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionFileReadException;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.XmlOutcome;

/** Deserializer which handles deserialization of bulk submission outcomes from XML files. */
public class XmlOutcomeDeserializer extends JsonDeserializer<XmlOutcome> {
  /**
   * Deserializes the provided content into a {@link XmlOutcome} object.
   *
   * @param p Parser used for reading JSON content
   * @param ctxt Context that can be used to access information about this deserialization activity.
   * @return an {@link XmlOutcome} object representing the provided content.
   * @throws IOException when processing of the input fails.
   * @throws IllegalStateException when an unsupported outcome item has been found in the XML.
   */
  @Override
  public XmlOutcome deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    String feeCode = null;
    String caseRefNumber = null;
    String caseStartDate = null;
    String caseId = null;
    String caseStageLevel = null;
    String ufn = null;
    String procurementArea = null;
    String accessPoint = null;
    String clientForename = null;
    String clientSurname = null;
    String clientDateOfBirth = null;
    String ucn = null;
    String claRefNumber = null;
    String claExemption = null;
    String gender = null;
    String ethnicity = null;
    String disability = null;
    String clientPostCode = null;
    String workConcludedDate = null;
    String adviceTime = null;
    String travelTime = null;
    String waitingTime = null;
    String profitCost = null;
    String valueOfCosts = null;
    String disbursementsAmount = null;
    String counselCost = null;
    String disbursementsVat = null;
    String travelWaitingCosts = null;
    String vatIndicator = null;
    String londonNonlondonRate = null;
    String clientType = null;
    String toleranceIndicator = null;
    String travelCosts = null;
    String outcomeCode = null;
    String legacyCase = null;
    String claimType = null;
    String adjournedHearingFee = null;
    String typeOfAdvice = null;
    String postalApplAccp = null;
    String scheduleRef = null;
    String cmrhOral = null;
    String cmrhTelephone = null;
    String aitHearingCentre = null;
    String substantiveHearing = null;
    String hoInterview = null;
    String hoUcn = null;
    String transferDate = null;
    String detentionTravelWaitingCosts = null;
    String deliveryLocation = null;
    String priorAuthorityRef = null;
    String jrFormFilling = null;
    String additionalTravelPayment = null;
    String meetingsAttended = null;
    String medicalReportsClaimed = null;
    String desiAccRep = null;
    String mhtRefNumber = null;
    String stageReached = null;
    String followOnWork = null;
    String nationalRefMechanismAdvice = null;
    String exemptionCriteriaSatisfied = null;
    String exclCaseFundingRef = null;
    String noOfClients = null;
    String noOfSurgeryClients = null;
    String ircSurgery = null;
    String surgeryDate = null;
    String lineNumber = null;
    String crimeMatterType = null;
    String feeScheme = null;
    String repOrderDate = null;
    String noOfSuspects = null;
    String noOfPoliceStation = null;
    String policeStation = null;
    String dsccNumber = null;
    String maatId = null;
    String prisonLawPriorApproval = null;
    String dutySolicitor = null;
    String youthCourt = null;
    String schemeId = null;
    String numberOfMediationSessions = null;
    String mediationTime = null;
    String outreach = null;
    String referral = null;
    String clientLegallyAided = null;
    String client2Forename = null;
    String client2Surname = null;
    String client2DateOfBirth = null;
    String client2Ucn = null;
    String client2PostCode = null;
    String client2Gender = null;
    String client2Ethnicity = null;
    String client2Disability = null;
    String client2LegallyAided = null;
    String uniqueCaseId = null;
    String standardFeeCat = null;
    String client2PostalApplAccp = null;
    String costsDamagesRecovered = null;
    String eligibleClientIndicator = null;
    String courtLocationHpcds = null;
    String localAuthorityNumber = null;
    String paNumber = null;
    String excessTravelCosts = null;
    String medConcludedDate = null;

    XmlMapper mapper = (XmlMapper) p.getCodec();

    JsonNode node = mapper.readTree(p);

    String matterType = node.get("matterType").asText();

    for (JsonNode outcomeItem : node.get("outcomeItem")) {
      JsonNode nameNode = outcomeItem.get("name");
      JsonNode valueNode = outcomeItem.get("");

      if (nameNode == null) {
        throw new BulkSubmissionFileReadException(
            "Outcome item under matter type %s does not have a name.".formatted(matterType));
      }

      String name = nameNode.asText();
      String value = valueNode == null ? null : valueNode.asText();

      switch (name) {
        case "FEE_CODE" -> feeCode = value;
        case "CASE_REF_NUMBER" -> caseRefNumber = value;
        case "CASE_START_DATE" -> caseStartDate = value;
        case "CASE_ID" -> caseId = value;
        case "CASE_STAGE_LEVEL" -> caseStageLevel = value;
        case "UFN" -> ufn = value;
        case "PROCUREMENT_AREA" -> procurementArea = value;
        case "ACCESS_POINT" -> accessPoint = value;
        case "CLIENT_FORENAME" -> clientForename = value;
        case "CLIENT_SURNAME" -> clientSurname = value;
        case "CLIENT_DATE_OF_BIRTH" -> clientDateOfBirth = value;
        case "UCN" -> ucn = value;
        case "CLA_REF_NUMBER" -> claRefNumber = value;
        case "CLA_EXEMPTION" -> claExemption = value;
        case "GENDER" -> gender = value;
        case "ETHNICITY" -> ethnicity = value;
        case "DISABILITY" -> disability = value;
        case "CLIENT_POST_CODE" -> clientPostCode = value;
        case "WORK_CONCLUDED_DATE" -> workConcludedDate = value;
        case "ADVICE_TIME" -> adviceTime = value;
        case "TRAVEL_TIME" -> travelTime = value;
        case "WAITING_TIME" -> waitingTime = value;
        case "PROFIT_COST" -> profitCost = value;
        case "VALUE_OF_COSTS" -> valueOfCosts = value;
        case "DISBURSEMENTS_AMOUNT" -> disbursementsAmount = value;
        case "COUNSEL_COST" -> counselCost = value;
        case "DISBURSEMENTS_VAT" -> disbursementsVat = value;
        case "TRAVEL_WAITING_COSTS" -> travelWaitingCosts = value;
        case "VAT_INDICATOR" -> vatIndicator = value;
        case "LONDON_NONLONDON_RATE" -> londonNonlondonRate = value;
        case "CLIENT_TYPE" -> clientType = value;
        case "TOLERANCE_INDICATOR" -> toleranceIndicator = value;
        case "TRAVEL_COSTS" -> travelCosts = value;
        case "OUTCOME_CODE" -> outcomeCode = value;
        case "LEGACY_CASE" -> legacyCase = value;
        case "CLAIM_TYPE" -> claimType = value;
        case "ADJOURNED_HEARING_FEE" -> adjournedHearingFee = value;
        case "TYPE_OF_ADVICE" -> typeOfAdvice = value;
        case "POSTAL_APPL_ACCP" -> postalApplAccp = value;
        case "SCHEDULE_REF" -> scheduleRef = value;
        case "CMRH_ORAL" -> cmrhOral = value;
        case "CMRH_TELEPHONE" -> cmrhTelephone = value;
        case "AIT_HEARING_CENTRE" -> aitHearingCentre = value;
        case "SUBSTANTIVE_HEARING" -> substantiveHearing = value;
        case "HO_INTERVIEW" -> hoInterview = value;
        case "HO_UCN" -> hoUcn = value;
        case "TRANSFER_DATE" -> transferDate = value;
        case "DETENTION_TRAVEL_WAITING_COSTS" -> detentionTravelWaitingCosts = value;
        case "DELIVERY_LOCATION" -> deliveryLocation = value;
        case "PRIOR_AUTHORITY_REF" -> priorAuthorityRef = value;
        case "JR_FORM_FILLING" -> jrFormFilling = value;
        case "ADDITIONAL_TRAVEL_PAYMENT" -> additionalTravelPayment = value;
        case "MEETINGS_ATTENDED" -> meetingsAttended = value;
        case "MEDICAL_REPORTS_CLAIMED" -> medicalReportsClaimed = value;
        case "DESI_ACC_REP" -> desiAccRep = value;
        case "MHT_REF_NUMBER" -> mhtRefNumber = value;
        case "STAGE_REACHED" -> stageReached = value;
        case "FOLLOW_ON_WORK" -> followOnWork = value;
        case "NATIONAL_REF_MECHANISM_ADVICE" -> nationalRefMechanismAdvice = value;
        case "EXEMPTION_CRITERIA_SATISFIED" -> exemptionCriteriaSatisfied = value;
        case "EXCL_CASE_FUNDING_REF" -> exclCaseFundingRef = value;
        case "NO_OF_CLIENTS" -> noOfClients = value;
        case "NO_OF_SURGERY_CLIENTS" -> noOfSurgeryClients = value;
        case "IRC_SURGERY" -> ircSurgery = value;
        case "SURGERY_DATE" -> surgeryDate = value;
        case "LINE_NUMBER" -> lineNumber = value;
        case "CRIME_MATTER_TYPE" -> crimeMatterType = value;
        case "FEE_SCHEME" -> feeScheme = value;
        case "REP_ORDER_DATE" -> repOrderDate = value;
        case "NO_OF_SUSPECTS" -> noOfSuspects = value;
        case "NO_OF_POLICE_STATION" -> noOfPoliceStation = value;
        case "POLICE_STATION" -> policeStation = value;
        case "DSCC_NUMBER" -> dsccNumber = value;
        case "MAAT_ID" -> maatId = value;
        case "PRISON_LAW_PRIOR_APPROVAL" -> prisonLawPriorApproval = value;
        case "DUTY_SOLICITOR" -> dutySolicitor = value;
        case "YOUTH_COURT" -> youthCourt = value;
        case "SCHEME_ID" -> schemeId = value;
        case "NUMBER_OF_MEDIATION_SESSIONS" -> numberOfMediationSessions = value;
        case "MEDIATION_TIME" -> mediationTime = value;
        case "OUTREACH" -> outreach = value;
        case "REFERRAL" -> referral = value;
        case "CLIENT_LEGALLY_AIDED" -> clientLegallyAided = value;
        case "CLIENT2_FORENAME" -> client2Forename = value;
        case "CLIENT2_SURNAME" -> client2Surname = value;
        case "CLIENT2_DATE_OF_BIRTH" -> client2DateOfBirth = value;
        case "CLIENT2_UCN" -> client2Ucn = value;
        case "CLIENT2_POST_CODE" -> client2PostCode = value;
        case "CLIENT2_GENDER" -> client2Gender = value;
        case "CLIENT2_ETHNICITY" -> client2Ethnicity = value;
        case "CLIENT2_DISABILITY" -> client2Disability = value;
        case "CLIENT2_LEGALLY_AIDED" -> client2LegallyAided = value;
        case "UNIQUE_CASE_ID" -> uniqueCaseId = value;
        case "STANDARD_FEE_CAT" -> standardFeeCat = value;
        case "CLIENT2_POSTAL_APPL_ACCP" -> client2PostalApplAccp = value;
        case "COSTS_DAMAGES_RECOVERED" -> costsDamagesRecovered = value;
        case "ELIGIBLE_CLIENT_INDICATOR" -> eligibleClientIndicator = value;
        case "COURT_LOCATION_HPCDS" -> courtLocationHpcds = value;
        case "LOCAL_AUTHORITY_NUMBER" -> localAuthorityNumber = value;
        case "PA_NUMBER" -> paNumber = value;
        case "EXCESS_TRAVEL_COSTS" -> excessTravelCosts = value;
        case "MED_CONCLUDED_DATE" -> medConcludedDate = value;
        default -> throw new IllegalStateException("Unsupported name for outcome item: " + name);
      }
    }

    return new XmlOutcome(
        matterType,
        feeCode,
        caseRefNumber,
        caseStartDate,
        caseId,
        caseStageLevel,
        ufn,
        procurementArea,
        accessPoint,
        clientForename,
        clientSurname,
        clientDateOfBirth,
        ucn,
        claRefNumber,
        claExemption,
        gender,
        ethnicity,
        disability,
        clientPostCode,
        workConcludedDate,
        adviceTime,
        travelTime,
        waitingTime,
        profitCost,
        valueOfCosts,
        disbursementsAmount,
        counselCost,
        disbursementsVat,
        travelWaitingCosts,
        vatIndicator,
        londonNonlondonRate,
        clientType,
        toleranceIndicator,
        travelCosts,
        outcomeCode,
        legacyCase,
        claimType,
        adjournedHearingFee,
        typeOfAdvice,
        postalApplAccp,
        scheduleRef,
        cmrhOral,
        cmrhTelephone,
        aitHearingCentre,
        substantiveHearing,
        hoInterview,
        hoUcn,
        transferDate,
        detentionTravelWaitingCosts,
        deliveryLocation,
        priorAuthorityRef,
        jrFormFilling,
        additionalTravelPayment,
        meetingsAttended,
        medicalReportsClaimed,
        desiAccRep,
        mhtRefNumber,
        stageReached,
        followOnWork,
        nationalRefMechanismAdvice,
        exemptionCriteriaSatisfied,
        exclCaseFundingRef,
        noOfClients,
        noOfSurgeryClients,
        ircSurgery,
        surgeryDate,
        lineNumber,
        crimeMatterType,
        feeScheme,
        repOrderDate,
        noOfSuspects,
        noOfPoliceStation,
        policeStation,
        dsccNumber,
        maatId,
        prisonLawPriorApproval,
        dutySolicitor,
        youthCourt,
        schemeId,
        numberOfMediationSessions,
        mediationTime,
        outreach,
        referral,
        clientLegallyAided,
        client2Forename,
        client2Surname,
        client2DateOfBirth,
        client2Ucn,
        client2PostCode,
        client2Gender,
        client2Ethnicity,
        client2Disability,
        client2LegallyAided,
        uniqueCaseId,
        standardFeeCat,
        client2PostalApplAccp,
        costsDamagesRecovered,
        eligibleClientIndicator,
        courtLocationHpcds,
        localAuthorityNumber,
        paNumber,
        excessTravelCosts,
        medConcludedDate);
  }
}
