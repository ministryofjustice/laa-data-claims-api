package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.payments.claimsdata.converter.BulkSubmissionConverterFactory;
import uk.gov.justice.laa.dstew.payments.claimsdata.converter.BulkSubmissionCsvConverter;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionFileReadException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.BulkSubmissionMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionDetails;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.BulkSubmissionRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkSubmissionServiceTest {

    @Mock
    BulkSubmissionConverterFactory bulkClaimConverterFactory;

    @Mock
    BulkSubmissionRepository bulkSubmissionRepository;

    @Mock
    BulkSubmissionMapper bulkClaimSubmissionMapper;

    @InjectMocks
    BulkSubmissionService bulkClaimService;

    @Nested
    @DisplayName("getBulkSubmissionDetails")
    class GetBulkSubmissionDetails {

        @Test
        @DisplayName("Returns the bulk submission details")
        void returnsBulkSubmissionDetails() {
            MultipartFile file = new MockMultipartFile("filePath.csv", new byte[0]);
            FileSubmission csvSubmission = mock(CsvSubmission.class);
            BulkSubmissionDetails expected = mock(BulkSubmissionDetails.class);
            BulkSubmissionCsvConverter bulkClaimCsvConverter = mock(BulkSubmissionCsvConverter.class);
            when(bulkClaimConverterFactory.converterFor(FileExtension.CSV))
                    .thenReturn(bulkClaimCsvConverter);
            when(bulkClaimCsvConverter.convert(any(MockMultipartFile.class)))
                    .thenReturn((CsvSubmission) csvSubmission);
            when(bulkClaimSubmissionMapper.toBulkSubmissionDetails(csvSubmission)).thenReturn(expected);
            BulkSubmissionDetails actual = bulkClaimService.getBulkSubmissionDetails(file);

            assertEquals(expected, actual);
        }

        @Test
        @DisplayName("Throws an exception for unsupported file extensions")
        void throwsExceptionForInvalidFileExtensions() {
            MultipartFile file = new MockMultipartFile("filePath.invalid", new byte[0]);
            assertThrows(
                    BulkSubmissionFileReadException.class,
                    () -> bulkClaimService.getBulkSubmissionDetails(file),
                    "Expected BulkSubmissionFileReadException to be thrown");
        }
    }
}