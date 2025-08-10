package uk.gov.justice.laa.dstew.payments.claimsdata.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Record holding submission outcome details. */
public record BulkSubmissionOutcome(String matterType, String feeCode, String caseRefNumber,
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate caseStartDate, String caseId,
    String caseStageLevel, String ufn, String procurementArea, String accessPoint,
    String clientForename, String clientSurname,
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate clientDateOfBirth, String ucn,
    String claRefNumber, String claExemption, String gender, String ethnicity, String disability,
    String clientPostCode, @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate workConcludedDate,
    Integer adviceTime, Integer travelTime, Integer waitingTime, BigDecimal profitCost,
    BigDecimal valueOfCosts, BigDecimal disbursementsAmount, BigDecimal counselCost,
    BigDecimal disbursementsVat, BigDecimal travelWaitingCosts, Boolean vatIndicator,
    Boolean londonNonlondonRate, String clientType, Boolean toleranceIndicator,
    BigDecimal travelCosts, String outcomeCode, Boolean legacyCase, String claimType,
    BigDecimal adjournedHearingFee, String typeOfAdvice, Boolean postalApplAccp, String scheduleRef,
    String cmrhOral, String cmrhTelephone, String aitHearingCentre, Boolean substantiveHearing,
    String hoInterview, String hoUcn,
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate transferDate,
    BigDecimal detentionTravelWaitingCosts, String deliveryLocation, String priorAuthorityRef,
    String jrFormFilling, Boolean additionalTravelPayment, String meetingsAttended,
    Integer medicalReportsClaimed, Integer desiAccRep, String mhtRefNumber, String stageReached,
    String followOnWork, String nationalRefMechanismAdvice, String exemptionCriteriaSatisfied,
    String exclCaseFundingRef, Integer noOfClients, Integer noOfSurgeryClients, String ircSurgery,
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate surgeryDate, String lineNumber,
    String crimeMatterType, String feeScheme,
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate repOrderDate, Integer noOfSuspects,
    Integer noOfPoliceStation, String policeStation, String dsccNumber, String maatId,
    String prisonLawPriorApproval, String dutySolicitor, String youthCourt, String schemeId,
    Integer numberOfMediationSessions, Integer mediationTime, String outreach, String referral,
    String clientLegallyAided, String client2Forename, String client2Surname,
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate client2DateOfBirth, String client2Ucn,
    String client2PostCode, String client2Gender, String client2Ethnicity, String client2Disability,
    String client2LegallyAided, String uniqueCaseId, String standardFeeCat,
    String client2PostalApplAccp, String costsDamagesRecovered, String eligibleClient,
    String courtLocation, String localAuthorityNumber, String paNumber,
    BigDecimal excessTravelCosts,
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate medConcludedDate) {}
