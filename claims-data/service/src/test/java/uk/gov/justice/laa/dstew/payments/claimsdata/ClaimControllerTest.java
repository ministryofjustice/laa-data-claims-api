package uk.gov.justice.laa.dstew.payments.claimsdata;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.ClaimController;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimRequestBody;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.ClaimService;

@WebMvcTest(ClaimController.class)
@ImportAutoConfiguration(exclude = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
})
class ClaimControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ClaimService mockClaimService;

  @Test
  void getClaims_returnsOkStatusAndAllClaims() throws Exception {
    List<Claim> claims = List.of(Claim.builder().id(1L).name("Claim One").description("This is a test claim one.").build(),
        Claim.builder().id(2L).name("Claim Two").description("This is a test claim two.").build());
    when(mockClaimService.getAllClaims()).thenReturn(claims);

    mockMvc.perform(get("/api/v1/claims"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.*", hasSize(2)));
  }

  @Test
  void getClaimById_returnsOkStatusAndOneClaim() throws Exception {
    when(mockClaimService.getClaim(1L)).thenReturn(Claim.builder().id(1L).name("Claim One").description("This is a test claim one.").build());

    mockMvc.perform(get("/api/v1/claims/1"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.name").value("Claim One"))
        .andExpect(jsonPath("$.description").value("This is a test claim one."));
  }

  @Test
  void createClaim_returnsCreatedStatusAndLocationHeader() throws Exception {
    ClaimRequestBody claimRequestBody = ClaimRequestBody.builder().name("Claim Three").description("This is an updated claim three.").build();
    when(mockClaimService.createClaim(claimRequestBody))
        .thenReturn(3L);

    mockMvc
        .perform(post("/api/v1/claims")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\": \"Claim Three\", \"description\": \"This is an updated claim three.\"}")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", containsString("/api/v1/claims/3")));
  }

  @Test
  void createClaim_returnsBadRequestStatus() throws Exception {
    mockMvc
        .perform(post("/api/v1/claims")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\": \"Claim Three\"}")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(content().string("{\"type\":\"about:blank\",\"title\":\"Bad Request\"," +
            "\"status\":400,\"detail\":\"Invalid request content.\",\"instance\":\"/api/v1/claims\"}"));

    verify(mockClaimService, never()).createClaim(any(ClaimRequestBody.class));
  }

  @Test
  void updateClaim_returnsNoContentStatus() throws Exception {
    mockMvc
        .perform(put("/api/v1/claims/2")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\": \"Claim Two\", \"description\": \"This is an updated claim two.\"}")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    verify(mockClaimService).updateClaim(eq(2L), any(ClaimRequestBody.class));
  }

  @Test
  void updateClaim_returnsBadRequestStatus() throws Exception {
    mockMvc
        .perform(put("/api/v1/claims/2")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"description\": \"This is an updated claim two.\"}")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(content().string("{\"type\":\"about:blank\",\"title\":\"Bad Request\"," +
            "\"status\":400,\"detail\":\"Invalid request content.\",\"instance\":\"/api/v1/claims/2\"}"));

    verify(mockClaimService, never()).updateClaim(eq(2L), any(ClaimRequestBody.class));
  }

  @Test
  void deleteClaim_returnsNoContentStatus() throws Exception {
    mockMvc.perform(delete("/api/v1/claims/3")).andExpect(status().isNoContent());

    verify(mockClaimService).deleteClaim(3L);
  }
}
