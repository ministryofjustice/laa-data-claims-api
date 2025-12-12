package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_USER_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;

import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.MatterStart;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CategoryCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MatterStartGet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MatterStartPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MediationType;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@TestInstance(Lifecycle.PER_CLASS)
public class MatterStartsControllerIntegrationTest extends AbstractIntegrationTest {

  public static final String POST_MATTER_START_URI = "/submissions/{submissionId}/matter-starts";

  private static final String GET_ALL_MATTER_STARTS_URI =
      "/submissions/{submissionId}/matter-starts";

  public static final String GET_MATTER_STARTS_URI = GET_ALL_MATTER_STARTS_URI + "/{msId}";

  @BeforeEach
  void setup() {
    super.abstractSetup();
    seedSubmissionsData();
  }

  @Test
  void postMatterStart_shouldCreate() throws Exception {
    // given: a MatterStart Post payload
    MatterStartPost matterStartPost =
        MatterStartPost.builder()
            .createdByUserId(API_USER_ID)
            .categoryCode(CategoryCode.AAP)
            .build();

    // when: calling POST endpoint for matter starts
    mockMvc
        .perform(
            post(API_URI_PREFIX + POST_MATTER_START_URI, submission1.getId())
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(matterStartPost)))
        .andExpect(status().isCreated())
        .andReturn();

