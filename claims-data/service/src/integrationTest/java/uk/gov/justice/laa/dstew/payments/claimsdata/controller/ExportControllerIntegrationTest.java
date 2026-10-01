package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw.CRIME_LOWER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw.LEGAL_HELP;
import static uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw.MEDIATION;
import static uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus.INVALID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus.READY_TO_PROCESS;
import static uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus.VALID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus.VALIDATED_PENDING_APPROVAL;
import static uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus.VOID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.USER_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ExportTestUtil.assertCsvHeadersMatchDefinition;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ExportTestUtil.dataRowsByHeader;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimCase;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentOutcome;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

class ExportControllerIntegrationTest extends AbstractIntegrationTest {

  private record FieldAssertion(String header, Consumer<String> assertion) {}

  private static final String OFFICE = "office-export";

  @Autowired private JdbcTemplate jdbcTemplate;

  private Submission exportedSubmission;
  private Claim exportedClaim;
  private ClaimSummaryFee exportedSummaryFee;

  @BeforeEach
  void setup() {
    seedClaimsData();
  }

  @Test
  void exportsEveryLegalHelpColumnInOrder() throws Exception {
    givenLegalHelpClaim(ClaimStatus.VALID);
    givenCalculation(
        october(2),
        calculation ->
            calculation
                .escapeCaseFlag(false)
                .fixedFeeAmount(BigDecimal.valueOf(100))
                .hourlyTotalAmount(BigDecimal.valueOf(110))
                .netProfitCostsAmount(BigDecimal.valueOf(120))
                .disbursementAmount(BigDecimal.valueOf(130))
                .disbursementVatAmount(BigDecimal.valueOf(26))
                .netCostOfCounselAmount(BigDecimal.valueOf(140))
                .travelAndWaitingCostsAmount(BigDecimal.valueOf(150))
                .detentionTravelAndWaitingCostsAmount(BigDecimal.valueOf(160))
                .jrFormFillingAmount(BigDecimal.valueOf(170))
                .boltOnAdjournedHearingCount(1)
                .boltOnAdjournedHearingFee(BigDecimal.valueOf(175))
                .boltOnCmrhTelephoneFee(BigDecimal.valueOf(180))
                .boltOnCmrhOralFee(BigDecimal.valueOf(190))
                .boltOnHomeOfficeInterviewFee(BigDecimal.valueOf(200))
                .boltOnSubstantiveHearingFee(BigDecimal.valueOf(210))
                .totalAmount(BigDecimal.valueOf(1200))
                .calculatedVatAmount(BigDecimal.valueOf(200)));
    givenAssessment(
        october(3),
        assessment ->
            assessment
                .assessmentOutcome(AssessmentOutcome.PAID_IN_FULL)
                .allowedTotalInclVat(BigDecimal.valueOf(900))
                .allowedTotalVat(BigDecimal.valueOf(150)));
    givenAmendment(october(4), "PROVIDER");

    var row = singleExportRow();

    assertEveryColumnInOrder(
        row,
        List.of(
            text("Client name", "Jane Doe"),
            text("Unique file number (UFN)", "011025/001"),
            text("Fee code", "IMMA"),
            text("Matter type 1", "IMMA"),
            text("Matter type 2", "IASY"),
            text("Case start date", "2025-09-01"),
            text("Case concluded/case claimed date", "2025-09-20"),
            text("Escape case?", "No"),
            numeric("Reported net profit costs", "250"),
            numeric("Reported net disbursements", "40"),
            numeric("VAT on reported disbursements", "8"),
            numeric("Reported net counsel costs", "35"),
            numeric("Reported travel and waiting costs", "15"),
            numeric("Reported detention travel and waiting costs", "11"),
            numeric("Reported judicial review form-filling costs", "9"),
            numeric("Reported adjourned hearing fee?", "2"),
            numeric("Reported case management review hearing (CMRH) telephone fee", "1"),
            numeric("Reported case management review hearing (CMRH) oral fee", "3"),
            text("London rate?", "Yes"),
            numeric("Reported Home Office interview fee", "4"),
            text("Reported substantive hearing fee?", "Yes"),
            text("Immigration prior authority reference", "PAR0001"),
            text("VAT claimed?", "Yes"),
            numeric("Initial calculated fixed fee", "100"),
            numeric("Initial calculated hourly rates total", "110"),
            numeric("Initial calculated net profit costs", "120"),
            numeric("Initial calculated net disbursements", "130"),
            numeric("Initial calculated VAT on disbursements", "26"),
            numeric("Initial calculated net counsel costs", "140"),
            numeric("Initial calculated travel and waiting costs", "150"),
            numeric("Initial calculated detention travel and waiting costs", "160"),
            numeric("Initial calculated judicial review form-filling costs", "170"),
            numeric("Initial calculated adjourned hearing fee", "175"),
            numeric(
                "Initial calculated case management review hearing (CMRH) telephone fee", "180"),
            numeric("Initial calculated case management review hearing (CMRH) oral fee", "190"),
            numeric("Initial calculated Home Office interview fee", "200"),
            numeric("Initial calculated substantive hearing fee?", "210"),
            numeric("Current calculated fixed fee", "100"),
            numeric("Current calculated hourly rates total", "110"),
            numeric("Current calculated net profit costs", "120"),
            numeric("Current calculated net disbursements", "130"),
            numeric("Current calculated VAT on disbursements", "26"),
            numeric("Current calculated net counsel costs", "140"),
            numeric("Current calculated travel and waiting costs", "150"),
            numeric("Current calculated detention travel and waiting costs", "160"),
            numeric("Current calculated judicial review form-filling costs", "170"),
            numeric("Current calculated adjourned hearing fee", "175"),
            numeric(
                "Current calculated case management review hearing (CMRH) telephone fee", "180"),
            numeric("Current calculated case management review hearing (CMRH) oral fee", "190"),
            numeric("Current calculated Home Office interview fee", "200"),
            numeric("Current calculated substantive hearing fee?", "210"),
            numeric("Initial total claim value (including VAT)", "1200"),
            numeric("VAT on initial claim", "200"),
            // An assessment overrides the calculated claim value.
            numeric("Current total claim value (including VAT)", "900"),
            numeric("VAT on current claim", "150"),
            text("Claim status", "Assessed"),
            text("Latest assessment outcome", "Assessed in full"),
            text("Latest assessment date", "2025-10-03"),
            text("Latest amendment requestor", "Provider"),
            text("Latest amendment date", "2025-10-04"),
            text("Office account number", OFFICE),
            text("Submission month", "OCT-2025"),
            text("Area of law", "Legal help"),
            text("Submission date", "2025-10-01")));
  }

