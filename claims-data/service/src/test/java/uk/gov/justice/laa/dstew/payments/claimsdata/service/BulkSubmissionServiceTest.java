package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_USER_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.BULK_SUBMISSION_ID;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionAreaOfLawException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionValidationException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.BulkSubmissionMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionErrorCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CreateBulkSubmission201Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetails;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetailsOffice;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.GetBulkSubmission200ResponseDetailsSchedule;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.BulkSubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@ExtendWith(MockitoExtension.class)
class BulkSubmissionServiceTest {

  @Mock BulkSubmissionFileService bulkSubmissionFileService;

  @Mock BulkSubmissionRepository bulkSubmissionRepository;

  @SuppressWarnings("unused") // This is needed by the bulkSubmissionService
  @Mock
  SubmissionEventPublisherService submissionEventPublisherService;

  @Mock BulkSubmissionMapper bulkSubmissionMapper;

  @Spy @InjectMocks BulkSubmissionService bulkSubmissionService;

  @Test
  @DisplayName("Uploads a bulk submission file")
  void submitBulkSubmissionFile() {
    // Setup and mock
    MultipartFile file = new MockMultipartFile("filePath.csv", new byte[0]);
    String userId = "test-user-id";
    GetBulkSubmission200ResponseDetails mockDetails =
        mock(GetBulkSubmission200ResponseDetails.class);
    GetBulkSubmission200ResponseDetailsOffice mockOffice =
        mock(GetBulkSubmission200ResponseDetailsOffice.class);
    GetBulkSubmission200ResponseDetailsSchedule mockSchedule =
        mock(GetBulkSubmission200ResponseDetailsSchedule.class);
    when(mockDetails.getOffice()).thenReturn(mockOffice);
    when(mockDetails.getSchedule()).thenReturn(mockSchedule);
    when(mockSchedule.getSubmissionPeriod()).thenReturn("APR-2025");
    when(mockSchedule.getAreaOfLaw()).thenReturn("LEGAL HELP");
    when(mockOffice.getAccount()).thenReturn("TEST");

    doReturn(mockDetails).when(bulkSubmissionService).getBulkSubmissionDetails(file);

    // Test
    CreateBulkSubmission201Response response =
        bulkSubmissionService.submitBulkSubmissionFile(userId, file, List.of("TEST"));

    // Assert
    assertNotNull(response);
    assertEquals(1, response.getSubmissionIds().size());
    assertNotNull(response.getSubmissionIds().getFirst());

    // Capture and verify saved BulkSubmission
    ArgumentCaptor<BulkSubmission> captor = ArgumentCaptor.forClass(BulkSubmission.class);
    verify(bulkSubmissionRepository).save(captor.capture());

    BulkSubmission captured = captor.getValue();

    assertThat(captured)
        .extracting(
            BulkSubmission::getCreatedByUserId, BulkSubmission::getData, BulkSubmission::getStatus)
        .containsExactly(userId, mockDetails, BulkSubmissionStatus.READY_FOR_PARSING);
  }

  @ParameterizedTest(name = "submissionPeriod: {0}")
  @NullSource
  @ValueSource(strings = {"", " "})
  @DisplayName("Throws BulkSubmissionValidationException when submission period is invalid")
  void throwsWhenSubmissionPeriodInvalid(String submissionPeriod) {
    MultipartFile file = new MockMultipartFile("filePath.csv", new byte[0]);
    String userId = "test-user-id";
    GetBulkSubmission200ResponseDetails mockDetails =
        mock(GetBulkSubmission200ResponseDetails.class);
    GetBulkSubmission200ResponseDetailsOffice mockOffice =
        mock(GetBulkSubmission200ResponseDetailsOffice.class);
    GetBulkSubmission200ResponseDetailsSchedule mockSchedule =
        mock(GetBulkSubmission200ResponseDetailsSchedule.class);
    when(mockDetails.getOffice()).thenReturn(mockOffice);
    when(mockDetails.getSchedule()).thenReturn(mockSchedule);
    when(mockSchedule.getSubmissionPeriod()).thenReturn(submissionPeriod);
    when(mockSchedule.getAreaOfLaw()).thenReturn("LEGAL HELP");
    when(mockOffice.getAccount()).thenReturn("TEST");

    doReturn(mockDetails).when(bulkSubmissionService).getBulkSubmissionDetails(file);

    BulkSubmissionValidationException exception =
        assertThrows(
            BulkSubmissionValidationException.class,
            () -> bulkSubmissionService.submitBulkSubmissionFile(userId, file, List.of("TEST")));

    assertEquals(
        "Submission period is required, please check the file and try again.",
        exception.getMessage());

    ArgumentCaptor<BulkSubmission> captor = ArgumentCaptor.forClass(BulkSubmission.class);
    verify(bulkSubmissionRepository).save(captor.capture());
    assertThat(captor.getValue())
        .extracting(BulkSubmission::getStatus, BulkSubmission::getErrorCode)
        .containsExactly(BulkSubmissionStatus.VALIDATION_FAILED, BulkSubmissionErrorCode.V100);
  }

