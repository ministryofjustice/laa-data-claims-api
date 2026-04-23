package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_SUMMARY_FEE_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ExportTestUtil.assertCsvHeadersMatchDefinition;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ExportTestUtil.firstDataRowByHeader;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

class ExportControllerIntegrationTest extends AbstractIntegrationTest {

  private static final String EXPORTS_BASE_PATH = "/exports";

  private static final String LEGAL_HELP_ENDPOINT =
      EXPORTS_BASE_PATH + "/submission-claims-legal-help";
  private static final String CRIME_LOWER_ENDPOINT =
      EXPORTS_BASE_PATH + "/submission-claims-crime-lower";
  private static final String MEDIATION_ENDPOINT =
      EXPORTS_BASE_PATH + "/submission-claims-mediation";

  private static final String LEGAL_HELP_DEFINITION = "submission-claims-legal-help.yml";
  private static final String CRIME_LOWER_DEFINITION = "submission-claims-crime-lower.yml";
  private static final String MEDIATION_DEFINITION = "submission-claims-mediation.yml";

  private static final String CRIME_OFFICE = "office-crime-export";
  private static final String MEDIATION_OFFICE = "office-mediation-export";

  @BeforeEach
  void setup() {
    seedClaimsData();
  }

  @Test
  void exportsLegalHelpCsvWithDefinitionHeadersAndSeededRowValues() throws Exception {
    createAssessmentDataForClaimAndSummaryFeeId(CLAIM_1_ID, CLAIM_1_SUMMARY_FEE_ID, true);
    MvcResult response =
        exportCsv(LEGAL_HELP_ENDPOINT, submission1.getId(), submission1.getOfficeAccountNumber());

    assertCsvHeadersMatchDefinition(
        response.getResponse().getContentAsString(), LEGAL_HELP_DEFINITION);

    Map<String, String> firstRow =
        firstDataRowByHeader(response.getResponse().getContentAsString());

    assertLegalHelpValues(firstRow);
    assertAssessmentValues(firstRow);
    assertLegalHelpAssessmentValues(firstRow);
    assertLatestAssessmentSelected(firstRow);
  }

  @Test
  void exportsCrimeLowerCsvWithDefinitionHeadersAndSeededRowValues() throws Exception {
    UUID crimeSubmissionId = createCrimeLowerExportData(CRIME_OFFICE);
    MvcResult response = exportCsv(CRIME_LOWER_ENDPOINT, crimeSubmissionId, CRIME_OFFICE);

    assertCsvHeadersMatchDefinition(
        response.getResponse().getContentAsString(), CRIME_LOWER_DEFINITION);

    Map<String, String> firstRow =
        firstDataRowByHeader(response.getResponse().getContentAsString());

    assertCrimeLowerValues(firstRow);
    assertAssessmentValues(firstRow);
    assertLatestAssessmentSelected(firstRow);
  }

  @Test
  void exportsCrimeLowerCsvWithDefinitionHeadersAndNoAssessments() throws Exception {
    UUID crimeSubmissionId = createCrimeLowerExportData(CRIME_OFFICE);
    assessmentRepository.deleteAll();
    MvcResult response = exportCsv(CRIME_LOWER_ENDPOINT, crimeSubmissionId, CRIME_OFFICE);

    assertCsvHeadersMatchDefinition(
        response.getResponse().getContentAsString(), CRIME_LOWER_DEFINITION);

    Map<String, String> firstRow =
        firstDataRowByHeader(response.getResponse().getContentAsString());

    assertCrimeLowerValues(firstRow);
    assertDefaultClaimValues(firstRow);
  }

  @Test
  void exportsMediationCsvWithDefinitionHeadersAndSeededRowValues() throws Exception {
    UUID mediationSubmissionId = createMediationExportData(MEDIATION_OFFICE);
    MvcResult response = exportCsv(MEDIATION_ENDPOINT, mediationSubmissionId, MEDIATION_OFFICE);

    assertCsvHeadersMatchDefinition(
        response.getResponse().getContentAsString(), MEDIATION_DEFINITION);

    Map<String, String> firstRow =
        firstDataRowByHeader(response.getResponse().getContentAsString());

    assertMediationValues(firstRow);
    assertAssessmentValues(firstRow);
    assertLatestAssessmentSelected(firstRow);
  }