  @Test
  void exportsEveryCrimeLowerColumnInOrder() throws Exception {
    givenCrimeLowerClaim(ClaimStatus.VALID);
    givenCalculation(
        october(2),
        calculation ->
            calculation
                .escapeCaseFlag(true)
                .fixedFeeAmount(BigDecimal.valueOf(100))
                .hourlyTotalAmount(BigDecimal.valueOf(110))
                .netProfitCostsAmount(BigDecimal.valueOf(120))
                .disbursementAmount(BigDecimal.valueOf(130))
                .disbursementVatAmount(BigDecimal.valueOf(26))
                .netTravelCostsAmount(BigDecimal.valueOf(140))
                .netWaitingCostsAmount(BigDecimal.valueOf(150))
                .totalAmount(BigDecimal.valueOf(1200))
                .calculatedVatAmount(BigDecimal.valueOf(200)));
    givenAssessment(
        october(3),
        assessment ->
            assessment
                .assessmentOutcome(AssessmentOutcome.PAID_IN_FULL)
                .allowedTotalInclVat(BigDecimal.valueOf(900))
                .allowedTotalVat(BigDecimal.valueOf(150)));
    givenAmendment(october(4), "CONTRACT_MANAGEMENT");

    var row = singleExportRow();

    assertEveryColumnInOrder(
        row,
        List.of(
            text("Client name", "John Smith"),
            text("Unique file number (UFN)", "011025/002"),
            text("Fee code", "INVC"),
            text("Matter type", "01"),
            text("Representation order date", "2025-09-05"),
            text("Stage reached", "PROG"),
            text("Outcome code", "CP01"),
            text("Date of work concluded", "2025-09-25"),
            text("Escape case?", "Yes"),
            numeric("Reported net profit costs", "250"),
            numeric("Reported net disbursements", "40"),
            numeric("VAT on reported disbursements", "8"),
            numeric("Reported net travel costs", "15"),
            numeric("Reported net waiting costs", "12"),
            text("VAT claimed?", "Yes"),
            numeric("Initial calculated fixed fee", "100"),
            numeric("Initial calculated hourly rates total", "110"),
            numeric("Initial calculated net profit costs", "120"),
            numeric("Initial calculated net disbursements", "130"),
            numeric("Initial calculated VAT on disbursements", "26"),
            numeric("Initial calculated net travel costs", "140"),
            numeric("Initial calculated net waiting costs", "150"),
            // With only one calculation, initial and current are the same.
            numeric("Current calculated fixed fee", "100"),
            numeric("Current calculated hourly rates total", "110"),
            numeric("Current calculated net profit costs", "120"),
            numeric("Current calculated net disbursements", "130"),
            numeric("Current calculated VAT on disbursements", "26"),
            numeric("Current calculated net travel costs", "140"),
            numeric("Current calculated net waiting costs", "150"),
            numeric("Initial total claim value (including VAT)", "1200"),
            numeric("VAT on initial claim", "200"),
            // An assessment overrides the calculated claim value.
            numeric("Current total claim value (including VAT)", "900"),
            numeric("VAT on current claim", "150"),
            text("Claim status", "Assessed"),
            text("Latest assessment outcome", "Assessed in full"),
            text("Latest assessment date", "2025-10-03"),
            text("Latest amendment requestor", "Contract management"),
            text("Latest amendment date", "2025-10-04"),
            text("Office account number", OFFICE),
            text("Submission month", "OCT-2025"),
            text("Area of law", "Crime lower"),
            text("Submission date", "2025-10-01")));
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = AreaOfLaw.class,
      names = {"LEGAL_HELP", "CRIME_LOWER"})
  void exportsFirstCalculationAsInitialAndNewestRowsAsCurrentAndLatest(AreaOfLaw areaOfLaw)
      throws Exception {
    givenClaim(areaOfLaw, ClaimStatus.VALID);
    // Seeded out of date order, so neither insert order nor id order gives the right answer.
    givenCalculation(october(3), calculation -> calculation.fixedFeeAmount(BigDecimal.valueOf(30)));
    givenCalculation(october(1), calculation -> calculation.fixedFeeAmount(BigDecimal.valueOf(10)));
    givenCalculation(october(2), calculation -> calculation.fixedFeeAmount(BigDecimal.valueOf(20)));
    givenAssessment(
        october(6),
        assessment -> assessment.assessmentOutcome(AssessmentOutcome.REDUCED_TO_FIXED_FEE));
    givenAssessment(
        october(4), assessment -> assessment.assessmentOutcome(AssessmentOutcome.PAID_IN_FULL));
    givenAssessment(
        october(5), assessment -> assessment.assessmentOutcome(AssessmentOutcome.NILLED));
    givenAmendment(october(8), "ASSURANCE");
    givenAmendment(october(7), "PROVIDER");

    var row = singleExportRow();

    assertColumns(
        row,
        List.of(
            numeric("Initial calculated fixed fee", "10"),
            numeric("Current calculated fixed fee", "30"),
            text("Latest assessment outcome", "Reduced to fixed fee (assessed)"),
            text("Latest assessment date", "2025-10-06"),
            text("Latest amendment requestor", "Assurance"),
            text("Latest amendment date", "2025-10-08")));
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = AreaOfLaw.class,
      names = {"LEGAL_HELP", "CRIME_LOWER"})
  void exportsCalculatedClaimValueWhenThereIsNoAssessmentOrAmendment(AreaOfLaw areaOfLaw)
      throws Exception {
    givenClaim(areaOfLaw, ClaimStatus.VALID);
    givenCalculation(
        october(2),
        calculation ->
            calculation
                .totalAmount(BigDecimal.valueOf(1200))
                .calculatedVatAmount(BigDecimal.valueOf(200)));

    var row = singleExportRow();

    assertColumns(
        row,
        List.of(
            numeric("Current total claim value (including VAT)", "1200"),
            numeric("VAT on current claim", "200"),
            text("Claim status", "Accepted"),
            blank("Latest assessment outcome"),
            blank("Latest assessment date"),
            blank("Latest amendment requestor"),
            blank("Latest amendment date")));
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = AreaOfLaw.class,
      names = {"LEGAL_HELP", "CRIME_LOWER"})
  void exportsAssessedValuesWhenThereIsNoCalculation(AreaOfLaw areaOfLaw) throws Exception {
    givenClaim(areaOfLaw, ClaimStatus.VALID);
    givenAssessment(
        october(3),
        assessment ->
            assessment
                .fixedFeeAmount(BigDecimal.valueOf(300))
                .netProfitCostsAmount(BigDecimal.valueOf(310))
                .allowedTotalInclVat(BigDecimal.valueOf(900))
                .allowedTotalVat(BigDecimal.valueOf(150)));

    var row = singleExportRow();

    assertColumns(
        row,
        List.of(
            blank("Escape case?"),
            blank("Initial calculated fixed fee"),
            blank("Initial total claim value (including VAT)"),
            numeric("Current calculated fixed fee", "300"),
            numeric("Current calculated net profit costs", "310"),
            blank("Current calculated hourly rates total"),
            numeric("Current total claim value (including VAT)", "900"),
            numeric("VAT on current claim", "150")));
  }

