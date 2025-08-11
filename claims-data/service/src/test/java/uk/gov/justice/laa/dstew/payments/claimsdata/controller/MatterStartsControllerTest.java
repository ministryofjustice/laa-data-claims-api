package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CreateMatterStartRequest;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.MatterStartService;

@WebMvcTest(MatterStartsController.class)
@ImportAutoConfiguration(exclude = {org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class})
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class MatterStartsControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private MatterStartService matterStartService;

  @Test
  void createMatterStart_returnsCreatedStatusAndLocationHeader() throws Exception {
    final UUID submissionId = UUID.randomUUID();
    final UUID matterStartId = UUID.randomUUID();
    when(matterStartService.createMatterStart(eq(submissionId), any(CreateMatterStartRequest.class)))
        .thenReturn(matterStartId);

    final String body = "{"
        + "\"schedule_reference\":\"SCH-123\","
        + "\"category_code\":\"CAT-01\","
        + "\"procurement_area_code\":\"PAC-1\","
        + "\"access_point_code\":\"AP-9\","
        + "\"delivery_location\":\"LOC-77\","
        + "\"number_of_matter_starts\":3"
        + "}";

    mockMvc.perform(post("/api/v0/submissions/{id}/matter-starts", submissionId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location",
            containsString("/api/v0/submissions/" + submissionId + "/matter-starts/" + matterStartId)))
        .andExpect(jsonPath("$.id").value(matterStartId.toString()));

    verify(matterStartService).createMatterStart(eq(submissionId), any(CreateMatterStartRequest.class));
  }
}
