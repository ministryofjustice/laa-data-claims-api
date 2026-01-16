package uk.gov.justice.laa.dstew.payments.claimsdata.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionFileReadException;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.XmlSubmission;

/** Converter responsible for converting bulk submissions in XML format. */
@Slf4j
@Component
@RequiredArgsConstructor
public class BulkSubmissionXmlConverter implements BulkSubmissionConverter {

  public static final String FILE_REJECTION_MESSAGE =
      "File rejected: unsupported content found. Please correct the file and try again.";

  private final XmlMapper xmlMapper;

  /**
   * Converts the given file to a {@link XmlSubmission} object.
   *
   * @param file the input file
   * @return the {@link XmlSubmission} object.
   */
  @Override
  public XmlSubmission convert(MultipartFile file) {

    try {
      return xmlMapper.readValue(file.getInputStream(), XmlSubmission.class);
    } catch (MismatchedInputException mismatchedInputException) {
      log.error(
          "Unsupported XML tag: {}",
          mismatchedInputException.getOriginalMessage(),
          mismatchedInputException);
      throw new BulkSubmissionFileReadException(missingFieldMessage(mismatchedInputException));

    } catch (InvalidDefinitionException invalidDefinitionException) {
      log.error(
          "Invalid XML mapping definition: {}",
          invalidDefinitionException.getOriginalMessage(),
          invalidDefinitionException);

      throw new BulkSubmissionFileReadException(
          resolveInvalidDefinitionMessage(invalidDefinitionException));

    } catch (JsonProcessingException jsonProcessingException) {
      log.error(
          "Failed to read/parse XML file: {}",
          jsonProcessingException.getMessage(),
          jsonProcessingException);
      if (isMalformedXml(jsonProcessingException)) {
        throw new BulkSubmissionFileReadException(
            "Malformed XML / file is corrupt (not well-formed). Please fix XML structure and re-submit.");
      }
      throw new BulkSubmissionFileReadException(
          containsStackTraceElements(jsonProcessingException.getOriginalMessage())
              ? FILE_REJECTION_MESSAGE
              : jsonProcessingException.getOriginalMessage());
    } catch (IOException ioException) {
      log.error("Failed to read/parse XML file: {}", ioException.getMessage(), ioException);
      throw new BulkSubmissionFileReadException(
          "Failed to read/parse XML file: {}" + file.getName());
    }
  }

  /**
   * Determines whether this converter handles the given {@link FileExtension}.
   *
   * @param fileExtension the file extension to check
   * @return true if the converter can handle files with the given extension, false otherwise
   */
  @Override
  public boolean handles(FileExtension fileExtension) {
    return FileExtension.XML.equals(fileExtension);
  }

  /**
   * Checks if the provided {@link JsonProcessingException} is caused by a malformed XML structure.
   *
   * @param e the {@link JsonProcessingException} to inspect for XML malformation
   * @return true if the XML is identified as malformed, false otherwise
   */
  private boolean isMalformedXml(JsonProcessingException e) {
    String msg = e.getOriginalMessage();
    return msg != null && msg.startsWith("Unexpected close tag");
  }

  /**
   * Generates an error message indicating which required field is missing from an XML bulk
   * submission file.
   *
   * @param e the {@link MismatchedInputException} containing details about the missing field(s)
   * @return a formatted error message for the first missing field, or a generic error message if no
   *     field details are available
   */
  private String missingFieldMessage(MismatchedInputException e) {
    return getInvalidProperties(e).stream()
        .findFirst()
        .map("%s missing from xml bulk submission file."::formatted)
        .orElse("Required data missing from xml bulk submission file.");
  }

  /**
   * Resolves an error message for an invalid definition by mapping invalid properties to predefined
   * error messages or falling back to the original exception message.
   *
   * @param invalidDefinitionException the exception containing information about the invalid
   *     definition
   * @return a resolved error message as a combined string of all detected issues
   */
  private String resolveInvalidDefinitionMessage(
      InvalidDefinitionException invalidDefinitionException) {
    return getInvalidProperties(invalidDefinitionException).stream()
        .map(
            property ->
                MAP_PROPERTY_TO_ERROR_MESSAGE.getOrDefault(
                    property, invalidDefinitionException.getOriginalMessage()))
        .collect(Collectors.joining(System.lineSeparator()));
  }

  /**
   * Extracts the invalid properties from the given JsonMappingException.
   *
   * @param e the JsonMappingException to extract invalid properties from
   * @return a set of invalid properties
   */
  private Set<String> getInvalidProperties(JsonMappingException e) {
    return e.getPath().stream()
        .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : ("[" + ref.getIndex() + "]"))
        .collect(Collectors.toSet());
  }

  /**
   * Checks if the given text contains stack trace elements by looking for opening brackets or
   * braces.
   *
   * @param text the text to check for stack trace elements
   * @return true if the text contains stack trace elements (brackets or braces), false otherwise
   */
  private boolean containsStackTraceElements(String text) {
    return text != null && (text.contains("[") || text.contains("{"));
  }
}