  @Test
  void exportsMediationCsvWithDefinitionHeadersAndNoAssessments() throws Exception {
    UUID mediationSubmissionId = createMediationExportData(MEDIATION_OFFICE);
    assessmentRepository.deleteAll();
    MvcResult response = exportCsv(MEDIATION_ENDPOINT, mediationSubmissionId, MEDIATION_OFFICE);

    assertCsvHeadersMatchDefinition(
        response.getResponse().getContentAsString(), MEDIATION_DEFINITION);

    Map<String, String> firstRow =
        firstDataRowByHeader(response.getResponse().getContentAsString());
    assertMediationValues(firstRow);
    assertDefaultClaimValues(firstRow);
  }

  @Test
  void exportsLegalHelpCsvWithDefinitionHeadersAndNoAssessments() throws Exception {
    assessmentRepository.deleteAll();
    MvcResult response =
        exportCsv(LEGAL_HELP_ENDPOINT, submission1.getId(), submission1.getOfficeAccountNumber());

    assertCsvHeadersMatchDefinition(
        response.getResponse().getContentAsString(), LEGAL_HELP_DEFINITION);

    Map<String, String> firstRow =
        firstDataRowByHeader(response.getResponse().getContentAsString());

    assertLegalHelpValues(firstRow);
    assertDefaultClaimValues(firstRow);
  }

  @Test
  void exportsLegalHelpCsvSeedsMultipleOtherSubmissions() throws Exception {
    createCrimeLowerExportData(CRIME_OFFICE);
    createMediationExportData(MEDIATION_OFFICE);
    createSingleAssessmentForClaimAndSummaryFeeId(CLAIM_1_ID, CLAIM_1_SUMMARY_FEE_ID, true);
    MvcResult response =
        exportCsv(LEGAL_HELP_ENDPOINT, submission1.getId(), submission1.getOfficeAccountNumber());

    assertCsvHeadersMatchDefinition(
        response.getResponse().getContentAsString(), LEGAL_HELP_DEFINITION);

    Map<String, String> firstRow =
        firstDataRowByHeader(response.getResponse().getContentAsString());

    assertLegalHelpValues(firstRow);
    assertAssessmentValues(firstRow);
    assertLegalHelpAssessmentValues(firstRow);
    assertSingleAssessmentSelected(firstRow);
  }

  @Test
  void exportsCsvWithDefinitionHeadersAndSingleAssessment() throws Exception {
    createSingleAssessmentForClaimAndSummaryFeeId(CLAIM_1_ID, CLAIM_1_SUMMARY_FEE_ID, true);
    MvcResult response =
        exportCsv(LEGAL_HELP_ENDPOINT, submission1.getId(), submission1.getOfficeAccountNumber());

    assertCsvHeadersMatchDefinition(
        response.getResponse().getContentAsString(), LEGAL_HELP_DEFINITION);

    Map<String, String> firstRow =
        firstDataRowByHeader(response.getResponse().getContentAsString());
    assertLegalHelpValues(firstRow);
    assertAssessmentValues(firstRow);
    assertLegalHelpAssessmentValues(firstRow);
    assertSingleAssessmentSelected(firstRow);
  }

  @ParameterizedTest
  @MethodSource("exportEndpoints")
  void returnsBadRequestWhenSubmissionIdIsNotUuidForExport(String endpoint) throws Exception {
    mockMvc
        .perform(
            get(endpoint)
                .param("submission-id", "invalid-uuid")
                .param("office", submission1.getOfficeAccountNumber())
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isBadRequest());
  }

  private static Stream<String> exportEndpoints() {
    return Stream.of(LEGAL_HELP_ENDPOINT, CRIME_LOWER_ENDPOINT, MEDIATION_ENDPOINT);
  }