  @Test
  void exportsLegalHelpAssessedValuesOverCalculatedValues() throws Exception {
    givenLegalHelpClaim(ClaimStatus.VALID);
    givenCalculation(
        october(2),
        calculation ->
            calculation
                .fixedFeeAmount(BigDecimal.valueOf(100))
                .netProfitCostsAmount(BigDecimal.valueOf(120))
                .disbursementAmount(BigDecimal.valueOf(130))
                .disbursementVatAmount(BigDecimal.valueOf(26))
                .netCostOfCounselAmount(BigDecimal.valueOf(140))
                .detentionTravelAndWaitingCostsAmount(BigDecimal.valueOf(160))
                .jrFormFillingAmount(BigDecimal.valueOf(170))
                .totalAmount(BigDecimal.valueOf(1200))
                .calculatedVatAmount(BigDecimal.valueOf(200)));
    givenAssessment(
        october(3),
        assessment ->
            assessment
                .fixedFeeAmount(BigDecimal.valueOf(50))
                .netProfitCostsAmount(BigDecimal.valueOf(60))
                .disbursementAmount(BigDecimal.valueOf(65))
                .disbursementVatAmount(BigDecimal.valueOf(13))
                .netCostOfCounselAmount(BigDecimal.valueOf(70))
                .detentionTravelAndWaitingCostsAmount(BigDecimal.valueOf(80))
                .jrFormFillingAmount(BigDecimal.valueOf(85))
                .allowedTotalInclVat(BigDecimal.valueOf(600))
                .allowedTotalVat(BigDecimal.valueOf(100)));

    var row = singleExportRow();

    assertColumns(
        row,
        List.of(
            numeric("Initial calculated fixed fee", "100"),
            numeric("Current calculated fixed fee", "50"),
            numeric("Current calculated net profit costs", "60"),
            numeric("Current calculated net disbursements", "65"),
            numeric("Current calculated VAT on disbursements", "13"),
            numeric("Current calculated net counsel costs", "70"),
            numeric("Current calculated detention travel and waiting costs", "80"),
            numeric("Current calculated judicial review form-filling costs", "85"),
            numeric("Current total claim value (including VAT)", "600"),
            numeric("VAT on current claim", "100")));
  }

