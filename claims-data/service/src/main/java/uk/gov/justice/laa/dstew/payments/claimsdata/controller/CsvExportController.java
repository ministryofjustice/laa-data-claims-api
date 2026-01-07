package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.export.core.CsvException;
import uk.gov.justice.laa.export.core.CsvExportRequest;
import uk.gov.justice.laa.export.core.CsvExportService;

/** Controller for CSV export endpoints. */
@RestController
public class CsvExportController {

  private final CsvExportService csvExportService;

  public CsvExportController(CsvExportService csvExportService) {
    this.csvExportService = csvExportService;
  }

  /**
   * Endpoint to export data as CSV.
   *
   * @param source the data source name
   * @param submissionId the submission ID filter
   * @param response the HTTP response
   * @throws IOException in case of {@link IOException }
   * @throws CsvException in case of Csv generation errors
   */
  @GetMapping(value = "/export", produces = "text/csv")
  public void export(
      @RequestParam String source,
      @RequestParam String submissionId,
      HttpServletResponse response)
      throws IOException, CsvException {

    response.setHeader(
        "Content-Disposition", "attachment; filename=" + source + "-" + submissionId + ".csv");
    response.setCharacterEncoding("UTF-8");

    Map<String, Object> filters = new HashMap<>();
    if (submissionId != null) {
      filters.put("submissionId", submissionId);
    }

    CsvExportRequest request = new CsvExportRequest(source, filters);

    csvExportService.export(request, response.getWriter());
  }
}
