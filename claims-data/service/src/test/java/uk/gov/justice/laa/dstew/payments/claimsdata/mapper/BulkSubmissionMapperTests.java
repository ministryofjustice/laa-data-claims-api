package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AREA_OF_LAW;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionFieldConversionException;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.XmlOffice;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.XmlOutcome;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.XmlSchedule;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.XmlSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;

@Slf4j
@ExtendWith(MockitoExtension.class)
class BulkSubmissionMapperTests {

  @InjectMocks
  private final BulkSubmissionMapper bulkSubmissionMapper = new BulkSubmissionMapperImpl();

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
    FileSubmission submission = createCsvSubmission(createCsvOutcome());

    GetBulkSubmission200ResponseDetails expected = getExpectedBulkSubmissionDetails(true);

    GetBulkSubmission200ResponseDetails actual =
        bulkSubmissionMapper.toBulkSubmissionDetails(submission);

    assertEquals(expected, actual);
  }

  @ParameterizedTest(name = "Should include field context when {0} conversion fails")
  @CsvSource({
    "adviceTime, notANumber",
    "travelTime, notANumber",
    "waitingTime, notANumber",
    "profitCost, notANumber",
    "valueOfCosts, notANumber",
    "disbursementsAmount, notANumber",
    "counselCost, notANumber",
    "disbursementsVat, notANumber",
    "travelWaitingCosts, notANumber",
    "travelCosts, notANumber",
    "adjournedHearingFee, notANumber",
    "hoInterview, notANumber",
    "detentionTravelWaitingCosts, notANumber",
    "medicalReportsClaimed, notANumber",
    "desiAccRep, notANumber",
    "noOfClients, notANumber",
    "noOfSurgeryClients, notANumber",
    "noOfSuspects, notANumber",
    "noOfPoliceStation, notANumber",
    "numberOfMediationSessions, notANumber",
    "mediationTime, notANumber",
    "excessTravelCosts, notANumber"
  })
  void shouldIncludeFieldContextWhenCsvOutcomeNumericConversionFails(
      String fieldName, String invalidValue) {
    FileSubmission submission =
        createCsvSubmission(createCsvOutcome(Map.of(fieldName, invalidValue)));

    BulkSubmissionFieldConversionException exception =
        assertThrows(
            BulkSubmissionFieldConversionException.class,
            () -> bulkSubmissionMapper.toBulkSubmissionDetails(submission));

    assertEquals(fieldName, exception.getFieldName());
    assertEquals(invalidValue, exception.getRejectedValue());
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
                    AREA_OF_LAW.getValue(),
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
                            "clientPostcode",
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
                            "8",
                            "typeOfAdvice",
                            "Y",
                            "scheduleRef",
                            "cmrhOral",
                            "cmrhTelephone",
                            "aitHearingCentre",
                            "N",
                            "8",
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
                            "client2Postcode",
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

    GetBulkSubmission200ResponseDetails actual =
        bulkSubmissionMapper.toBulkSubmissionDetails(submission);

    assertEquals(expected, actual);
  }

  private CsvSubmission createCsvSubmission(CsvOutcome outcome) {
    return new CsvSubmission(
        new CsvOffice("account"),
        new CsvSchedule("submissionPeriod", AREA_OF_LAW.getValue(), "scheduleNum"),
        List.of(outcome),
        List.of(
            new CsvMatterStarts(
                "scheduleRef",
                "procurementArea",
                "accessPoint",
                CategoryCode.HOU,
                "deliveryLocation",
                null,
                "3")),
        List.of(Map.of("CLR_FIELD", "value")));
  }

  private CsvOutcome createCsvOutcome() {
    return createCsvOutcome(Collections.emptyMap());
  }

  private CsvOutcome createCsvOutcome(Map<String, String> overrides) {
    String adviceTime = overrides.getOrDefault("adviceTime", "1");
    String travelTime = overrides.getOrDefault("travelTime", "2");
    String waitingTime = overrides.getOrDefault("waitingTime", "3");
    String profitCost = overrides.getOrDefault("profitCost", "0.01");
    String valueOfCosts = overrides.getOrDefault("valueOfCosts", "0.02");
    String disbursementsAmount = overrides.getOrDefault("disbursementsAmount", "0.03");
    String counselCost = overrides.getOrDefault("counselCost", "0.04");
    String disbursementsVat = overrides.getOrDefault("disbursementsVat", "0.05");
    String travelWaitingCosts = overrides.getOrDefault("travelWaitingCosts", "0.06");
    String travelCosts = overrides.getOrDefault("travelCosts", "0.07");
    String adjournedHearingFee = overrides.getOrDefault("adjournedHearingFee", "8");
    String hoInterview = overrides.getOrDefault("hoInterview", "8");
    String detentionTravelWaitingCosts =
        overrides.getOrDefault("detentionTravelWaitingCosts", "0.09");
    String medicalReportsClaimed = overrides.getOrDefault("medicalReportsClaimed", "4");
    String desiAccRep = overrides.getOrDefault("desiAccRep", "5");
    String noOfClients = overrides.getOrDefault("noOfClients", "6");
    String noOfSurgeryClients = overrides.getOrDefault("noOfSurgeryClients", "7");
    String noOfSuspects = overrides.getOrDefault("noOfSuspects", "8");
    String noOfPoliceStation = overrides.getOrDefault("noOfPoliceStation", "9");
    String numberOfMediationSessions = overrides.getOrDefault("numberOfMediationSessions", "10");
    String mediationTime = overrides.getOrDefault("mediationTime", "11");
    String excessTravelCosts = overrides.getOrDefault("excessTravelCosts", "0.10");
    return new CsvOutcome(
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
        "clientPostcode",
        "03/01/2000",
        adviceTime,
        travelTime,
        waitingTime,
        profitCost,
        valueOfCosts,
        disbursementsAmount,
        counselCost,
        disbursementsVat,
        travelWaitingCosts,
        "Y",
        "N",
        "clientType",
        "Y",
        travelCosts,
        "outcomeCode",
        "N",
        "claimType",
        adjournedHearingFee,
        "typeOfAdvice",
        "Y",
        "scheduleRef",
        "cmrhOral",
        "cmrhTelephone",
        "aitHearingCentre",
        "N",
        hoInterview,
        "hoUcn",
        "04/01/2000",
        detentionTravelWaitingCosts,
        "deliveryLocation",
        "priorAuthorityRef",
        "jrFormFilling",
        "Y",
        "meetingsAttended",
        medicalReportsClaimed,
        desiAccRep,
        "mhtRefNumber",
        "stageReached",
        "followOnWork",
        "nationalRefMechanismAdvice",
        "exemptionCriteriaSatisfied",
        "exclCaseFundingRef",
        noOfClients,
        noOfSurgeryClients,
        "ircSurgery",
        "05/01/2000",
        "lineNumber",
        "crimeMatterType",
        "feeScheme",
        "06/01/2000",
        noOfSuspects,
        noOfPoliceStation,
        "policeStation",
        "dsccNumber",
        "maatId",
        "prisonLawPriorApproval",
        "dutySolicitor",
        "youthCourt",
        "schemeId",
        numberOfMediationSessions,
        mediationTime,
        "outreach",
        "referral",
        "clientLegallyAided",
        "client2Forename",
        "client2Surname",
        "07/01/2000",
        "client2Ucn",
        "client2Postcode",
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
        excessTravelCosts,
        "08/01/2000");
  }

  private GetBulkSubmission200ResponseDetails getExpectedBulkSubmissionDetails(
      boolean includeMatterStarts) {
    var expectedBulkSubmissionOffice = ClaimsDataTestUtil.getBulkSubmissionOffice();
    var expectedBulkSubmissionSchedule = ClaimsDataTestUtil.getBulkSubmissionSchedule();
    var expectedBulkSubmissionOutcome = ClaimsDataTestUtil.getBulkSubmissionOutcome();
    var expectedBulkSubmissionMatterStart = ClaimsDataTestUtil.getBulkSubmissionMatterStart();
    List<Map<String, String>> expectedImmigrationClrRows =
        includeMatterStarts ? ClaimsDataTestUtil.getImmigrationClrRows() : Collections.emptyList();

    List<BulkSubmissionMatterStart> expectedMatterStarts =
        includeMatterStarts ? List.of(expectedBulkSubmissionMatterStart) : Collections.emptyList();

    return new GetBulkSubmission200ResponseDetails()
        .office(expectedBulkSubmissionOffice)
        .schedule(expectedBulkSubmissionSchedule)
        .outcomes(List.of(expectedBulkSubmissionOutcome))
        .matterStarts(expectedMatterStarts)
        .immigrationClr(expectedImmigrationClrRows);
  }
}
