package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
import uk.gov.justice.laa.dstew.payments.claimsdata.model.InquestDepartmentReference;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.AmendmentReferenceService;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.InquestDepartmentReferenceService;

@WebMvcTest(AmendmentReferenceController.class)
@ImportAutoConfiguration(
    exclude = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc(addFilters = false)
class InquestDepartmentReferenceControllerTest {
  @Autowired private MockMvc mockMvc;
  @MockitoBean private AmendmentReferenceService amendmentReferenceService;
  @MockitoBean private InquestDepartmentReferenceService inquestDepartmentReferenceService;

  @Test
  void returnsOrderedActiveAndInactiveDepartmentsInStableShape() throws Exception {
    when(inquestDepartmentReferenceService.getAll())
        .thenReturn(
            List.of(
                new InquestDepartmentReference("AGO", "Attorney General's Office", 1, true),
                new InquestDepartmentReference("OLD", "Old department", 2, false)));

    mockMvc
        .perform(get(SystemReferencePaths.INQUEST_DEPARTMENTS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").value("AGO"))
        .andExpect(jsonPath("$[1].is_active").value(false));
  }
}