  @Test
  void exportsCrimeLowerAssessedValuesOverCalculatedValues() throws Exception {
    givenCrimeLowerClaim(ClaimStatus.VALID);
    givenCalculation(
        october(2),
        calculation ->
            calculation
                .fixedFeeAmount(BigDecimal.valueOf(100))
                .netProfitCostsAmount(BigDecimal.valueOf(120))
                .disbursementAmount(BigDecimal.valueOf(130))
                .disbursementVatAmount(BigDecimal.valueOf(26))
                .netTravelCostsAmount(BigDecimal.valueOf(140))
                .netWaitingCostsAmount(BigDecimal.valueOf(150))
                .totalAmount(BigDecimal.valueOf(1200))
                .calculatedVatAmount(BigDecimal.valueOf(200)));
    givenAssessment(
        october(3),
        assessment ->
            assessment
                .fixedFeeAmount(BigDecimal.valueOf(50))
                .netProfitCostsAmount(BigDecimal.valueOf(60))
                .disbursementAmount(BigDecimal.valueOf(65))
                .disbursementVatAmount(BigDecimal.valueOf(13))
                .netTravelCostsAmount(BigDecimal.valueOf(70))
                .netWaitingCostsAmount(BigDecimal.valueOf(75))
                .allowedTotalInclVat(BigDecimal.valueOf(600))
                .allowedTotalVat(BigDecimal.valueOf(100)));

    var row = singleExportRow();

    assertColumns(
        row,
        List.of(
            numeric("Initial calculated fixed fee", "100"),
            numeric("Current calculated fixed fee", "50"),
            numeric("Current calculated net profit costs", "60"),
            numeric("Current calculated net disbursements", "65"),
            numeric("Current calculated VAT on disbursements", "13"),
            numeric("Current calculated net travel costs", "70"),
            numeric("Current calculated net waiting costs", "75"),
            numeric("Current total claim value (including VAT)", "600"),
            numeric("VAT on current claim", "100")));
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = AreaOfLaw.class,
      names = {"LEGAL_HELP", "CRIME_LOWER"})
  void ignoresAssessmentsOnVoidedClaims(AreaOfLaw areaOfLaw) throws Exception {
    givenClaim(areaOfLaw, ClaimStatus.VOID);
    givenCalculation(
        october(2),
        calculation ->
            calculation
                .fixedFeeAmount(BigDecimal.valueOf(100))
                .totalAmount(BigDecimal.valueOf(1200))
                .calculatedVatAmount(BigDecimal.valueOf(200)));
    givenAssessment(
        october(3),
        assessment ->
            assessment
                .fixedFeeAmount(BigDecimal.valueOf(300))
                .allowedTotalInclVat(BigDecimal.valueOf(900))
                .allowedTotalVat(BigDecimal.valueOf(150)));

    var row = singleExportRow();

    assertColumns(
        row,
        List.of(
            numeric("Current calculated fixed fee", "100"),
            numeric("Current total claim value (including VAT)", "1200"),
            numeric("VAT on current claim", "200"),
            text("Claim status", "Voided"),
            blank("Latest assessment outcome"),
            blank("Latest assessment date")));
  }

