package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import static org.apache.commons.lang3.BooleanUtils.toBooleanObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionFieldConversionException;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionMatterStart;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CategoryCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetails;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvMatterStarts;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvOffice;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvOutcome;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvSchedule;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.XmlImmigrationClr;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.XmlMatterStarts;
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
        () -> bulkSubmissionMapper.toBulkSubmissionDetails(csvSubmission, false),
        "Unsupported submission type");
  }

  @ParameterizedTest(
      name = "Should map csv submission with boolean value {1} and inquests enabled: {2}")
  @CsvSource({"Y,true,true", "N,false,true", ",,true", "Y,true,false", "N,false,false", ",,false"})
  void shouldMapCsvSubmissionToBulkSubmission(
      String fieldValue, String expectedValue, boolean inquestFeatureEnabled) {
    FileSubmission submission =
        createCsvSubmission(
            createCsvOutcome(
                Collections.singletonMap("inqClientMeansTested", fieldValue), fieldValue, true));

    GetBulkSubmission200ResponseDetails expected =
        getExpectedBulkSubmissionDetails(toBooleanObject(expectedValue), inquestFeatureEnabled);

    GetBulkSubmission200ResponseDetails actual =
        bulkSubmissionMapper.toBulkSubmissionDetails(submission, inquestFeatureEnabled);

    assertEquals(expected, actual);
  }

  @ParameterizedTest(name = "Should include field context when {0} conversion fails")
  @CsvSource({
    "vatIndicator, VAT Applicable, X",
    "londonNonlondonRate, London Rate, 1",
    "toleranceIndicator, Tolerance Applicable, TRUE",
    "legacyCase, Legacy Case, yes",
    "postalApplAccp, Postal Application Accepted, false",
    "substantiveHearing, Substantive Hearing, 2",
    "additionalTravelPayment, Additional Travel Payment, no",
    "clientLegallyAided, Is Legally Aided, false",
    "client2PostalApplAccp, Client 2 Postal Application Accepted, true",
    "dutySolicitor, Duty Solicitor, false",
    "nationalRefMechanismAdvice, NRM Advice, FALSE",
    "ircSurgery, IRC Surgery, FALSE",
    "client2LegallyAided, Client 2 Legally Aided, FALSE",
    "eligibleClient, Eligible Client, X",
    "youthCourt, Youth Court, Z",
    "inqClientMeansTested, Is Client Means Tested, X"
  })
  void shouldIncludeFieldContextWhenCsvOutcomeBooleanConversionFails(
      String fieldName, String errorFieldName, String invalidValue) {
    FileSubmission submission =
        createCsvSubmission(createCsvOutcome(Map.of(fieldName, invalidValue), "Y", true));

    BulkSubmissionFieldConversionException exception =
        assertThrows(
            BulkSubmissionFieldConversionException.class,
            () -> bulkSubmissionMapper.toBulkSubmissionDetails(submission, true));

    assertEquals(errorFieldName, exception.getExceptionMessage());
    assertEquals(invalidValue, exception.getRejectedValue());
  }

  @ParameterizedTest(
      name = "Error message should be: {2} when field context when {1} conversion fails")
  @CsvSource({
    "adviceTime, notANumber,Advice Time must be a number",
    "travelTime, notANumber,Travel Time must be a number",
    "waitingTime, notANumber,Waiting Time must be a number",
    "profitCost, notANumber,Net Profit Costs Amount must be a number with no more than 2 decimal places",
    "valueOfCosts, notANumber,Net Value of Costs Amount must be a valid monetary value",
    "disbursementsAmount, notANumber,Net Disbursement Amount must be a valid monetary value",
    "counselCost, notANumber,Net Counsel Costs Amount must be a valid monetary value",
    "disbursementsVat, notANumber,Disbursements VAT Amount must be a valid monetary value",
    "travelWaitingCosts, notANumber,Net Travel Waiting Costs Amount must be a valid monetary value",
    "travelCosts, notANumber,Travel Costs Amount must be a valid monetary value",
    "adjournedHearingFee, notANumber,Adjourned Hearing Fee Amount must be between 0 and 9",
    "hoInterview, notANumber,HO Interview must be between 0 and 9",
    "detentionTravelWaitingCosts, notANumber,Detention Travel Waiting Costs Amount must be a valid monetary value",
    "medicalReportsClaimed, notANumber,Medical Reports Count must be between 0 and 10",
    "desiAccRep, notANumber,Designated Accredited Representative Code must be a number from 1 to 5",
    "noOfClients, notANumber,Surgery Clients Count must be between 1 and 20",
    "noOfSurgeryClients, notANumber,Surgery Matters Count must be between 1 and 20",
    "noOfSuspects, notANumber,Suspects Defendants Count must be less than 100",
    "noOfPoliceStation, notANumber,Police Station Court Attendances Count must be between 0 and 99",
    "numberOfMediationSessions, notANumber,Mediation Sessions Count must be less than 100",
    "mediationTime, notANumber,Mediation Time Minutes must be 99999 or less",
    "excessTravelCosts, notANumber,Excess Travel Costs Amount must be a valid monetary value",
    "jrFormFilling, notANumber,JR Form Filling Amount must be a valid monetary value",
    "costsDamagesRecovered, notANumber,Costs Damages Recovered Amount must be a valid monetary value"
  })
  void shouldIncludeErrorMessageAndFieldContextWhenCsvOutcomeNumericConversionFails(
      String fieldName, String invalidValue, String exceptionMessage) {
    FileSubmission submission =
        createCsvSubmission(createCsvOutcome(Map.of(fieldName, invalidValue), "Y", true));

    BulkSubmissionFieldConversionException exception =
        assertThrows(
            BulkSubmissionFieldConversionException.class,
            () -> bulkSubmissionMapper.toBulkSubmissionDetails(submission, false));

    assertEquals(exceptionMessage, exception.getExceptionMessage());
    assertEquals(invalidValue, exception.getRejectedValue());
  }

  @ParameterizedTest(
      name = "Should map xml submission with boolean value {1} and inquests enabled: {2}")
  @CsvSource({"Y,true,true", "N,false,true", ",,true", "Y,true,false", "N,false,false", ",,false"})
  void shouldMapXmlSubmissionToBulkSubmission(
      String fieldValue, String expectedValue, boolean inquestFeatureEnabled) {
    FileSubmission submission = createXmlSubmission(getXmlOutcomes(fieldValue, true));

    GetBulkSubmission200ResponseDetails expected =
        getExpectedBulkSubmissionDetails(toBooleanObject(expectedValue), inquestFeatureEnabled);

    GetBulkSubmission200ResponseDetails actual =
        bulkSubmissionMapper.toBulkSubmissionDetails(submission, inquestFeatureEnabled);

    assertEquals(expected, actual);
  }

  @ParameterizedTest(
      name = "Should handle invalid means-tested input for {0}, inquests enabled: {1}")
  @CsvSource({"CSV,true", "CSV,false", "XML,true", "XML,false"})
  void shouldValidateInquestMeansTestedOnlyWhenEnabled(
      String format, boolean inquestFeatureEnabled) {
    Map<String, String> overrides = Map.of("inqClientMeansTested", "X");
    FileSubmission submission =
        format.equals("CSV")
            ? createCsvSubmission(createCsvOutcome(overrides, "Y", true))
            : createXmlSubmission(List.of(createXmlOutcome(overrides, "Y")));

    if (inquestFeatureEnabled) {
      BulkSubmissionFieldConversionException exception =
          assertThrows(
              BulkSubmissionFieldConversionException.class,
              () -> bulkSubmissionMapper.toBulkSubmissionDetails(submission, true));
      assertEquals("Is Client Means Tested", exception.getExceptionMessage());
      assertEquals("X", exception.getRejectedValue());
    } else {
      var outcome =
          bulkSubmissionMapper.toBulkSubmissionDetails(submission, false).getOutcomes().getFirst();
      assertNull(outcome.getIsClientMeansTested());
      assertNull(outcome.getDeceasedForename());
      assertNull(outcome.getDeceasedSurname());
      assertNull(outcome.getDeceasedDateOfDeath());
      assertNull(outcome.getCoronersInquestReference());
      assertNull(outcome.getInterestedDepartments());
      assertEquals("matterType", outcome.getMatterType());
      assertEquals(Boolean.TRUE, outcome.getVatIndicator());
    }
  }

  private XmlSubmission createXmlSubmission(List<XmlOutcome> outcomes) {
    return new XmlSubmission(
        null,
        new XmlOffice(
            "account",
            new XmlSchedule(
                "submissionPeriod",
                AREA_OF_LAW.getValue(),
                "scheduleNum",
                outcomes,
                getXmlMatterStarts(),
                List.of(
                    new XmlImmigrationClr(Map.of("CLR_FIELD", "value", "CLR_FIELD2", "value2"))))));
  }

  @Test
  void shouldMapAllXmlGovernmentDepartmentsInOrder() {
    XmlOutcome outcome =
        createXmlOutcome(
            Map.of(
                "governmentDepartment1", "department1",
                "governmentDepartment2", "department2",
                "governmentDepartment3", "department3",
                "governmentDepartment4", "department4",
                "governmentDepartment5", "department5",
                "governmentDepartment6", "department6",
                "governmentDepartment7", "department7",
                "governmentDepartment8", "department8",
                "governmentDepartment9", "department9",
                "governmentDepartment10", "department10"),
            "Y");

    assertEquals(
        List.of(
            "department1",
            "department2",
            "department3",
            "department4",
            "department5",
            "department6",
            "department7",
            "department8",
            "department9",
            "department10"),
        bulkSubmissionMapper.toBulkSubmissionOutcome(outcome, true).getInterestedDepartments());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldInvokeXmlInterestedDepartmentsMappingOnlyWhenEnabled(boolean inquestFeatureEnabled) {
    BulkSubmissionMapper mapper = spy(new BulkSubmissionMapperImpl());
    XmlOutcome outcome = createXmlOutcome(Map.of("governmentDepartment1", "department1"), "Y");

    var actual = mapper.toBulkSubmissionOutcome(outcome, inquestFeatureEnabled);

    if (inquestFeatureEnabled) {
      assertEquals(List.of("department1"), actual.getInterestedDepartments());
    } else {
      assertNull(actual.getInterestedDepartments());
    }
    verify(mapper, times(inquestFeatureEnabled ? 1 : 0)).mapInterestedDepartments(outcome);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldInvokeCsvInterestedDepartmentsMappingOnlyWhenEnabled(boolean inquestFeatureEnabled) {
    BulkSubmissionMapper mapper = spy(new BulkSubmissionMapperImpl());
    CsvOutcome outcome = createCsvOutcome("Y", true);

    var actual = mapper.toBulkSubmissionOutcome(outcome, inquestFeatureEnabled);

    if (inquestFeatureEnabled) {
      assertEquals(
          List.of(
              "governmentDepartment1",
              "governmentDepartment2",
              "governmentDepartment3",
              "governmentDepartment4",
              "governmentDepartment5",
              "governmentDepartment6",
              "governmentDepartment7",
              "governmentDepartment8",
              "governmentDepartment9",
              "governmentDepartment10"),
          actual.getInterestedDepartments());
    } else {
      assertNull(actual.getInterestedDepartments());
    }
    verify(mapper, times(inquestFeatureEnabled ? 1 : 0)).mapInterestedDepartments(outcome);
  }

  @Test
  void shouldMapMissingXmlGovernmentDepartmentsToEmptyList() {
    assertEquals(
        List.of(),
        bulkSubmissionMapper
            .toBulkSubmissionOutcome(createXmlOutcome(Map.of(), "Y"), true)
            .getInterestedDepartments());
  }

  @Test
  void shouldOmitBlankXmlGovernmentDepartments() {
    XmlOutcome outcome =
        createXmlOutcome(
            Map.of(
                "governmentDepartment1", "first",
                "governmentDepartment2", " ",
                "governmentDepartment10", "last"),
            "Y");

    assertEquals(
        List.of("first", "last"),
        bulkSubmissionMapper.toBulkSubmissionOutcome(outcome, true).getInterestedDepartments());
  }

  @ParameterizedTest(name = "Error message should be: {2} when monetary field {0} is not valid")
  @CsvSource({
    "profitCost,notANumber,Net Profit Costs Amount must be a number with no more than 2 decimal places",
    "valueOfCosts,notANumber,Net Value of Costs Amount must be a valid monetary value",
    "disbursementsAmount, notANumber,Net Disbursement Amount must be a valid monetary value",
    "counselCost, notANumber,Net Counsel Costs Amount must be a valid monetary value",
    "disbursementsVat, notANumber,Disbursements VAT Amount must be a valid monetary value",
    "travelWaitingCosts, notANumber,Net Travel Waiting Costs Amount must be a valid monetary value",
    "travelCosts, notANumber,Travel Costs Amount must be a valid monetary value",
    "adjournedHearingFee, notANumber,Adjourned Hearing Fee Amount must be between 0 and 9",
    "jrFormFilling, notANumber,JR Form Filling Amount must be a valid monetary value",
    "costsDamagesRecovered, notANumber,Costs Damages Recovered Amount must be a valid monetary value",
    "excessTravelCosts, notANumber,Excess Travel Costs Amount must be a valid monetary value",
    "detentionTravelWaitingCosts, notANumber,Detention Travel Waiting Costs Amount must be a valid monetary value",
    "travelTime, notANumber,Travel Time must be a number",
    "waitingTime, notANumber,Waiting Time must be a number",
    "hoInterview, notANumber,HO Interview must be between 0 and 9",
    "medicalReportsClaimed, notANumber,Medical Reports Count must be between 0 and 10",
    "noOfClients, notANumber,Surgery Clients Count must be between 1 and 20",
    "noOfSurgeryClients, notANumber,Surgery Matters Count must be between 1 and 20",
    "noOfSuspects, notANumber,Suspects Defendants Count must be less than 100",
    "noOfPoliceStation, notANumber,Police Station Court Attendances Count must be between 0 and 99",
    "numberOfMediationSessions, notANumber,Mediation Sessions Count must be less than 100",
    "mediationTime, notANumber,Mediation Time Minutes must be 99999 or less",
    "desiAccRep, notANumber,Designated Accredited Representative Code must be a number from 1 to 5",
    "adviceTime, notANumber,Advice Time must be a number",
  })
  void shouldIncludeErrorMessageAndFieldContextWhenXMLOutcomeNumericConversionFails(
      String fieldName, String invalidValue, String expectedExceptionMessage) {
    var xmlOutCome = createXmlOutcome(Map.of(fieldName, invalidValue), "Y");
    var actualException =
        assertThrows(
            BulkSubmissionFieldConversionException.class,
            () -> bulkSubmissionMapper.toBulkSubmissionOutcome(xmlOutCome, true));

    assertEquals(expectedExceptionMessage, actualException.getMessage());
    assertEquals(invalidValue, actualException.getRejectedValue());
  }

  private static List<XmlMatterStarts> getXmlMatterStarts() {
    return List.of(
        new XmlMatterStarts(
            "scheduleRef",
            "procurementArea",
            "accessPoint",
            CategoryCode.HOU,
            "deliveryLocation",
            null,
            3));
  }

  private static List<XmlOutcome> getXmlOutcomes(String fieldValue, boolean inquestFeatureEnabled) {
    return List.of(
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
            fieldValue,
            fieldValue,
            "clientType",
            fieldValue,
            "0.07",
            "outcomeCode",
            fieldValue,
            "claimType",
            "8",
            "typeOfAdvice",
            fieldValue,
            "scheduleRef",
            "cmrhOral",
            "cmrhTelephone",
            "aitHearingCentre",
            fieldValue,
            "8",
            "hoUcn",
            "04/01/2000",
            "0.09",
            "deliveryLocation",
            "priorAuthorityRef",
            "12.34",
            fieldValue,
            "meetingsAttended",
            "4",
            "5",
            "mhtRefNumber",
            "stageReached",
            "followOnWork",
            fieldValue,
            "exemptionCriteriaSatisfied",
            "exclCaseFundingRef",
            "6",
            "7",
            fieldValue,
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
            fieldValue,
            fieldValue,
            "schemeId",
            "10",
            "11",
            "outreach",
            "referral",
            fieldValue,
            "client2Forename",
            "client2Surname",
            "07/01/2000",
            "client2Ucn",
            "client2Postcode",
            "client2Gender",
            "client2Ethnicity",
            "client2Disability",
            fieldValue,
            "uniqueCaseId",
            "standardFeeCat",
            fieldValue,
            "56.78",
            fieldValue,
            "courtLocationHpcds",
            "localAuthorityNumber",
            "paNumber",
            "0.10",
            "08/01/2000",
            inquestFeatureEnabled ? fieldValue : null,
            inquestFeatureEnabled ? "deceasedFirstName" : null,
            inquestFeatureEnabled ? "deceasedSurname" : null,
            inquestFeatureEnabled ? "09/01/2000" : null,
            inquestFeatureEnabled ? "inquestReferenceNumber" : null,
            inquestFeatureEnabled ? "governmentDepartment1" : null,
            inquestFeatureEnabled ? "governmentDepartment2" : null,
            inquestFeatureEnabled ? "governmentDepartment3" : null,
            inquestFeatureEnabled ? "governmentDepartment4" : null,
            inquestFeatureEnabled ? "governmentDepartment5" : null,
            inquestFeatureEnabled ? "governmentDepartment6" : null,
            inquestFeatureEnabled ? "governmentDepartment7" : null,
            inquestFeatureEnabled ? "governmentDepartment8" : null,
            inquestFeatureEnabled ? "governmentDepartment9" : null,
            inquestFeatureEnabled ? "governmentDepartment10" : null));
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
        List.of(Map.of("CLR_FIELD", "value", "CLR_FIELD2", "value2")));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldPopulateCsvFixtureInquestFieldsOnlyWhenEnabled(boolean inquestEnabledFlag) {
    CsvOutcome outcome = createCsvOutcome("Y", inquestEnabledFlag);

    assertEquals(inquestEnabledFlag ? "Y" : null, outcome.inqClientMeansTested());
    assertEquals(inquestEnabledFlag ? "deceasedFirstName" : null, outcome.deceasedFirstName());
    assertEquals(inquestEnabledFlag ? "deceasedSurname" : null, outcome.deceasedSurname());
    assertEquals(inquestEnabledFlag ? "09/01/2000" : null, outcome.dateOfDeath());
    assertEquals(inquestEnabledFlag ? "inquestReferenceNumber" : null, outcome.inquestRef());
    assertEquals(inquestEnabledFlag ? "governmentDepartment1" : null, outcome.govDept1());
    assertEquals(inquestEnabledFlag ? "governmentDepartment2" : null, outcome.govDept2());
    assertEquals(inquestEnabledFlag ? "governmentDepartment3" : null, outcome.govDept3());
    assertEquals(inquestEnabledFlag ? "governmentDepartment4" : null, outcome.govDept4());
    assertEquals(inquestEnabledFlag ? "governmentDepartment5" : null, outcome.govDept5());
    assertEquals(inquestEnabledFlag ? "governmentDepartment6" : null, outcome.govDept6());
    assertEquals(inquestEnabledFlag ? "governmentDepartment7" : null, outcome.govDept7());
    assertEquals(inquestEnabledFlag ? "governmentDepartment8" : null, outcome.govDept8());
    assertEquals(inquestEnabledFlag ? "governmentDepartment9" : null, outcome.govDept9());
    assertEquals(inquestEnabledFlag ? "governmentDepartment10" : null, outcome.govDept10());
    assertEquals("matterType", outcome.matterType());
  }

  private CsvOutcome createCsvOutcome(String fieldValue, boolean inquestEnabledFlag) {
    return createCsvOutcome(Collections.emptyMap(), fieldValue, inquestEnabledFlag);
  }

  private CsvOutcome createCsvOutcome(
      Map<String, String> overrides, String fieldValue, boolean inquestEnabledFlag) {
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
    String jrFormFilling = overrides.getOrDefault("jrFormFilling", "12.34");
    String costsDamagesRecovered = overrides.getOrDefault("costsDamagesRecovered", "56.78");
    String medicalReportsClaimed = overrides.getOrDefault("medicalReportsClaimed", "4");
    String desiAccRep = overrides.getOrDefault("desiAccRep", "5");
    String noOfClients = overrides.getOrDefault("noOfClients", "6");
    String noOfSurgeryClients = overrides.getOrDefault("noOfSurgeryClients", "7");
    String noOfSuspects = overrides.getOrDefault("noOfSuspects", "8");
    String noOfPoliceStation = overrides.getOrDefault("noOfPoliceStation", "9");
    String numberOfMediationSessions = overrides.getOrDefault("numberOfMediationSessions", "10");
    String mediationTime = overrides.getOrDefault("mediationTime", "11");
    String excessTravelCosts = overrides.getOrDefault("excessTravelCosts", "0.10");
    String vatIndicator = overrides.getOrDefault("vatIndicator", fieldValue);
    String londonNonlondonRate = overrides.getOrDefault("londonNonlondonRate", fieldValue);
    String toleranceIndicator = overrides.getOrDefault("toleranceIndicator", fieldValue);
    String legacyCase = overrides.getOrDefault("legacyCase", fieldValue);
    String postalApplAccp = overrides.getOrDefault("postalApplAccp", fieldValue);
    String substantiveHearing = overrides.getOrDefault("substantiveHearing", fieldValue);
    String additionalTravelPayment = overrides.getOrDefault("additionalTravelPayment", fieldValue);
    String clientLegallyAided = overrides.getOrDefault("clientLegallyAided", fieldValue);
    String client2PostalApplAccp = overrides.getOrDefault("client2PostalApplAccp", fieldValue);
    String dutySolicitor = overrides.getOrDefault("dutySolicitor", fieldValue);
    String nationalRefMechanismAdvice =
        overrides.getOrDefault("nationalRefMechanismAdvice", fieldValue);
    String ircSurgery = overrides.getOrDefault("ircSurgery", fieldValue);
    String client2LegallyAided = overrides.getOrDefault("client2LegallyAided", fieldValue);
    String eligibleClient = overrides.getOrDefault("eligibleClient", fieldValue);
    String youthCourt = overrides.getOrDefault("youthCourt", fieldValue);
    String inqClientMeansTested = overrides.getOrDefault("inqClientMeansTested", "Y");
    String deceasedFirstName = overrides.getOrDefault("deceasedFirstName", "deceasedFirstName");
    String deceasedSurname = overrides.getOrDefault("deceasedSurname", "deceasedSurname");
    String dateOfDeath = overrides.getOrDefault("dateOfDeath", "09/01/2000");
    String inquestReferenceNumber =
        overrides.getOrDefault("inquestReferenceNumber", "inquestReferenceNumber");
    String governmentDepartment1 =
        overrides.getOrDefault("governmentDepartment1", "governmentDepartment1");
    String governmentDepartment2 =
        overrides.getOrDefault("governmentDepartment2", "governmentDepartment2");
    String governmentDepartment3 =
        overrides.getOrDefault("governmentDepartment3", "governmentDepartment3");
    String governmentDepartment4 =
        overrides.getOrDefault("governmentDepartment4", "governmentDepartment4");
    String governmentDepartment5 =
        overrides.getOrDefault("governmentDepartment5", "governmentDepartment5");
    String governmentDepartment6 =
        overrides.getOrDefault("governmentDepartment6", "governmentDepartment6");
    String governmentDepartment7 =
        overrides.getOrDefault("governmentDepartment7", "governmentDepartment7");
    String governmentDepartment8 =
        overrides.getOrDefault("governmentDepartment8", "governmentDepartment8");
    String governmentDepartment9 =
        overrides.getOrDefault("governmentDepartment9", "governmentDepartment9");
    String governmentDepartment10 =
        overrides.getOrDefault("governmentDepartment10", "governmentDepartment10");
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
        vatIndicator,
        londonNonlondonRate,
        "clientType",
        toleranceIndicator,
        travelCosts,
        "outcomeCode",
        legacyCase,
        "claimType",
        adjournedHearingFee,
        "typeOfAdvice",
        postalApplAccp,
        "scheduleRef",
        "cmrhOral",
        "cmrhTelephone",
        "aitHearingCentre",
        substantiveHearing,
        hoInterview,
        "hoUcn",
        "04/01/2000",
        detentionTravelWaitingCosts,
        "deliveryLocation",
        "priorAuthorityRef",
        jrFormFilling,
        additionalTravelPayment,
        "meetingsAttended",
        medicalReportsClaimed,
        desiAccRep,
        "mhtRefNumber",
        "stageReached",
        "followOnWork",
        nationalRefMechanismAdvice,
        "exemptionCriteriaSatisfied",
        "exclCaseFundingRef",
        noOfClients,
        noOfSurgeryClients,
        ircSurgery,
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
        dutySolicitor,
        youthCourt,
        "schemeId",
        numberOfMediationSessions,
        mediationTime,
        "outreach",
        "referral",
        clientLegallyAided,
        "client2Forename",
        "client2Surname",
        "07/01/2000",
        "client2Ucn",
        "client2Postcode",
        "client2Gender",
        "client2Ethnicity",
        "client2Disability",
        client2LegallyAided,
        "uniqueCaseId",
        "standardFeeCat",
        client2PostalApplAccp,
        costsDamagesRecovered,
        eligibleClient,
        "courtLocationHpcds",
        "localAuthorityNumber",
        "paNumber",
        excessTravelCosts,
        "08/01/2000",
        inquestEnabledFlag ? inqClientMeansTested : null,
        inquestEnabledFlag ? deceasedFirstName : null,
        inquestEnabledFlag ? deceasedSurname : null,
        inquestEnabledFlag ? dateOfDeath : null,
        inquestEnabledFlag ? inquestReferenceNumber : null,
        inquestEnabledFlag ? governmentDepartment1 : null,
        inquestEnabledFlag ? governmentDepartment2 : null,
        inquestEnabledFlag ? governmentDepartment3 : null,
        inquestEnabledFlag ? governmentDepartment4 : null,
        inquestEnabledFlag ? governmentDepartment5 : null,
        inquestEnabledFlag ? governmentDepartment6 : null,
        inquestEnabledFlag ? governmentDepartment7 : null,
        inquestEnabledFlag ? governmentDepartment8 : null,
        inquestEnabledFlag ? governmentDepartment9 : null,
        inquestEnabledFlag ? governmentDepartment10 : null);
  }

  private XmlOutcome createXmlOutcome(Map<String, String> overrides, String fieldValue) {
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
    String jrFormFilling = overrides.getOrDefault("jrFormFilling", "12.34");
    String costsDamagesRecovered = overrides.getOrDefault("costsDamagesRecovered", "56.78");
    String medicalReportsClaimed = overrides.getOrDefault("medicalReportsClaimed", "4");
    String desiAccRep = overrides.getOrDefault("desiAccRep", "5");
    String noOfClients = overrides.getOrDefault("noOfClients", "6");
    String noOfSurgeryClients = overrides.getOrDefault("noOfSurgeryClients", "7");
    String noOfSuspects = overrides.getOrDefault("noOfSuspects", "8");
    String noOfPoliceStation = overrides.getOrDefault("noOfPoliceStation", "9");
    String numberOfMediationSessions = overrides.getOrDefault("numberOfMediationSessions", "10");
    String mediationTime = overrides.getOrDefault("mediationTime", "11");
    String excessTravelCosts = overrides.getOrDefault("excessTravelCosts", "0.10");
    String vatIndicator = overrides.getOrDefault("vatIndicator", fieldValue);
    String londonNonlondonRate = overrides.getOrDefault("londonNonlondonRate", fieldValue);
    String toleranceIndicator = overrides.getOrDefault("toleranceIndicator", fieldValue);
    String legacyCase = overrides.getOrDefault("legacyCase", fieldValue);
    String postalApplAccp = overrides.getOrDefault("postalApplAccp", fieldValue);
    String substantiveHearing = overrides.getOrDefault("substantiveHearing", fieldValue);
    String additionalTravelPayment = overrides.getOrDefault("additionalTravelPayment", fieldValue);
    String clientLegallyAided = overrides.getOrDefault("clientLegallyAided", fieldValue);
    String client2PostalApplAccp = overrides.getOrDefault("client2PostalApplAccp", fieldValue);
    String dutySolicitor = overrides.getOrDefault("dutySolicitor", fieldValue);
    String nationalRefMechanismAdvice =
        overrides.getOrDefault("nationalRefMechanismAdvice", fieldValue);
    String ircSurgery = overrides.getOrDefault("ircSurgery", fieldValue);
    String client2LegallyAided = overrides.getOrDefault("client2LegallyAided", fieldValue);
    String eligibleClient = overrides.getOrDefault("eligibleClient", fieldValue);
    String youthCourt = overrides.getOrDefault("youthCourt", fieldValue);
    String inqClientMeansTested = overrides.getOrDefault("inqClientMeansTested", fieldValue);
    String deceasedFirstName = overrides.getOrDefault("deceasedFirstName", "");
    String deceasedSurname = overrides.getOrDefault("deceasedSurname", "");
    String dateOfDeath = overrides.getOrDefault("dateOfDeath", "");
    String inquestReferenceNumber = overrides.getOrDefault("inquestReferenceNumber", "");
    String governmentDepartment1 = overrides.getOrDefault("governmentDepartment1", "");
    String governmentDepartment2 = overrides.getOrDefault("governmentDepartment2", "");
    String governmentDepartment3 = overrides.getOrDefault("governmentDepartment3", "");
    String governmentDepartment4 = overrides.getOrDefault("governmentDepartment4", "");
    String governmentDepartment5 = overrides.getOrDefault("governmentDepartment5", "");
    String governmentDepartment6 = overrides.getOrDefault("governmentDepartment6", "");
    String governmentDepartment7 = overrides.getOrDefault("governmentDepartment7", "");
    String governmentDepartment8 = overrides.getOrDefault("governmentDepartment8", "");
    String governmentDepartment9 = overrides.getOrDefault("governmentDepartment9", "");
    String governmentDepartment10 = overrides.getOrDefault("governmentDepartment10", "");
    return new XmlOutcome(
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
        vatIndicator,
        londonNonlondonRate,
        "clientType",
        toleranceIndicator,
        travelCosts,
        "outcomeCode",
        legacyCase,
        "claimType",
        adjournedHearingFee,
        "typeOfAdvice",
        postalApplAccp,
        "scheduleRef",
        "cmrhOral",
        "cmrhTelephone",
        "aitHearingCentre",
        substantiveHearing,
        hoInterview,
        "hoUcn",
        "04/01/2000",
        detentionTravelWaitingCosts,
        "deliveryLocation",
        "priorAuthorityRef",
        jrFormFilling,
        additionalTravelPayment,
        "meetingsAttended",
        medicalReportsClaimed,
        desiAccRep,
        "mhtRefNumber",
        "stageReached",
        "followOnWork",
        nationalRefMechanismAdvice,
        "exemptionCriteriaSatisfied",
        "exclCaseFundingRef",
        noOfClients,
        noOfSurgeryClients,
        ircSurgery,
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
        dutySolicitor,
        youthCourt,
        "schemeId",
        numberOfMediationSessions,
        mediationTime,
        "outreach",
        "referral",
        clientLegallyAided,
        "client2Forename",
        "client2Surname",
        "07/01/2000",
        "client2Ucn",
        "client2Postcode",
        "client2Gender",
        "client2Ethnicity",
        "client2Disability",
        client2LegallyAided,
        "uniqueCaseId",
        "standardFeeCat",
        client2PostalApplAccp,
        costsDamagesRecovered,
        eligibleClient,
        "courtLocationHpcds",
        "localAuthorityNumber",
        "paNumber",
        excessTravelCosts,
        "08/01/2000",
        inqClientMeansTested,
        deceasedFirstName,
        deceasedSurname,
        dateOfDeath,
        inquestReferenceNumber,
        governmentDepartment1,
        governmentDepartment2,
        governmentDepartment3,
        governmentDepartment4,
        governmentDepartment5,
        governmentDepartment6,
        governmentDepartment7,
        governmentDepartment8,
        governmentDepartment9,
        governmentDepartment10);
  }

  private GetBulkSubmission200ResponseDetails getExpectedBulkSubmissionDetails(
      Boolean expectedBooleanValue, boolean inquestFeatureEnabled) {
    var expectedBulkSubmissionOffice = ClaimsDataTestUtil.getBulkSubmissionOffice();
    var expectedBulkSubmissionSchedule = ClaimsDataTestUtil.getBulkSubmissionSchedule();
    var expectedBulkSubmissionOutcome =
        ClaimsDataTestUtil.getBulkSubmissionOutcome(expectedBooleanValue, inquestFeatureEnabled);
    if (inquestFeatureEnabled) {
      expectedBulkSubmissionOutcome.isClientMeansTested(expectedBooleanValue);
    }
    var expectedBulkSubmissionMatterStart = ClaimsDataTestUtil.getBulkSubmissionMatterStart();
    List<Map<String, String>> expectedImmigrationClrRows =
        ClaimsDataTestUtil.getImmigrationClrRows();

    List<BulkSubmissionMatterStart> expectedMatterStarts =
        List.of(expectedBulkSubmissionMatterStart);

    return new GetBulkSubmission200ResponseDetails()
        .office(expectedBulkSubmissionOffice)
        .schedule(expectedBulkSubmissionSchedule)
        .outcomes(List.of(expectedBulkSubmissionOutcome))
        .matterStarts(expectedMatterStarts)
        .immigrationClr(expectedImmigrationClrRows);
  }
}
