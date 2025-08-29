package uk.gov.justice.laa.dstew.payments.claimsdata.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileExtension;

@ExtendWith(MockitoExtension.class)
class BulkSubmissionConverterFactoryTest {

  @Mock private BulkSubmissionCsvConverter csvConverter;

  @Mock private BulkSubmissionXmlConverter xmlConverter;

  private List<BulkSubmissionConverter> converters;

  private BulkSubmissionConverterFactory bulkSubmissionConverterFactory;

  @BeforeEach
  void setUp() {
    converters = Arrays.asList(csvConverter, xmlConverter);

    bulkSubmissionConverterFactory = new BulkSubmissionConverterFactory(converters);
  }

  @Test
  void converterFor_whenCsvFileExtension_thenItUsesBulkSubmissionCsvConverter() {
    when(csvConverter.handles(FileExtension.CSV)).thenReturn(true);
    var concreteFactory = bulkSubmissionConverterFactory.converterFor(FileExtension.CSV);

    assertThat(concreteFactory).isInstanceOf(BulkSubmissionCsvConverter.class);
  }

  @Test
  void converterFor_whenTxtFileExtension_thenItUsesBulkSubmissionCsvConverter() {
    when(csvConverter.handles(FileExtension.TXT)).thenReturn(true);
    var concreteFactory = bulkSubmissionConverterFactory.converterFor(FileExtension.TXT);

    assertThat(concreteFactory).isInstanceOf(BulkSubmissionCsvConverter.class);
  }

  @Test
  void converterFor_whenXmlFileExtension_thenItUsesBulkSubmissionXmlConverter() {
    when(xmlConverter.handles(FileExtension.XML)).thenReturn(true);
    var concreteFactory = bulkSubmissionConverterFactory.converterFor(FileExtension.XML);

    assertThat(concreteFactory).isInstanceOf(BulkSubmissionXmlConverter.class);
  }

  @ParameterizedTest
  @EnumSource(FileExtension.class)
  void converterFor_throwsException_whenFileExtensionIsInvalid(FileExtension extension) {
    when(csvConverter.handles(extension)).thenReturn(false);
    when(xmlConverter.handles(extension)).thenReturn(false);

    assertThrows(
        RuntimeException.class,
        () -> bulkSubmissionConverterFactory.converterFor(extension),
        "No converter found for file extension: " + extension);
  }
}
