package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import uk.gov.justice.laa.export.core.CsvDataSource;
import uk.gov.justice.laa.export.core.CsvExportService;
import uk.gov.justice.laa.export.datasource.postgres.PostgresCopyCsvDataSource;
import uk.gov.justice.laa.export.datasource.postgres.PostgresWhereClauseBuilder;
import uk.gov.justice.laa.export.datasource.postgres.SqlFilterDefinition;
import uk.gov.justice.laa.export.datasource.restapi.RestApiCsvDataSource;

/** Configuration for CSV Export Service and Data Sources. */
@Configuration
public class CsvExportConfig {

  @Bean
  public CsvExportService csvExportService(List<CsvDataSource> sources) {
    return new CsvExportService(List.copyOf(sources));
  }

  @Bean
  public WebClient webClient() {
    return WebClient.builder().build();
  }

  /** Postgres CSV Data Source fetching claims from claims.vw_claim_export view. */
  @Bean
  public CsvDataSource claimsCsvSource(DataSource dataSource) {
    PostgresWhereClauseBuilder whereBuilder =
        new PostgresWhereClauseBuilder(
            Map.of("submissionId", new SqlFilterDefinition("\"Submission Id\"", String.class)));

    return new PostgresCopyCsvDataSource(
        "claims", dataSource, "SELECT * FROM claims.vw_claim_export", whereBuilder);
  }

  /** Example REST API CSV Data Source fetching posts from JSONPlaceholder. */
  @Bean
  public CsvDataSource postsCsvSource(WebClient webClient) {

    return new RestApiCsvDataSource(
        "posts",
        webClient,
        "https://jsonplaceholder.typicode.com/posts",
        List.of("userId", "id", "title", "body"),
        json ->
            List.of(
                json.get("userId").toString(),
                json.get("id").toString(),
                json.get("title").toString(),
                json.get("body").toString()));
  }
}
