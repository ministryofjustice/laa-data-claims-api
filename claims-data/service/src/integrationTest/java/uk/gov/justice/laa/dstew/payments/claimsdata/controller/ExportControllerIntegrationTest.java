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

  private UUID crimeSubmissionId;
  private UUID mediationSubmissionId;

  @BeforeEach
  void setup() {
    seedClaimsData();
    crimeSubmissionId = createCrimeLowerExportData(CRIME_OFFICE);
    mediationSubmissionId = createMediationExportData(MEDIATION_OFFICE);
  }

  @Test
  void exportsLegalHelpCsvWithDefinitionHeadersAndSeededRowValues() throws Exception {
    createAssessmentDataForClaimAndSummaryFeeId(CLAIM_1_ID, CLAIM_1_SUMMARY_FEE_ID);
    MvcResult response =
        exportCsv(LEGAL_HELP_ENDPOINT, submission1.getId(), submission1.getOfficeAccountNumber());

    assertCsvHeadersMatchDefinition(
        response.getResponse().getContentAsString(), LEGAL_HELP_DEFINITION);

    Map<String, String> firstRow =
        firstDataRowByHeader(response.getResponse().getContentAsString());
    assertThat(firstRow.get("Providers LAA Office Number")).isEqualTo("office1");
    assertThat(firstRow.get("Submission Month")).isEqualTo("JAN-2025");
    assertThat(firstRow.get("Area of Law")).isEqualTo("LEGAL_HELP");
    assertThat(firstRow.get("Fee Code")).isEqualTo("FEE_123");
    assertThat(firstRow.get("Case Reference Number")).isEqualTo("CASE-123");
    assertThat(firstRow.get("Client Forename")).isEqualTo("Alice");
    assertThat(firstRow.get("Client Surname")).isEqualTo("Smith");
    assertThat(firstRow.get("Case ID")).isEqualTo("CASE_ID_1");
    assertThat(firstRow.get("Calculated Fee Detail - Fee Type")).isEqualTo("DISB_ONLY");
    assertThat(firstRow.get("Assessment - Type")).isEqualTo("ESCAPE_CASE_ASSESSMENT");
    assertThat(firstRow.get("Assessment - Reason")).isEqualTo("Latest generic assessment");
    assertThat(
            new BigDecimal(firstRow.get("Assessment - Detention Travel And Waiting Costs Amount")))
        .isEqualByComparingTo(new BigDecimal("300.00"));
    assertThat(new BigDecimal(firstRow.get("Assessment - JR Form Filling Amount")))
        .isEqualByComparingTo(new BigDecimal("99.99"));
    assertThat(new BigDecimal(firstRow.get("Assessment - Final Claim Value")))
        .isEqualByComparingTo(new BigDecimal("240.00"));
  }

  @Test
  void exportsCrimeLowerCsvWithDefinitionHeadersAndSeededRowValues() throws Exception {
    MvcResult response = exportCsv(CRIME_LOWER_ENDPOINT, crimeSubmissionId, CRIME_OFFICE);

    assertCsvHeadersMatchDefinition(
        response.getResponse().getContentAsString(), CRIME_LOWER_DEFINITION);

    Map<String, String> firstRow =
        firstDataRowByHeader(response.getResponse().getContentAsString());
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
  }

  @Test
  void exportsMediationCsvWithDefinitionHeadersAndSeededRowValues() throws Exception {
    MvcResult response = exportCsv(MEDIATION_ENDPOINT, mediationSubmissionId, MEDIATION_OFFICE);

    assertCsvHeadersMatchDefinition(
        response.getResponse().getContentAsString(), MEDIATION_DEFINITION);

    Map<String, String> firstRow =
        firstDataRowByHeader(response.getResponse().getContentAsString());
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
    assertThat(firstRow.get("Assessment - Type")).isEqualTo("ESCAPE_CASE_ASSESSMENT");
    assertThat(firstRow.get("Assessment - Reason")).isEqualTo("Latest generic assessment");
    assertThat(new BigDecimal(firstRow.get("Assessment - Final Claim Value")))
        .isEqualByComparingTo("240.00");
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
}
