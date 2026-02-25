package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;

public final class ExportTestUtil {

  private ExportTestUtil() {}

  public static void assertCsvHeadersMatchDefinition(String csv, String definitionFile)
      throws IOException {
    List<String> expectedHeaders = headersFromDefinition(definitionFile);
    List<String> actualHeaders = csvCells(firstNonEmptyLine(csv));
    assertThat(actualHeaders).containsExactlyInAnyOrderElementsOf(expectedHeaders);
  }

  public static Map<String, String> firstDataRowByHeader(String csv) {
    String[] lines = csv.split("\\R");
    String headerLine = Arrays.stream(lines).filter(line -> !line.isBlank()).findFirst().orElseThrow();
    String firstDataLine =
        Arrays.stream(lines)
            .filter(line -> !line.isBlank())
            .skip(1)
            .findFirst()
            .orElseThrow(
                () ->
                    new AssertionError(
                        "Expected CSV to contain at least one data row. CSV was:\n" + csv));

    List<String> headers = csvCells(headerLine);
    List<String> values = csvCells(firstDataLine);
    assertThat(values).hasSize(headers.size());

    Map<String, String> row = new LinkedHashMap<>();
    for (int i = 0; i < headers.size(); i++) {
      row.put(headers.get(i), values.get(i));
    }
    return row;
  }

  private static List<String> headersFromDefinition(String definitionFile) throws IOException {
    ClassPathResource resource = new ClassPathResource("export_definitions/" + definitionFile);
    try (var inputStream = resource.getInputStream()) {
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).lines()
          .map(String::trim)
          .filter(line -> line.startsWith("header:"))
          .map(line -> line.substring("header:".length()).trim())
          .map(ExportTestUtil::stripWrappingQuotes)
          .collect(Collectors.toList());
    }
  }

  private static List<String> csvCells(String line) {
    return Arrays.stream(line.split(",", -1))
        .map(ExportTestUtil::stripWrappingQuotes)
        .collect(Collectors.toList());
  }

  private static String firstNonEmptyLine(String value) {
    return value.lines().filter(line -> !line.isBlank()).findFirst().orElseThrow();
  }

  private static String stripWrappingQuotes(String value) {
    String trimmed = value.trim();
    if (trimmed.length() >= 2
        && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
            || (trimmed.startsWith("'") && trimmed.endsWith("'")))) {
      return trimmed.substring(1, trimmed.length() - 1);
    }
    return trimmed;
  }
}
