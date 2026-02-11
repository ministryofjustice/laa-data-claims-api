package uk.gov.justice.laa.dstew.payments.claimsdata.validator;

import java.util.*;
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
 * @see uk.gov.justice.laa.dstew.payments.claimsdata.exception.DataClaimsExceptionHandler
 */
@Component
public class BulkSubmissionFileValidator {

  private static final Map<String, Set<String>> ALLOWED_BY_EXT =
      Map.of(
          ".csv", Set.of("text/csv", "application/vnd.ms-excel", "text/plain"),
          ".xml", Set.of("text/xml", "application/xml"),
          ".txt", Set.of("text/plain"));

  private static final Set<String> SUPPORTED_EXTENSIONS = ALLOWED_BY_EXT.keySet();

  /**
   * Validates the provided file against specific criteria, including emptiness, file extension, and
   * Content type compliance.
   *
   * <p>The validation rules include:
   *
   * <ul>
   *   <li>Checking the object is a multipart file.
   *   <li>Checking if the file is empty.
   *   <li>Ensuring the file has a valid extension (.csv, .xml or .txt).
   *   <li>Verifying that the Content type matches the file extension.
   * </ul>
   *
   * @param target The object to be validated, expected to be of type {@link MultipartFile}. It
   *     represents the uploaded file which will undergo validation.
   * @throws IllegalArgumentException If the provided file is empty, has an unsupported file
   *     extension, or if the Content type is not consistent with the file extension.
   */
  public void validate(Object target) {
    // Step 1: validate it is a multipart file
    if (!(target instanceof MultipartFile file)) {
      // Causes a 400 Bad Request response to be returned to the client.
      throw new BulkSubmissionValidationException("The upload is not a MultipartFile");
    }

    // Step 2: Check if file is null or empty
    if (file.isEmpty()) {
      // Causes a 400 Bad Request response to be returned to the client.
      throw new BulkSubmissionValidationException("The uploaded file is empty");
    }

    // Step 3: Validate file extension
    String originalFilename = file.getOriginalFilename();
    final String fileName = originalFilename == null ? "" : originalFilename.toLowerCase().trim();
    final String extension = getExtensionIfFound(fileName);

    if (extension == null) {
      // 415 Unsupported Media Type
      throw new BulkSubmissionInvalidFileException("Only .csv, .xml and .txt files are allowed");
    }

    // Step 4: Validate Content Type
    final String contentTypeRaw = file.getContentType();
    final String contentType = contentTypeRaw == null ? "" : contentTypeRaw.toLowerCase().trim();
    final Set<String> allowed = ALLOWED_BY_EXT.get(extension);
    if (allowed.stream().noneMatch(a -> a.equalsIgnoreCase(contentType))) {
      // 415 Unsupported Media Type
      throw new BulkSubmissionInvalidFileException(
          "Content type '"
              + contentType
              + "' does not match the "
              + extension
              + " file extension.");
    }
  }

  private static String getExtensionIfFound(String fileName) {
    return SUPPORTED_EXTENSIONS.stream().filter(fileName::endsWith).findFirst().orElse(null);
  }
}
