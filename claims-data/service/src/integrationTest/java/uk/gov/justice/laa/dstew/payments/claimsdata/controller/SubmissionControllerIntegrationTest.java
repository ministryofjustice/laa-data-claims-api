package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.SubmissionMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.SubmissionMapperImpl;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.SubmissionsResultSetMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.SubmissionsResultSetMapperImpl;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetails;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionsResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.BulkSubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.*;

@TestInstance(Lifecycle.PER_CLASS)
public class SubmissionControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private SubmissionRepository submissionRepository;

    @Autowired private BulkSubmissionRepository bulkSubmissionRepository;

    private final SubmissionMapper submissionMapper = new SubmissionMapperImpl();

    private final SubmissionsResultSetMapper submissionsResultSetMapper = new SubmissionsResultSetMapperImpl();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String AUTHORIZATION_HEADER = "Authorization";

    // must match application-test.yml for test-runner token
    private static final String AUTHORIZATION_TOKEN = "f67f968e-b479-4e61-b66e-f57984931e56";

    @BeforeAll
    void setup() {
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
    }

    @Test
    void shouldGetSubmissions_Returns200() throws Exception {
        var bulkSubmission =
                BulkSubmission.builder()
                        .data(new GetBulkSubmission200ResponseDetails())
                        .status(BulkSubmissionStatus.READY_FOR_PARSING)
                        .createdByUserId(USER_ID)
                        .createdOn(Instant.now())
                        .updatedOn(Instant.now())
                        .build();
        bulkSubmissionRepository.save(bulkSubmission);

        var submission =
                Submission.builder()
                        .id(SUBMISSION_1_ID)
                        .bulkSubmissionId(bulkSubmission.getId())
                        .officeAccountNumber("office1")
                        .submissionPeriod("JAN-25")
                        .areaOfLaw("CIVIL")
                        .status(SubmissionStatus.CREATED)
                        .scheduleNumber("office1/CIVIL")
                        .previousSubmissionId(SUBMISSION_1_ID)
                        .isNilSubmission(false)
                        .numberOfClaims(5)
                        .createdByUserId(USER_ID)
                        .build();
        var savedSubmission = submissionRepository.save(submission);

        MvcResult result = mockMvc
                .perform(
                        get(
                                API_URI_PREFIX + "/submissions")
                                .param("offices", "office1")
                                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();

        var submissionsResultSet = OBJECT_MAPPER.readValue(responseBody, SubmissionsResultSet.class);

        assertThat(submissionsResultSet.getContent().getFirst().getSubmissionId()).isEqualTo(savedSubmission.getId());
        assertThat(submissionsResultSet.getContent().getFirst().getStatus()).isEqualTo(SubmissionStatus.CREATED);

        submissionRepository.delete(submission);
        bulkSubmissionRepository.delete(bulkSubmission);
    }

    @Test
    void shouldNotGetSubmissions_NoOffices_BadRequest() throws Exception {
        mockMvc.perform(
                get(API_URI_PREFIX + "/submissions")
                        .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN))
                .andExpect(status().isBadRequest())
                .andReturn();
    }
}