  @Test
  void exportsEveryMediationColumnInOrder() throws Exception {
    givenMediationClaim(ClaimStatus.VALID);
    givenCalculation(
        october(2),
        calculation ->
            calculation
                .fixedFeeAmount(BigDecimal.valueOf(100))
                .disbursementAmount(BigDecimal.valueOf(130))
                .disbursementVatAmount(BigDecimal.valueOf(26))
                .totalAmount(BigDecimal.valueOf(1200))
                .calculatedVatAmount(BigDecimal.valueOf(200)));
    givenAmendment(october(4), "PROVIDER");

    var row = singleExportRow();

    assertEveryColumnInOrder(
        row,
        List.of(
            text("Client 1 name", "Mia Green"),
            text("Client 1 unique client number (UCN)", "03031992/C/DEFG"),
            text("Client 2 name", "Noah Green"),
            text("Client 2 unique client number (UCN)", "04041993/D/EFGH"),
            text("Fee code", "MDAS2B"),
            text("Matter type codes", "FAMA:FAMY"),
            text("Case start date", "2025-09-01"),
            text("Date of work concluded", "2025-09-20"),
            numeric("Reported net disbursements", "40"),
            numeric("VAT on reported disbursements", "8"),
            text("VAT claimed?", "No"),
            numeric("Initial calculated fixed fee", "100"),
            numeric("Initial calculated net disbursements", "130"),
            numeric("Initial calculated VAT on disbursements", "26"),
            // With only one calculation, initial and current are the same.
            numeric("Current calculated fixed fee", "100"),
            numeric("Current calculated net disbursements", "130"),
            numeric("Current calculated VAT on disbursements", "26"),
            numeric("Initial total claim value (including VAT)", "1200"),
            numeric("VAT on initial claim", "200"),
            numeric("Current total claim value (including VAT)", "1200"),
            numeric("VAT on current claim", "200"),
            text("Claim status", "Amended"),
            text("Latest amendment requestor", "Provider"),
            text("Latest amendment date", "2025-10-04"),
            text("Office account number", OFFICE),
            text("Submission month", "OCT-2025"),
            text("Area of law", "Mediation"),
            text("Submission date", "2025-10-01")));
  }

  @Test
  void exportsFirstMediationCalculationAsInitialAndNewestRowsAsCurrentAndLatest() throws Exception {
    givenMediationClaim(ClaimStatus.VALID);
    // Seeded out of date order, so neither insert order nor id order gives the right answer.
    givenCalculation(
        october(3),
        calculation ->
            calculation
                .fixedFeeAmount(BigDecimal.valueOf(30))
                .totalAmount(BigDecimal.valueOf(300)));
    givenCalculation(
        october(1),
        calculation ->
            calculation
                .fixedFeeAmount(BigDecimal.valueOf(10))
                .totalAmount(BigDecimal.valueOf(100)));
    givenCalculation(
        october(2),
        calculation ->
            calculation
                .fixedFeeAmount(BigDecimal.valueOf(20))
                .totalAmount(BigDecimal.valueOf(200)));
    givenAmendment(october(8), "ASSURANCE");
    givenAmendment(october(7), "PROVIDER");

    var row = singleExportRow();

    assertColumns(
        row,
        List.of(
            numeric("Initial calculated fixed fee", "10"),
            numeric("Initial total claim value (including VAT)", "100"),
            numeric("Current calculated fixed fee", "30"),
            numeric("Current total claim value (including VAT)", "300"),
            text("Latest amendment requestor", "Assurance"),
            text("Latest amendment date", "2025-10-08")));
  }

  @Test
  void exportsBlankMediationCalculatedValuesWhenThereIsNoCalculation() throws Exception {
    givenMediationClaim(ClaimStatus.VALID);

    var row = singleExportRow();

    assertColumns(
        row,
        List.of(
            blank("Initial calculated fixed fee"),
            blank("Initial total claim value (including VAT)"),
            blank("Current calculated fixed fee"),
            blank("Current total claim value (including VAT)"),
            blank("VAT on current claim"),
            text("Claim status", "Accepted"),
            blank("Latest amendment requestor"),
            blank("Latest amendment date")));
  }

