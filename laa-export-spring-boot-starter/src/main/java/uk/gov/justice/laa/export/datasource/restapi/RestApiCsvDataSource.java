package uk.gov.justice.laa.export.datasource.restapi;

import java.io.IOException;
import uk.gov.justice.laa.export.core.CsvDataSource;
import uk.gov.justice.laa.export.core.CsvException;
import uk.gov.justice.laa.export.core.CsvExportRequest;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.Writer;
import java.util.List;
import java.util.Map;

public class RestApiCsvDataSource implements CsvDataSource {

  private final String name;
  private final WebClient webClient;
  private final String endpoint;
  private final List<String> headers;
  private final CsvRowMapper rowMapper;

  public RestApiCsvDataSource(
      String name,
      WebClient webClient,
      String endpoint,
      List<String> headers,
      CsvRowMapper rowMapper
  ) {
    this.name = name;
    this.webClient = webClient;
    this.endpoint = endpoint;
    this.headers = headers;
    this.rowMapper = rowMapper;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void writeCsv(
      CsvExportRequest request,
      Writer writer
  ) throws CsvException, IOException {

    CSVPrinter printer = new CSVPrinter(
        writer,
        CSVFormat.Builder.create(CSVFormat.DEFAULT)
            .setHeader(headers.toArray(String[]::new))
            .build()
    );

    List<Map<String, Object>> response =
        webClient.get()
            .uri(endpoint)
            .retrieve()
            .bodyToMono(List.class)
            .block();

    if (response == null) {
      return;
    }

    for (Map<String, Object> item : response) {
      printer.printRecord(rowMapper.mapRow(item));
    }

    printer.flush();
  }
}