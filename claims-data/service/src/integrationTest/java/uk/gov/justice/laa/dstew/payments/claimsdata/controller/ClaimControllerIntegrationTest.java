package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.ClaimsDataApplication;

@ActiveProfiles("h2")
@SpringBootTest(classes = ClaimsDataApplication.class)
@AutoConfigureMockMvc
@Transactional
public class ClaimControllerIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void shouldGetAllClaims() throws Exception {
    mockMvc
        .perform(get("/api/v1/claims"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.*", hasSize(5)));
  }

  @Test
  void shouldGetClaim() throws Exception {
    mockMvc.perform(get("/api/v1/claims/1"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.name").value("Claim One"))
        .andExpect(jsonPath("$.description").value("This is a description of Claim One."));
  }

  @Test
  void shouldCreateClaim() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/claims")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Claim Six\", \"description\": \"This is a description of Claim Six.\"}")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated());
  }

  @Test
  void shouldUpdateClaim() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/claims/2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\": 2, \"name\": \"Claim Two\", \"description\": \"This is a updated description of Claim Three.\"}")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());
  }

  @Test
  void shouldDeleteClaim() throws Exception {
    mockMvc.perform(delete("/api/v1/claims/3")).andExpect(status().isNoContent());
  }
}
