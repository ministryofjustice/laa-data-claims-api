package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.BulkSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.BulkSubmissionMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionDetails;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CreateBulkSubmission201Response;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.BulkSubmissionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkSubmissionServiceTest {

    @Mock
    BulkSubmissionFileService bulkSubmissionFileService;

    @Mock
    BulkSubmissionRepository bulkSubmissionRepository;

    @Mock
    BulkSubmissionMapper bulkSubmissionMapper;

    @Spy
    @InjectMocks
    BulkSubmissionService bulkSubmissionService;

    @Test
    @DisplayName("Returns the bulk submission details")
    void saveBulkSubmission() {
        // Setup and mock
        MultipartFile file = new MockMultipartFile("filePath.csv", new byte[0]);
        String userId = "test-user-id";
        BulkSubmissionDetails mockDetails = mock(BulkSubmissionDetails.class);
        doReturn(mockDetails).when(bulkSubmissionService).getBulkSubmissionDetails(file);

        BulkSubmission savedSubmission = BulkSubmission.builder()
                .data(mockDetails)
                .status(BulkSubmissionStatus.READY_FOR_PARSING)
                .createdByUserId(userId)
                .build();
        when(bulkSubmissionRepository.save(ArgumentMatchers.any())).thenReturn(savedSubmission);

        // Test
        CreateBulkSubmission201Response response = bulkSubmissionService.submitBulkSubmissionFile(userId, file);

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
                        BulkSubmission::getCreatedByUserId,
                        BulkSubmission::getData,
                        BulkSubmission::getStatus
                ).containsExactly(
                        savedSubmission.getCreatedByUserId(),
                        savedSubmission.getData(),
                        savedSubmission.getStatus()
                );
    }

    @Test
    @DisplayName("Returns the bulk submission details")
    void returnsBulkSubmissionDetails() {
        MultipartFile file = new MockMultipartFile("filePath.csv", new byte[0]);
        FileSubmission csvSubmission = mock(CsvSubmission.class);
        BulkSubmissionDetails expected = mock(BulkSubmissionDetails.class);
        when(bulkSubmissionFileService.convert(file)).thenReturn(csvSubmission);
        when(bulkSubmissionMapper.toBulkSubmissionDetails(csvSubmission)).thenReturn(expected);

        BulkSubmissionDetails actual = bulkSubmissionService.getBulkSubmissionDetails(file);

        assertEquals(expected, actual);
    }
}