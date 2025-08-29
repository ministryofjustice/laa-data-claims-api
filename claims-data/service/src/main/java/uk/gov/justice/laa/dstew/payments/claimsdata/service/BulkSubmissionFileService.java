package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.payments.claimsdata.converter.BulkSubmissionConverterFactory;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionFileReadException;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileSubmission;

/** Service responsible for handling processing of multipart files for bulk submission. */
@Service
@Slf4j
@RequiredArgsConstructor
public class BulkSubmissionFileService {

  private final BulkSubmissionConverterFactory bulkSubmissionConverterFactory;

  FileSubmission convert(MultipartFile file) {
    FileExtension fileExtension = getFileExtension(file);

    return bulkSubmissionConverterFactory.converterFor(fileExtension).convert(file);
  }

  private FileExtension getFileExtension(MultipartFile file) {
    String filename =
        !StringUtils.hasText(file.getOriginalFilename())
            ? file.getName()
            : file.getOriginalFilename();
    try {
      int index = filename.lastIndexOf('.');
      return FileExtension.valueOf(filename.substring(index + 1).toUpperCase());
    } catch (NullPointerException | IllegalArgumentException e) {
      throw new BulkSubmissionFileReadException(
          "Unable to retrieve file extension from filename: %s".formatted(filename), e);
    }
  }
}