  @Test
  @DisplayName("Throws BulkSubmissionAreaOfLawException when the area of law is unknown")
  void throwsWhenAreaOfLawUnknown() {
    MultipartFile file = new MockMultipartFile("filePath.csv", new byte[0]);
    GetBulkSubmission200ResponseDetails mockDetails =
        mock(GetBulkSubmission200ResponseDetails.class);
    GetBulkSubmission200ResponseDetailsSchedule mockSchedule =
        mock(GetBulkSubmission200ResponseDetailsSchedule.class);
    when(mockDetails.getSchedule()).thenReturn(mockSchedule);
    when(mockSchedule.getAreaOfLaw()).thenReturn("UNKNOWN");
    doReturn(mockDetails).when(bulkSubmissionService).getBulkSubmissionDetails(file);

    BulkSubmissionAreaOfLawException exception =
        assertThrows(
            BulkSubmissionAreaOfLawException.class,
            () -> bulkSubmissionService.submitBulkSubmissionFile("user", file, List.of("TEST")));

    assertEquals(
        "Area of Law must be one of: MEDIATION, CRIME LOWER, or LEGAL HELP",
        exception.getMessage());
  }

  @Test
  @DisplayName("Returns the bulk submission details from the multipart file")
  void returnsBulkSubmissionDetailsFromFile() {
    MultipartFile file = new MockMultipartFile("filePath.csv", new byte[0]);
    FileSubmission csvSubmission = mock(CsvSubmission.class);
    GetBulkSubmission200ResponseDetails expected = mock(GetBulkSubmission200ResponseDetails.class);
    when(bulkSubmissionFileService.convert(file)).thenReturn(csvSubmission);
    when(bulkSubmissionMapper.toBulkSubmissionDetails(csvSubmission)).thenReturn(expected);

    GetBulkSubmission200ResponseDetails actual =
        bulkSubmissionService.getBulkSubmissionDetails(file);

    assertEquals(expected, actual);
  }

  @Test
  @DisplayName("Returns the bulk submission")
  void returnsBulkSubmission() {
    var id = Uuid7.timeBasedUuid();
    var expectedDetails = ClaimsDataTestUtil.getBulkSubmission200ResponseDetails();
    var expectedBulkSubmission = new BulkSubmission();
    expectedBulkSubmission.setId(id);
    expectedBulkSubmission.setStatus(BulkSubmissionStatus.READY_FOR_PARSING);
    expectedBulkSubmission.setData(expectedDetails);

    var expectedResponse = new GetBulkSubmission200Response();
    expectedResponse.setBulkSubmissionId(id);
    expectedResponse.setStatus(BulkSubmissionStatus.READY_FOR_PARSING);
    expectedResponse.details(expectedDetails);

    when(bulkSubmissionRepository.findById(id)).thenReturn(Optional.of(expectedBulkSubmission));

    var bulkSubmissionResponse = bulkSubmissionService.getBulkSubmission(id);

    assertEquals(expectedResponse, bulkSubmissionResponse);
  }

  @Test
  @DisplayName("Throws BulkSubmissionNotFoundException when bulk submission not found")
  void shouldThrowWhenBulkSubmissionNotFound() {
    var id = Uuid7.timeBasedUuid();
    when(bulkSubmissionRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(
        BulkSubmissionNotFoundException.class, () -> bulkSubmissionService.getBulkSubmission(id));
  }

  @Test
  @DisplayName("Updates a BulkSubmission")
  void shouldUpdateBulkSubmission() {
    BulkSubmission entity = BulkSubmission.builder().id(BULK_SUBMISSION_ID).build();
    BulkSubmissionPatch patch =
        new BulkSubmissionPatch()
            .status(BulkSubmissionStatus.VALIDATION_FAILED)
            .errorCode(BulkSubmissionErrorCode.V100)
            .errorDescription("This is the error message")
            .updatedByUserId(API_USER_ID);

    when(bulkSubmissionRepository.findById(BULK_SUBMISSION_ID)).thenReturn(Optional.of(entity));

    bulkSubmissionService.updateBulkSubmission(BULK_SUBMISSION_ID, patch);

    verify(bulkSubmissionRepository).save(entity);
  }

  @Test
  @DisplayName("Throws BulkSubmissionNotFoundException when bulk submission not found")
  void shouldThrowWhenBulkSubmissionNotFoundOnUpdate() {
    BulkSubmissionPatch patch = new BulkSubmissionPatch();
    when(bulkSubmissionRepository.findById(BULK_SUBMISSION_ID)).thenReturn(Optional.empty());

    assertThrows(
        BulkSubmissionNotFoundException.class,
        () -> bulkSubmissionService.updateBulkSubmission(BULK_SUBMISSION_ID, patch));
  }
}