    // then: matter starts is correctly created
    List<MatterStart> savedMatterStarts =
        matterStartRepository.findBySubmissionId(submission1.getId());
    assertThat(savedMatterStarts.size()).isEqualTo(1);
    assertThat(savedMatterStarts.getFirst().getCategoryCode())
        .isEqualTo(CategoryCode.AAP.getValue());
    assertThat(savedMatterStarts.getFirst().getCreatedByUserId()).isEqualTo(API_USER_ID);
  }

  @Test
  void postMatterStart_shouldReturnNotFound() throws Exception {
    // when: calling POST endpoint with invalid submission ID, should return Not Found
    MatterStartPost matterStartPost =
        MatterStartPost.builder().categoryCode(CategoryCode.AAP).build();
    mockMvc
        .perform(
            post(API_URI_PREFIX + POST_MATTER_START_URI, UUID.randomUUID())
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OBJECT_MAPPER.writeValueAsString(matterStartPost)))
        .andExpect(status().isNotFound())
        .andReturn();
  }

  @Test
  void postMatterStart_shouldReturnBadRequest() throws Exception {
    // when: calling POST endpoint with invalid payload, should return Bad Request
    String invalidJson = "{ \"status\": \"INVALID_ENUM\" }";
    mockMvc
        .perform(
            post(API_URI_PREFIX + POST_MATTER_START_URI, submission1.getId())
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
        .andExpect(status().isBadRequest())
        .andReturn();
  }

  @Test
  @Transactional
  void getMatterStart_shouldReturn200() throws Exception {
    // given: a MatterStart created on DB
    MatterStart matterStart =
        MatterStart.builder()
            .id(Uuid7.timeBasedUuid())
            .submission(submissionRepository.findById(submission1.getId()).orElseThrow())
            .scheduleReference("REF1")
            .categoryCode(CategoryCode.AAP.getValue())
            .procurementAreaCode("AREA1")
            .accessPointCode("ACCESS1")
            .deliveryLocation("LONDON")
            .createdByUserId("user1")
            .createdOn(Instant.now())
            .updatedOn(Instant.now())
            .build();
    matterStartRepository.save(matterStart);

    // when: calling GET endpoint with a valid submission and matter start ID
    MvcResult mvcResult =
        mockMvc
            .perform(
                get(
                        API_URI_PREFIX + GET_MATTER_STARTS_URI,
                        submission1.getId(),
                        matterStart.getId())
                    .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
            .andExpect(status().isOk())
            .andReturn();

    // then: matter start is correctly retrieved
    MatterStartGet result =
        OBJECT_MAPPER.readValue(mvcResult.getResponse().getContentAsString(), MatterStartGet.class);
    assertThat(result.getScheduleReference()).isEqualTo(matterStart.getScheduleReference());
    assertThat(result.getCategoryCode()).isEqualTo(CategoryCode.AAP);
    assertThat(result.getProcurementAreaCode()).isEqualTo(matterStart.getProcurementAreaCode());
    assertThat(result.getAccessPointCode()).isEqualTo(matterStart.getAccessPointCode());
    assertThat(result.getDeliveryLocation()).isEqualTo(matterStart.getDeliveryLocation());
  }

  @Test
  void getMatterStart_shouldReturnNotFound() throws Exception {
    // when: calling GET endpoint with invalid matter start ID, should return not found
    mockMvc
        .perform(
            get(API_URI_PREFIX + GET_MATTER_STARTS_URI, submission1.getId(), UUID.randomUUID())
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
        .andExpect(status().isNotFound())
        .andReturn();
  }

  @Nested
  class GetAllMatterStartsForSubmission {
    @DisplayName("Status 200: when a valid submission ID exists in the system")
    @Test
    @Transactional
    void getAllMatterStart_shouldReturnOK() throws Exception {
      var matterStartEntity =
          MatterStart.builder()
              .id(Uuid7.timeBasedUuid())
              .submission(submission1)
              .scheduleReference("REF1")
              .categoryCode(CategoryCode.AAP.getValue())
              .procurementAreaCode("AREA1")
              .accessPointCode("ACCESS1")
              .deliveryLocation("LONDON")
              .createdByUserId("user1")
              .mediationType(MediationType.MDAC_ALL_ISSUES_CO)
              .numberOfMatterStarts(25)
              .createdOn(Instant.now())
              .updatedOn(Instant.now())
              .build();
      matterStartRepository.save(matterStartEntity);

      MvcResult mvcResult =
          mockMvc
              .perform(
                  get(API_URI_PREFIX + GET_ALL_MATTER_STARTS_URI, submission1.getId())
                      .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
              .andExpect(status().isOk())
              .andReturn();

      var expectedResults =
          """
              {
              "submission_id":"%s",
              "matter_starts":[
                   {
                   "schedule_reference":"REF1",
                   "category_code":"AAP",
                   "procurement_area_code":"AREA1",
                   "access_point_code":"ACCESS1",
                   "delivery_location":"LONDON",
                   "mediation_type":"MDAC All Issues Co",
                   "number_of_matter_starts":25,
                   "created_by_user_id":"user1"
                   }
                 ]
              }
              """;

      assertThat(OBJECT_MAPPER.readTree(mvcResult.getResponse().getContentAsString()))
          .isEqualTo(OBJECT_MAPPER.readTree(String.format(expectedResults, submission1.getId())));
    }

    @DisplayName("Status 400: when a submission ID with an invalid format (non-UUID)")
    @Test
    void getAllMatterStart_shouldReturnBadRequest() throws Exception {
      mockMvc
          .perform(
              get(API_URI_PREFIX + GET_ALL_MATTER_STARTS_URI, "invalid-submission_id_format")
                  .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
          .andExpect(status().isBadRequest());
    }

    @DisplayName("Status 404: when a submission ID does not exist in the database")
    @Test
    void getAllMatterStart_shouldReturnNotFound() throws Exception {
      mockMvc
          .perform(
              get(API_URI_PREFIX + GET_ALL_MATTER_STARTS_URI, UUID.randomUUID())
                  .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
          .andExpect(status().isNotFound());
    }

    @DisplayName("Status 401: when endpoint is accessed without proper authentication")
    @Test
    void getAllMatterStart_shouldReturnUnauthorized() throws Exception {
      mockMvc
          .perform(get(API_URI_PREFIX + GET_ALL_MATTER_STARTS_URI, submission1.getId()))
          .andExpect(status().isUnauthorized());
    }
  }
}