  private static Stream<Arguments> claimStatuses() {
    return Stream.of(
        arguments(LEGAL_HELP, VALIDATED_PENDING_APPROVAL, false, false, "Not submitted"),
        arguments(LEGAL_HELP, VOID, true, true, "Voided"),
        arguments(LEGAL_HELP, VALID, false, false, "Accepted"),
        arguments(LEGAL_HELP, VALID, false, true, "Amended"),
        arguments(LEGAL_HELP, VALID, true, false, "Assessed"),
        arguments(LEGAL_HELP, VALID, true, true, "Assessed"),
        arguments(CRIME_LOWER, VALIDATED_PENDING_APPROVAL, false, false, "Not submitted"),
        arguments(CRIME_LOWER, VOID, true, true, "Voided"),
        arguments(CRIME_LOWER, VALID, false, false, "Accepted"),
        arguments(CRIME_LOWER, VALID, false, true, "Amended"),
        arguments(CRIME_LOWER, VALID, true, false, "Assessed"),
        arguments(CRIME_LOWER, VALID, true, true, "Assessed"),
        arguments(MEDIATION, VALIDATED_PENDING_APPROVAL, false, false, "Not submitted"),
        arguments(MEDIATION, VOID, false, true, "Voided"),
        arguments(MEDIATION, VALID, false, false, "Accepted"),
        arguments(MEDIATION, VALID, false, true, "Amended"));
  }

  @ParameterizedTest(name = "{0}: {1} claim, assessed: {2}, amended: {3} -> {4}")
  @MethodSource("claimStatuses")
  void exportsDerivedClaimStatus(
      AreaOfLaw areaOfLaw,
      ClaimStatus claimStatus,
      boolean assessed,
      boolean amended,
      String expectedStatus)
      throws Exception {
    givenClaim(areaOfLaw, claimStatus);
    if (assessed) {
      givenAssessment(october(3), assessment -> assessment);
    }
    if (amended) {
      givenAmendment(october(4), "PROVIDER");
    }

    var row = singleExportRow();

    assertColumns(row, List.of(text("Claim status", expectedStatus)));
  }

  @ParameterizedTest(name = "{0}: {1} claim")
  @MethodSource("unsupportedClaimStatuses")
  void excludesClaimsWithUnsupportedStatus(AreaOfLaw areaOfLaw, ClaimStatus claimStatus)
      throws Exception {
    givenClaim(areaOfLaw, claimStatus);

    assertThat(exportRows()).isEmpty();
  }

