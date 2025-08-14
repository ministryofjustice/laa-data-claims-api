package uk.gov.justice.laa.dstew.payments.claimsdata.validator;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionInvalidFileException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionValidationException;

/**
 * This class is responsible for validating bulk submission files uploaded as part of the submission
 * process.
 *
 * @author Jamie Briggs
 * @see MultipartFile
 * @see uk.gov.justice.laa.dstew.payments.claimsdata.exception.GlobalExceptionHandler
 */
@Component
public class BulkSubmissionFileValidator {
  /**
   * Validates the provided file against specific criteria, including emptiness, file extension, and
   * MIME type compliance.
   *
   * <p>The validation rules include:
   *
   * <ul>
   *   <li>Checking if the file is empty.
   *   <li>Ensuring the file has a valid extension (.csv or .xml).
   *   <li>Verifying that the MIME type matches the file extension.
   * </ul>
   *
   * @param target The object to be validated, expected to be of type {@link MultipartFile}. It
   *     represents the uploaded file which will undergo validation.
   * @throws IllegalArgumentException If the provided file is empty, has an unsupported file
   *     extension, or if the MIME type is not consistent with the file extension.
   */
  public void validate(Object target) {
    MultipartFile file = (MultipartFile) target;

    // Step 1: Check if file is null or empty
    if (file.isEmpty()) {
      // Causes a 400 Bad Request response to be returned to the client.
      throw new BulkSubmissionValidationException("The uploaded file is empty");
    }

    // Step 2: Validate file extension
    String originalFilename = file.getOriginalFilename();
    if (originalFilename == null
        || (!originalFilename.toLowerCase().endsWith(".csv")
            && !originalFilename.toLowerCase().endsWith(".xml")
            && !originalFilename.toLowerCase().endsWith(".txt"))) {
      // Causes a 415 Unsupported Media Type response to be returned to the client.
      throw new BulkSubmissionInvalidFileException("Only .csv and .xml files are allowed");
    }

    // Step 3: Validate MIME Type
    String contentType = file.getContentType();
    if (originalFilename.toLowerCase().endsWith(".csv") && !"text/csv".equals(contentType)) {
      // Causes a 415 Unsupported Media Type response to be returned to the client.
      throw new BulkSubmissionInvalidFileException(
          "Mime type does not match the .csv file extension");
    }
    if (originalFilename.toLowerCase().endsWith(".xml")
        && !("text/xml".equals(contentType) || "application/xml".equals(contentType))) {
      // Causes a 415 Unsupported Media Type response to be returned to the client.
      throw new BulkSubmissionInvalidFileException(
          "Mime type does not match the .xml file extension");
    }
  }
}
