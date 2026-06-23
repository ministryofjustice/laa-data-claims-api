package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AmendmentReasonReference;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AmendmentRequestedByReference;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AmendmentRequestedByReferenceList;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.AmendmentReferenceService;

@WebMvcTest(AmendmentReferenceController.class)
@ImportAutoConfiguration(
    exclude = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AmendmentReferenceController")
class AmendmentReferenceControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AmendmentReferenceService amendmentReferenceService;

  @Nested
  @DisplayName("GET /api/v1/system/references/amendment-requested-by")
  class GetAmendmentRequestedBy {

    @Test
    @DisplayName("Status 200: returns the nested Requested By / reasons structure")
    void returnsNestedReferenceData() throws Exception {
      AmendmentRequestedByReferenceList payload =
          new AmendmentRequestedByReferenceList()
              .requestedBy(
                  List.of(
                      new AmendmentRequestedByReference()
                          .code("PROVIDER")
                          .displayLabel("Provider")
                          .displayOrder(10)
                          .reasons(
                              List.of(
                                  new AmendmentReasonReference()
                                      .code("PROVIDER_ERROR")
                                      .displayLabel("Provider Error")
                                      .displayOrder(10)))));

      when(amendmentReferenceService.getAmendmentRequestedByReferences()).thenReturn(payload);

      mockMvc
          .perform(get(SystemReferencePaths.AMENDMENT_REQUESTED_BY))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.requested_by[0].code").value("PROVIDER"))
          .andExpect(jsonPath("$.requested_by[0].reasons[0].code").value("PROVIDER_ERROR"));
    }

    @Test
    @DisplayName("Status 200: returns an empty Requested By list when no reference data exists")
    void returnsEmptyRequestedByList() throws Exception {
      when(amendmentReferenceService.getAmendmentRequestedByReferences())
          .thenReturn(new AmendmentRequestedByReferenceList().requestedBy(List.of()));

      mockMvc
          .perform(get(SystemReferencePaths.AMENDMENT_REQUESTED_BY))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.requested_by").isEmpty());
    }
  }
}
