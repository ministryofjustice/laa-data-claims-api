package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.BULK_SUBMISSION_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.USER_ID;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionInvalidFileException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionValidationException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.DataClaimsExceptionHandler;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CreateBulkSubmission201Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.BulkSubmissionService;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;
import uk.gov.justice.laa.dstew.payments.claimsdata.validator.BulkSubmissionFileValidator;

@ExtendWith(SpringExtension.class)
@ContextConfiguration
@WebAppConfiguration
@DisplayName("Bulk Submission Controller Test")
class BulkSubmissionControllerTest {

  private static final String BULK_SUBMISSIONS_URI = API_URI_PREFIX + "/bulk-submissions";
  private static final String BULK_SUBMISSIONS_ID_URI = BULK_SUBMISSIONS_URI + "/{id}";

  @InjectMocks private BulkSubmissionController bulkSubmissionController;

  @Mock private BulkSubmissionService bulkSubmissionService;

  @Mock private BulkSubmissionFileValidator bulkSubmissionFileValidator;

  protected MockMvcTester mockMvc;

  protected MockMultipartFile mockMultipartFile;

  @BeforeEach
  void setup() {
    MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter =
        new MappingJackson2HttpMessageConverter();
    mappingJackson2HttpMessageConverter.setObjectMapper(new ObjectMapper());
    mockMvc =
        MockMvcTester.create(
                standaloneSetup(bulkSubmissionController)
                    .setControllerAdvice(new DataClaimsExceptionHandler())
                    .build())
            .withHttpMessageConverters(singletonList(mappingJackson2HttpMessageConverter));
    mockMultipartFile =
        new MockMultipartFile("test-file", "test-file.csv", "text/csv", "one,two".getBytes());
  }

  @Nested
  @DisplayName("POST: " + BULK_SUBMISSIONS_URI)
  class PostBulkSubmissionTests {

    @Test
    @DisplayName("Should return 201 response")
    void shouldReturn201Response() throws IOException {
      CreateBulkSubmission201Response expected = new CreateBulkSubmission201Response();
      expected.setBulkSubmissionId(Uuid7.timeBasedUuid());
      expected.setSubmissionIds(singletonList(SUBMISSION_ID));

      when(bulkSubmissionService.submitBulkSubmissionFile(any(), any(), any()))
          .thenReturn(expected);

      // Perform POST with multipart file
      assertThat(
              mockMvc.perform(
                  multipart(BULK_SUBMISSIONS_URI)
                      .file("file", mockMultipartFile.getBytes())
                      .param("userId", USER_ID)
                      .param("offices", "OFFICE1,OFFICE2")))
          .hasStatus(201)
          .hasHeader(
              "Location",
              String.format("http://localhost%s/submissions/%s", API_URI_PREFIX, SUBMISSION_ID))
          .bodyJson()
          .convertTo(CreateBulkSubmission201Response.class)
          .isEqualTo(expected);
    }

    @Test
    @DisplayName("Should return 400 response")
    void shouldReturn400Response() throws IOException {
      doThrow(new BulkSubmissionValidationException("This error was found"))
          .when(bulkSubmissionFileValidator)
          .validate(any(MockMultipartFile.class));

      // Perform POST with multipart file
      assertThat(
              mockMvc.perform(
                  multipart(BULK_SUBMISSIONS_URI)
                      .file("file", mockMultipartFile.getBytes())
                      .param("userId", USER_ID)
                      .param("offices", "OFFICE1,OFFICE2")))
          .hasStatus(400)
          .bodyText()
          .contains("This error was found");
    }

    @Test
    @DisplayName("Should return 400 response when user ID is missing")
    void shouldReturn400ResponseWhenUserIdIsMissing() throws IOException {
      doThrow(new IllegalArgumentException("This error was found"))
          .when(bulkSubmissionFileValidator)
          .validate(any(MockMultipartFile.class));

      // Perform POST with multipart file
      assertThat(
              mockMvc.perform(
                  multipart(BULK_SUBMISSIONS_URI).file("file", mockMultipartFile.getBytes())))
          .hasStatus(400)
          .bodyText()
          .contains("Required parameter 'userId' is not present.");
    }