  private MvcResult exportCsv(String endpoint, UUID submissionId, String office) throws Exception {
    MvcResult initialResponse =
        mockMvc
            .perform(
                get(endpoint)
                    .param("submission-id", submissionId.toString())
                    .param("office", office)
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    MvcResult response = initialResponse;
    if (initialResponse.getRequest().isAsyncStarted()) {
      response =
          mockMvc.perform(asyncDispatch(initialResponse)).andExpect(status().isOk()).andReturn();
    }

    assertThat(response.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
    return response;
  }

  // asserts

  private void assertCrimeLowerValues(Map<String, String> firstRow) {
    assertThat(firstRow.get("Providers LAA Office Number")).isEqualTo(CRIME_OFFICE);
    assertThat(firstRow.get("Submission Month")).isEqualTo("FEB-2025");
    assertThat(firstRow.get("Area of Law")).isEqualTo("CRIME_LOWER");
    assertThat(firstRow.get("Fee Code")).isEqualTo("CRIME-FEE");
    assertThat(firstRow.get("Claim Status")).isEqualTo("INVALID");
    assertThat(firstRow.get("Client surname")).isEqualTo("Davis");
    assertThat(firstRow.get("Client Initial")).isEqualTo("Chris");
    assertThat(firstRow.get("UFN")).isEqualTo("CRIME-UFN-1");
    assertThat(firstRow.get("Stage Reached (Claim Code)")).isEqualTo("CRIME-STAGE");
    assertThat(firstRow.get("Calculated Fee Detail - Fee Type")).isEqualTo("FIXED");
    assertThat(firstRow.get("Calculated Fee Detail - Fee Code Description"))
        .isEqualTo("Crime fee detail");
    assertThat(firstRow.get("Assessment - Detention Travel And Waiting Costs Amount")).isNull();
    assertThat(firstRow.get("Assessment - JR Form Filling Amount")).isNull();
  }

  private void assertLegalHelpValues(Map<String, String> firstRow) {
    assertThat(firstRow.get("Providers LAA Office Number")).isEqualTo("office1");
    assertThat(firstRow.get("Submission Month")).isEqualTo("JAN-2025");
    assertThat(firstRow.get("Area of Law")).isEqualTo("LEGAL_HELP");
    assertThat(firstRow.get("Fee Code")).isEqualTo("FEE_123");
    assertThat(firstRow.get("Case Reference Number")).isEqualTo("CASE-123");
    assertThat(firstRow.get("Client Forename")).isEqualTo("Alice");
    assertThat(firstRow.get("Client Surname")).isEqualTo("Smith");
    assertThat(firstRow.get("Case ID")).isEqualTo("CASE_ID_1");
    assertThat(firstRow.get("Calculated Fee Detail - Fee Type")).isEqualTo("DISB_ONLY");
  }

  private void assertMediationValues(Map<String, String> firstRow) {
    assertThat(firstRow.get("Providers LAA Office Number")).isEqualTo(MEDIATION_OFFICE);
    assertThat(firstRow.get("Submission Month")).isEqualTo("MAY-2025");
    assertThat(firstRow.get("Area of Law")).isEqualTo("MEDIATION");
    assertThat(firstRow.get("Mediation Submission Reference")).isEqualTo("MED-SUB-001");
    assertThat(firstRow.get("Matter Type (1:2)")).isEqualTo("MED-MATTER");
    assertThat(firstRow.get("Fee Code")).isEqualTo("MED-FEE");
    assertThat(firstRow.get("Case Reference Number")).isEqualTo("MED-CASE-001");
    assertThat(firstRow.get("Client 1 Forename")).isEqualTo("Mia");
    assertThat(firstRow.get("Client 1 Surname")).isEqualTo("Green");
    assertThat(firstRow.get("Client 2 Forename")).isEqualTo("Noah");
    assertThat(firstRow.get("Number of Mediation Sessions")).isEqualTo("4");
    assertThat(firstRow.get("Mediation Time (mins)")).isEqualTo("90");
    assertThat(firstRow.get("Referral")).isEqualTo("Court referral");
    assertThat(firstRow.get("Calculated Fee Detail - Fee Type")).isEqualTo("FIXED");
    assertThat(firstRow.get("Calculated Fee Detail - Fee Code Description"))
        .isEqualTo("Mediation fee detail");
    assertThat(firstRow.get("Assessment - Detention Travel And Waiting Costs Amount")).isNull();
    assertThat(firstRow.get("Assessment - JR Form Filling Amount")).isNull();
  }

  private void assertAssessmentValues(Map<String, String> firstRow) {
    assertThat(firstRow.get("Assessment - Type")).isEqualTo("ESCAPE_CASE_ASSESSMENT");
    assertThat(new BigDecimal(firstRow.get("Assessment - Fixed Fee Amount")))
        .isEqualByComparingTo(new BigDecimal("100.00"));
    assertThat(new BigDecimal(firstRow.get("Assessment - Net Profit Costs Amount")))
        .isEqualByComparingTo("50.0");
    assertThat(new BigDecimal(firstRow.get("Assessment - Disbursement Amount")))
        .isEqualByComparingTo("15.0");
    assertThat(new BigDecimal(firstRow.get("Assessment - Disbursement VAT Amount")))
        .isEqualByComparingTo("3.0");
    assertThat(new BigDecimal(firstRow.get("Assessment - Net Cost Of Counsel Amount")))
        .isEqualByComparingTo("25.0");
    assertThat(new BigDecimal(firstRow.get("Assessment - Net Travel Costs Amount")))
        .isEqualByComparingTo("8.0");
    assertThat(new BigDecimal(firstRow.get("Assessment - Net Waiting Costs Amount")))
        .isEqualByComparingTo("10.0");
    assertThat(firstRow.get("Assessment - VAT Indicator")).isEqualTo("t");
    assertThat(new BigDecimal(firstRow.get("Assessment - Total VAT"))).isEqualByComparingTo("1.0");
    assertThat(new BigDecimal(firstRow.get("Assessment - Total Inc VAT")))
        .isEqualByComparingTo("2.0");
    assertAssessmentClaimValues(firstRow);
  }

  private void assertAssessmentClaimValues(Map<String, String> firstRow) {
    assertThat(new BigDecimal(firstRow.get("Final Claim Value"))).isEqualByComparingTo("240.00");
    assertThat(new BigDecimal(firstRow.get("Final Claim Value VAT"))).isEqualByComparingTo("200.0");
  }

  private void assertDefaultClaimValues(Map<String, String> firstRow) {
    BigDecimal totalAmount = new BigDecimal(firstRow.get("Calculated Fee Detail - Total Amount"));
    BigDecimal vatAmount =
        new BigDecimal(firstRow.get("Calculated Fee Detail - Calculated VAT Amount"));
    BigDecimal finalClaimValue = new BigDecimal(firstRow.get("Final Claim Value"));
    BigDecimal finalClaimValueVat = new BigDecimal(firstRow.get("Final Claim Value VAT"));

    assertThat(finalClaimValue).isEqualByComparingTo(totalAmount);
    assertThat(finalClaimValueVat).isEqualByComparingTo(vatAmount);
  }

  private void assertLegalHelpAssessmentValues(Map<String, String> firstRow) {
    assertThat(
            new BigDecimal(firstRow.get("Assessment - Detention Travel And Waiting Costs Amount")))
        .isEqualByComparingTo(new BigDecimal("300.00"));
    assertThat(new BigDecimal(firstRow.get("Assessment - JR Form Filling Amount")))
        .isEqualByComparingTo(new BigDecimal("99.99"));
  }

  private void assertLatestAssessmentSelected(Map<String, String> firstRow) {
    assertThat(firstRow.get("Assessment - Reason")).isEqualTo("Latest generic assessment");
  }

  private void assertSingleAssessmentSelected(Map<String, String> firstRow) {
    assertThat(firstRow.get("Assessment - Reason")).isEqualTo("Single generic assessment");
  }
}