  private static Stream<Arguments> unsupportedClaimStatuses() {
    return Stream.of(
        arguments(LEGAL_HELP, READY_TO_PROCESS),
        arguments(LEGAL_HELP, INVALID),
        arguments(CRIME_LOWER, READY_TO_PROCESS),
        arguments(CRIME_LOWER, INVALID),
        arguments(MEDIATION, READY_TO_PROCESS),
        arguments(MEDIATION, INVALID));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/exports/submission-claims-legal-help",
        "/exports/submission-claims-crime-lower",
        "/exports/submission-claims-mediation"
      })
  void returnsBadRequestWhenSubmissionIdIsNotUuidForExport(String endpoint) throws Exception {
    mockMvc
        .perform(
            get(endpoint)
                .param("submission-id", "invalid-uuid")
                .param("office", OFFICE)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isBadRequest());
  }

  private static Instant october(int day) {
    return LocalDate.of(2025, Month.OCTOBER, day).atTime(12, 0).toInstant(ZoneOffset.UTC);
  }

  private void givenClaim(AreaOfLaw areaOfLaw, ClaimStatus status) {
    switch (areaOfLaw) {
      case LEGAL_HELP -> givenLegalHelpClaim(status);
      case CRIME_LOWER -> givenCrimeLowerClaim(status);
      case MEDIATION -> givenMediationClaim(status);
    }
  }

  private void givenLegalHelpClaim(ClaimStatus status) {
    givenSubmission(AreaOfLaw.LEGAL_HELP);
    givenClaim(
        claim ->
            claim
                .status(status)
                .uniqueFileNumber("011025/001")
                .feeCode("IMMA")
                .matterTypeCode("IMMA:IASY")
                .caseStartDate(LocalDate.of(2025, Month.SEPTEMBER, 1))
                .caseConcludedDate(LocalDate.of(2025, Month.SEPTEMBER, 20)));
    givenClient(client -> client.clientForename("Jane").clientSurname("Doe"));
    givenSummaryFee(
        summaryFee ->
            summaryFee
                .netProfitCostsAmount(BigDecimal.valueOf(250))
                .netDisbursementAmount(BigDecimal.valueOf(40))
                .disbursementsVatAmount(BigDecimal.valueOf(8))
                .netCounselCostsAmount(BigDecimal.valueOf(35))
                .travelWaitingCostsAmount(BigDecimal.valueOf(15))
                .detentionTravelWaitingCostsAmount(BigDecimal.valueOf(11))
                .jrFormFillingAmount(BigDecimal.valueOf(9))
                .adjournedHearingFeeAmount(2)
                .cmrhTelephoneCount(1)
                .cmrhOralCount(3)
                .isLondonRate(true)
                .hoInterview(4)
                .isSubstantiveHearing(true)
                .priorAuthorityReference("PAR0001")
                .isVatApplicable(true));
  }

  private void givenCrimeLowerClaim(ClaimStatus status) {
    givenSubmission(AreaOfLaw.CRIME_LOWER);
    givenClaim(
        claim ->
            claim
                .status(status)
                .uniqueFileNumber("011025/002")
                .feeCode("INVC")
                .matterTypeCode("CMAT")
                .crimeMatterTypeCode("01")
                .representationOrderDate(LocalDate.of(2025, Month.SEPTEMBER, 5))
                .caseConcludedDate(LocalDate.of(2025, Month.SEPTEMBER, 25)));
    givenClient(client -> client.clientForename("John").clientSurname("Smith"));
    claimCaseRepository.saveAndFlush(
        ClaimCase.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(exportedClaim)
            .stageReachedCode("PROG")
            .outcomeCode("CP01")
            .createdByUserId(USER_ID)
            .build());
    givenSummaryFee(
        summaryFee ->
            summaryFee
                .netProfitCostsAmount(BigDecimal.valueOf(250))
                .netDisbursementAmount(BigDecimal.valueOf(40))
                .disbursementsVatAmount(BigDecimal.valueOf(8))
                .travelWaitingCostsAmount(BigDecimal.valueOf(15))
                .netWaitingCostsAmount(BigDecimal.valueOf(12))
                .isVatApplicable(true));
  }

  private void givenMediationClaim(ClaimStatus status) {
    givenSubmission(AreaOfLaw.MEDIATION);
    givenClaim(
        claim ->
            claim
                .status(status)
                .feeCode("MDAS2B")
                .matterTypeCode("FAMA:FAMY")
                .caseStartDate(LocalDate.of(2025, Month.SEPTEMBER, 1))
                .caseConcludedDate(LocalDate.of(2025, Month.SEPTEMBER, 20)));
    givenClient(
        client ->
            client
                .clientForename("Mia")
                .clientSurname("Green")
                .uniqueClientNumber("03031992/C/DEFG")
                .client2Forename("Noah")
                .client2Surname("Green")
                .client2Ucn("04041993/D/EFGH"));
    givenSummaryFee(
        summaryFee ->
            summaryFee
                .netDisbursementAmount(BigDecimal.valueOf(40))
                .disbursementsVatAmount(BigDecimal.valueOf(8))
                .isVatApplicable(false));
  }

  private void givenSubmission(AreaOfLaw areaOfLaw) {
    exportedSubmission =
        submissionRepository.saveAndFlush(
            Submission.builder()
                .id(Uuid7.timeBasedUuid())
                .bulkSubmissionId(bulkSubmission.getId())
                .officeAccountNumber(OFFICE)
                .submissionPeriod("OCT-2025")
                .areaOfLaw(areaOfLaw)
                .status(SubmissionStatus.CREATED)
                .createdByUserId(USER_ID)
                .providerUserId(USER_ID)
                .numberOfClaims(1)
                .createdOn(october(1))
                .build());
  }

  private void givenClaim(UnaryOperator<Claim.ClaimBuilder> withValues) {
    var claim =
        Claim.builder()
            .id(Uuid7.timeBasedUuid())
            .submission(exportedSubmission)
            .lineNumber(1)
            .createdByUserId(USER_ID)
            .createdOn(october(1));
    exportedClaim = claimRepository.saveAndFlush(withValues.apply(claim).build());
  }

  private void givenClient(UnaryOperator<Client.ClientBuilder> withValues) {
    var client =
        Client.builder().id(Uuid7.timeBasedUuid()).claim(exportedClaim).createdByUserId(USER_ID);
    clientRepository.saveAndFlush(withValues.apply(client).build());
  }

  private void givenSummaryFee(UnaryOperator<ClaimSummaryFee.ClaimSummaryFeeBuilder> withValues) {
    var summaryFee =
        ClaimSummaryFee.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(exportedClaim)
            .createdByUserId(USER_ID)
            .createdOn(october(1));
    exportedSummaryFee =
        claimSummaryFeeRepository.saveAndFlush(withValues.apply(summaryFee).build());
  }

  private void givenCalculation(
      Instant createdOn, UnaryOperator<CalculatedFeeDetail.CalculatedFeeDetailBuilder> withValues) {
    var calculation =
        CalculatedFeeDetail.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(exportedClaim)
            .claimSummaryFee(exportedSummaryFee)
            .createdByUserId(USER_ID)
            .createdOn(createdOn);
    calculatedFeeDetailRepository.saveAndFlush(withValues.apply(calculation).build());
  }

  private void givenAssessment(
      Instant createdOn, UnaryOperator<Assessment.AssessmentBuilder> withValues) {
    var assessment =
        Assessment.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(exportedClaim)
            .claimSummaryFee(exportedSummaryFee)
            .assessmentType(AssessmentType.ESCAPE_CASE_ASSESSMENT)
            .assessmentOutcome(AssessmentOutcome.PAID_IN_FULL)
            .assessmentReason("Assessed")
            .assessedTotalVat(BigDecimal.ZERO)
            .assessedTotalInclVat(BigDecimal.ZERO)
            .allowedTotalVat(BigDecimal.ZERO)
            .allowedTotalInclVat(BigDecimal.ZERO)
            .createdByUserId(USER_ID)
            .updatedByUserId(USER_ID);
    var saved = assessmentRepository.saveAndFlush(withValues.apply(assessment).build());

    // The export picks the latest assessment by created_on and outputs it as "Latest assessment
    // date", so the tests need to control it. We can't set it on the builder because
    // Assessment.createdOn is a @CreationTimestamp, which Hibernate overwrites with the current
    // time on save. Instead, overwrite it in the database after saving.
    jdbcTemplate.update(
        "update claims.assessment set created_on = ? where id = ?",
        Timestamp.from(createdOn),
        saved.getId());
  }

  private void givenAmendment(Instant createdOn, String requestedBy) {
    claimAmendmentRepository.saveAndFlush(
        ClaimAmendment.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(exportedClaim)
            .requestedByCode(requestedBy)
            .amendmentReasonCode("PROVIDER".equals(requestedBy) ? "PROVIDER_ERROR" : "OTHER")
            .beforeState("{}")
            .requestPayload("{}")
            .diff("{}")
            .createdByUserId(USER_ID)
            .createdOn(createdOn)
            .build());
  }

  /** Exports the seeded submission, checks its headers and returns its only row. */
  private Map<String, String> singleExportRow() throws Exception {
    var rows = exportRows();
    assertThat(rows).as("one row per claim").hasSize(1);
    return rows.getFirst();
  }

  /** Exports the seeded submission, checks its headers and returns its rows. */
  private List<Map<String, String>> exportRows() throws Exception {
    var exportName =
        switch (exportedSubmission.getAreaOfLaw()) {
          case LEGAL_HELP -> "submission-claims-legal-help";
          case CRIME_LOWER -> "submission-claims-crime-lower";
          case MEDIATION -> "submission-claims-mediation";
        };

    var csv = exportCsv("/exports/" + exportName).getResponse().getContentAsString();

    assertCsvHeadersMatchDefinition(csv, exportName + ".yml");
    return dataRowsByHeader(csv);
  }

  private MvcResult exportCsv(String endpoint) throws Exception {
    var initialResponse =
        mockMvc
            .perform(
                get(endpoint)
                    .param("submission-id", exportedSubmission.getId().toString())
                    .param("office", OFFICE)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    var response = initialResponse;
    if (initialResponse.getRequest().isAsyncStarted()) {
      response =
          mockMvc.perform(asyncDispatch(initialResponse)).andExpect(status().isOk()).andReturn();
    }

    assertThat(response.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
    return response;
  }

  /** Asserts the given columns, ignoring any others in the row. */
  private static void assertColumns(Map<String, String> row, List<FieldAssertion> assertions) {
    assertions.forEach(
        assertion -> {
          assertThat(row).as("export columns").containsKey(assertion.header());
          assertion.assertion().accept(row.get(assertion.header()));
        });
  }

  /** Asserts that the row has exactly these columns, in this order, with these values. */
  private static void assertEveryColumnInOrder(
      Map<String, String> row, List<FieldAssertion> assertions) {
    assertThat(row.keySet())
        .containsExactlyElementsOf(assertions.stream().map(FieldAssertion::header).toList());
    assertColumns(row, assertions);
  }

  private static FieldAssertion text(String header, String expected) {
    return new FieldAssertion(header, value -> assertThat(value).as(header).isEqualTo(expected));
  }

  private static FieldAssertion numeric(String header, String expected) {
    return new FieldAssertion(
        header,
        value -> {
          assertThat(value).as(header).isNotBlank();
          assertThat(new BigDecimal(value)).as(header).isEqualByComparingTo(expected);
        });
  }

  private static FieldAssertion blank(String header) {
    return new FieldAssertion(header, value -> assertThat(value).as(header).isBlank());
  }
}
