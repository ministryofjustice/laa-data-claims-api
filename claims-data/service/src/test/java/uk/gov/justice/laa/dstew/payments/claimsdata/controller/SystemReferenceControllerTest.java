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
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GovernmentDepartment;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GovernmentDepartmentsList;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.AmendmentReferenceService;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.GovernmentDepartmentService;

@WebMvcTest(SystemReferenceController.class)
@ImportAutoConfiguration(
    exclude = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("SystemReferenceController")
class SystemReferenceControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AmendmentReferenceService amendmentReferenceService;
  @MockitoBean private GovernmentDepartmentService governmentDepartmentService;

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
                                      .displayLabel("Provider error")
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

  @Nested
  @DisplayName("GET /api/v1/system/references/departments")
  class GetGovernmentDepartments {

    @Test
    @DisplayName("Status 200: returns the government departments")
    void returnsGovernmentDepartments() throws Exception {
      when(governmentDepartmentService.getGovernmentDepartments())
          .thenReturn(
              GovernmentDepartmentsList.builder()
                  .governmentDepartments(
                      List.of(GovernmentDepartment.builder().name("Home Office").build()))
                  .build());

      mockMvc
          .perform(get(SystemReferencePaths.GOVERNMENT_DEPARTMENTS))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.government_departments[0].name").value("Home Office"));
    }

    @Test
    @DisplayName("Status 200: returns an empty list when no government departments exist")
    void returnsEmptyGovernmentDepartmentsList() throws Exception {
      when(governmentDepartmentService.getGovernmentDepartments())
          .thenReturn(GovernmentDepartmentsList.builder().governmentDepartments(List.of()).build());

      mockMvc
          .perform(get(SystemReferencePaths.GOVERNMENT_DEPARTMENTS))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.government_departments").isEmpty());
    }
  }
}
