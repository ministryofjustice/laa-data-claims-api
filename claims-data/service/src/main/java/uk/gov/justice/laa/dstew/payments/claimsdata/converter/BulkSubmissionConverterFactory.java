package uk.gov.justice.laa.dstew.payments.claimsdata.converter;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileExtension;

/**
 * Factory for providing a bulk submission converter that corresponds to the given file
 * extension.
 */
@Component
@RequiredArgsConstructor
public class BulkSubmissionConverterFactory {
  private final List<BulkSubmissionConverter> converters;

  /**
   * Provides a {@link BulkSubmissionConverter} for the given file extension.
   *
   * @param fileExtension the input file extension.
   * @return the {@link BulkSubmissionConverter} corresponding to the given file extension.
   */
  public BulkSubmissionConverter converterFor(FileExtension fileExtension) {
    return converters.stream()
        .filter(converter -> converter.handles(fileExtension))
        .findFirst()
        .orElseThrow(
            () -> new RuntimeException("No converter found for file extension: " + fileExtension));
  }
}
