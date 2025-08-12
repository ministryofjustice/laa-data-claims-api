package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import org.junit.jupiter.api.DisplayName;
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
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileSubmission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvSubmission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkSubmissionFileServiceTest {

    @Mock
    BulkSubmissionConverterFactory bulkSubmissionConverterFactory;

    @InjectMocks
    BulkSubmissionFileService bulkSubmissionFileService;

    @Test
    @DisplayName("Returns the converted csv file into a FileSubmission object")
    void convertCsvToFileSubmission() {
        MultipartFile file = new MockMultipartFile("filePath.csv", new byte[0]);
        CsvSubmission expected = mock(CsvSubmission.class);
            BulkSubmissionCsvConverter bulkClaimCsvConverter = mock(BulkSubmissionCsvConverter.class);
            when(bulkSubmissionConverterFactory.converterFor(FileExtension.CSV))
                    .thenReturn(bulkClaimCsvConverter);
            when(bulkClaimCsvConverter.convert(any(MockMultipartFile.class)))
                    .thenReturn(expected);

        FileSubmission actual = bulkSubmissionFileService.convert(file);

        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("Returns the converted csv file with original filename into a FileSubmission object")
    void convertCsvToFileSubmissionWithOriginalFilename() {
        MultipartFile file = new MockMultipartFile("filePath", "filePath.csv", "text/csv", new byte[0]);
        CsvSubmission expected = mock(CsvSubmission.class);
        BulkSubmissionCsvConverter bulkClaimCsvConverter = mock(BulkSubmissionCsvConverter.class);
        when(bulkSubmissionConverterFactory.converterFor(FileExtension.CSV))
                .thenReturn(bulkClaimCsvConverter);
        when(bulkClaimCsvConverter.convert(any(MockMultipartFile.class)))
                .thenReturn(expected);

        FileSubmission actual = bulkSubmissionFileService.convert(file);

        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("Throws an exception for unsupported file extensions")
    void throwsExceptionForInvalidFileExtensions() {
        MultipartFile file = new MockMultipartFile("filePath.invalid", new byte[0]);

        assertThrows(
                BulkSubmissionFileReadException.class,
                () -> bulkSubmissionFileService.convert(file),
                "Expected BulkSubmissionFileReadException to be thrown");
    }
}