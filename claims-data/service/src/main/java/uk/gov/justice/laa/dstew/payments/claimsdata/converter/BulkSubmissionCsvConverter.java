package uk.gov.justice.laa.dstew.payments.claimsdata.converter;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import io.micrometer.common.util.StringUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionFileReadException;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvBulkSubmissionRow;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvHeader;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvMatterStarts;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvOffice;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvOutcome;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvSchedule;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.csv.CsvSubmission;

/** Converter responsible for converting bulk submissions in CSV format. */
@Slf4j
@Component
public class BulkSubmissionCsvConverter implements BulkSubmissionConverter {
  private final ObjectMapper objectMapper;
  private final CsvMapper csvMapper;

  @Autowired
  public BulkSubmissionCsvConverter(ObjectMapper objectMapper, CsvMapper csvMapper) {
    this.objectMapper = objectMapper;
    this.csvMapper = csvMapper;
  }

  /**
   * Converts the given file to a {@link CsvSubmission} object.
   *
   * @param file the input file
   * @return the {@link CsvSubmission} object.
   */
  @Override
  public CsvSubmission convert(MultipartFile file) {
    CsvOffice csvOffice = null;
    CsvSchedule csvSchedule = null;
    List<CsvOutcome> csvOutcomes = new ArrayList<>();
    List<CsvMatterStarts> csvMatterStarts = new ArrayList<>();

    try (InputStream fileReader = file.getInputStream()) {
      MappingIterator<List<String>> rowIterator = csvMapper.readerForListOf(String.class)
                                                      .with(CsvParser.Feature.WRAP_AS_ARRAY)
                                                      .readValues(fileReader);

      while (rowIterator.hasNextValue()) {
        CsvBulkSubmissionRow csvBulkSubmissionRow;
        List<String> row = rowIterator.nextValue();
        String rawHeader = row.getFirst().replaceAll("[^\\p{Print}]", "").trim();
        CsvHeader header = getHeader(rawHeader);
        Map<String, String> values = getValues(row, header);
        csvBulkSubmissionRow = new CsvBulkSubmissionRow(header, values);

        switch (csvBulkSubmissionRow.header()) {
          case CsvHeader.OFFICE -> {
            if (csvOffice != null) {
              throw new BulkSubmissionFileReadException(
                  "Multiple offices found in bulk claim file");
            }
            csvOffice = objectMapper.convertValue(csvBulkSubmissionRow.values(), CsvOffice.class);
          }
          case CsvHeader.SCHEDULE -> {
            if (csvSchedule != null) {
              throw new BulkSubmissionFileReadException(
                  "Multiple schedules found in bulk claim file");
            }
            csvSchedule =
                objectMapper.convertValue(csvBulkSubmissionRow.values(), CsvSchedule.class);
          }
          case CsvHeader.OUTCOME -> csvOutcomes.add(
                objectMapper.convertValue(csvBulkSubmissionRow.values(), CsvOutcome.class));
          case CsvHeader.MATTERSTARTS -> csvMatterStarts.add(
                objectMapper.convertValue(csvBulkSubmissionRow.values(), CsvMatterStarts.class));
          default -> log.debug("Unsupported header '{}'", csvBulkSubmissionRow.header());
        }
      }
    } catch (IOException e) {
      throw new BulkSubmissionFileReadException("Failed to read csv bulk claim file", e);
    }

    if (csvOffice == null) {
      throw new BulkSubmissionFileReadException("Office missing from csv bulk claim file");
    }

    if (csvSchedule == null) {
      throw new BulkSubmissionFileReadException("Schedule missing from csv bulk claim file");
    }

    // parent submission object

    return new CsvSubmission(csvOffice, csvSchedule, csvOutcomes, csvMatterStarts);
  }

  /**
   * Determines whether this converter handles the given {@link FileExtension}.
   *
   * @param fileExtension the file extension to check
   * @return true if the converter can handle files with the given extension, false otherwise
   */
  @Override
  public boolean handles(FileExtension fileExtension) {
    return FileExtension.CSV.equals(fileExtension) || FileExtension.TXT.equals(fileExtension);
  }

  private Map<String, String> getValues(List<String> row, CsvHeader header) {
    Map<String, String> values = new HashMap<>();
    row.subList(1, row.size()).forEach(rowValue -> {
      rowValue = rowValue.replaceAll("[^\\p{Print}]", "").trim();
      if (StringUtils.isBlank(rowValue)) {
        log.debug("Blank row value found for {} row. Skipping...", header);
        return;
      }
      String[] entry = rowValue.split("=", 2);
      if (entry.length == 2) {
        values.put(entry[0], entry[1]);
      } else {
        throw new BulkSubmissionFileReadException(
            "Unable to read entry for %s:'%s'".formatted(header.name(), rowValue));
      }
    });
    return values;
  }

  private CsvHeader getHeader(String rawHeader) {
    try {
      return CsvHeader.valueOf(rawHeader);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new BulkSubmissionFileReadException(
          "Failed to parse bulk claim file header: %s".formatted(rawHeader), e);
    }
  }
}