    @Test
    @DisplayName("Should return 415 when media type is not supported")
    void shouldReturn415ResponseWhenMediaTypeIsNotSupported() throws IOException {
      doThrow(new BulkSubmissionInvalidFileException("Unsupported media type"))
          .when(bulkSubmissionFileValidator)
          .validate(any(MockMultipartFile.class));

      // Perform POST with multipart file
      assertThat(
              mockMvc.perform(
                  multipart(BULK_SUBMISSIONS_URI)
                      .file("file", mockMultipartFile.getBytes())
                      .param("userId", USER_ID)
                      .param("offices", "OFFICE1,OFFICE2")))
          .hasStatus(415)
          .bodyText()
          .contains("Unsupported media type");
    }

    @Test
    @DisplayName("Should return 415 response when file is missing")
    void shouldReturn415ResponseWhenFileIsMissing() {
      doThrow(new IllegalArgumentException("This error was found"))
          .when(bulkSubmissionFileValidator)
          .validate(any(MockMultipartFile.class));

      // Perform POST with multipart file
      assertThat(mockMvc.perform(post(BULK_SUBMISSIONS_URI).param("userId", USER_ID)))
          .hasStatus(415);
    }
  }

  @Nested
  @DisplayName("GET: " + BULK_SUBMISSIONS_ID_URI)
  class GetBulkSubmissionTests {

    @Test
    @DisplayName("Should return 200 response")
    void shouldReturn200Response() {
      UUID id = Uuid7.timeBasedUuid();

      var expectedDetails = ClaimsDataTestUtil.getBulkSubmission200ResponseDetails();

      var expectedResponse = new GetBulkSubmission200Response();
      expectedResponse.setBulkSubmissionId(id);
      expectedResponse.setStatus(BulkSubmissionStatus.READY_FOR_PARSING);
      expectedResponse.details(expectedDetails);

      when(bulkSubmissionService.getBulkSubmission(id)).thenReturn(expectedResponse);

      assertThat(mockMvc.perform(get(BULK_SUBMISSIONS_URI + "/{id}", id))).hasStatus(200);
    }
  }

  @Nested
  @DisplayName("PATCH: " + BULK_SUBMISSIONS_ID_URI)
  class PatchBulkSubmissionTests {

    @Test
    @DisplayName("Should return 204 response")
    void shouldReturn204Response_whenJsonWellFormed() {
      var body =
          """
          {
            "bulk_submission_id": "ae60657b-89a1-4a33-819d-331a9f5033c7",
            "status": "VALIDATION_FAILED",
            "error_code": "V100",
            "error_description": "This is the error message",
            "updated_by_user_id": "123"
          }""";

      // Perform PATCH with body content
      assertThat(
              mockMvc
                  .patch()
                  .uri(BULK_SUBMISSIONS_ID_URI, BULK_SUBMISSION_ID)
                  .content(body)
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .hasStatus(204);

      verify(bulkSubmissionService).updateBulkSubmission(eq(BULK_SUBMISSION_ID), any());
    }

    @Test
    @DisplayName("Should return 400 response")
    void shouldReturn400Response_whenJsonInWrongFormat() {
      var body =
          """
            "bulk_submission_id": "ae60657b-89a1-4a33-819d-331a9f5033c7",
            "status": "VALIDATION_FAILED",
            "error_code": "V100",
            "error_description": "This is the error message",
            "updated_by_user_id": "123"
          }""";

      // Perform PATCH with bad body content
      assertThat(
              mockMvc
                  .patch()
                  .uri(BULK_SUBMISSIONS_ID_URI, BULK_SUBMISSION_ID)
                  .content(body)
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .hasStatus(400);

      verify(bulkSubmissionService, never()).updateBulkSubmission(eq(BULK_SUBMISSION_ID), any());
    }
  }
}
