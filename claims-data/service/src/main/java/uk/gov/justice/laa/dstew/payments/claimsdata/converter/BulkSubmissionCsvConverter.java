package uk.gov.justice.laa.dstew.payments.claimsdata.converter;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import io.micrometer.common.util.StringUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionFileReadException;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CategoryCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FileExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MediationType;
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
@RequiredArgsConstructor
public class BulkSubmissionCsvConverter implements BulkSubmissionConverter {
  private final ObjectMapper objectMapper;
  private final CsvMapper csvMapper;
  static final String MISSING_RECORD_TYPE_ERROR =
      "Some rows are missing a record type tag. Each row must start with a valid type (e.g., OUTCOME, MATTERSTARTS). Please correct and resubmit.";

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
    List<Map<String, String>> csvImmigrationClr = new ArrayList<>();

    try (InputStream fileReader = file.getInputStream()) {
      MappingIterator<List<String>> rowIterator =
          csvMapper
              .readerForListOf(String.class)
              .with(CsvParser.Feature.WRAP_AS_ARRAY)
              .readValues(fileReader);

      while (rowIterator.hasNextValue()) {
        CsvBulkSubmissionRow csvBulkSubmissionRow;
        List<String> row = rowIterator.nextValue();
        String rawHeader = row.getFirst().replaceAll("[^\\p{Print}]", "").trim();
        Map<String, String> values = getValues(row, rawHeader);
        // Check if this row is really empty (OK) or if only the record type is missing (Error).
        if (rawHeader.isEmpty()) {
          if (values.containsKey("matterType") || values.containsKey("FEE_CODE")) {
            throw new BulkSubmissionFileReadException(MISSING_RECORD_TYPE_ERROR);
          } else {
            continue;
          }
        }
        CsvHeader header = getHeader(rawHeader);
        csvBulkSubmissionRow = new CsvBulkSubmissionRow(header, values);

        switch (csvBulkSubmissionRow.header()) {
          case CsvHeader.OFFICE -> {
            if (csvOffice != null) {
              throw new BulkSubmissionFileReadException(
                  MAP_PROPERTY_TO_ERROR_MESSAGE.get("office"));
            }
            csvOffice = objectMapper.convertValue(csvBulkSubmissionRow.values(), CsvOffice.class);
          }
          case CsvHeader.SCHEDULE -> {
            if (csvSchedule != null) {
              throw new BulkSubmissionFileReadException(
                  MAP_PROPERTY_TO_ERROR_MESSAGE.get("schedule"));
            }
            csvSchedule =
                objectMapper.convertValue(csvBulkSubmissionRow.values(), CsvSchedule.class);
          }
          case CsvHeader.OUTCOME ->
              csvOutcomes.add(
                  objectMapper.convertValue(csvBulkSubmissionRow.values(), CsvOutcome.class));
          case CsvHeader.MATTERSTARTS ->
              csvMatterStarts.addAll(toMatterStartRows(csvBulkSubmissionRow.values()));
          case CsvHeader.IMMIGRATIONCLR -> csvImmigrationClr.add(Map.copyOf(values));

          default -> log.debug("Unsupported header '{}'", csvBulkSubmissionRow.header());
        }
      }
    } catch (IllegalArgumentException e) {
      throw new BulkSubmissionFileReadException("Failed to parse csv bulk submission file", e);
    } catch (IOException e) {
      throw new BulkSubmissionFileReadException("Failed to read csv bulk submission file", e);
    }

    if (csvOffice == null) {
      throw new BulkSubmissionFileReadException("Office missing from csv bulk submission file");
    }

    if (csvSchedule == null) {
      throw new BulkSubmissionFileReadException("Schedule missing from csv bulk submission file");
    }

    // parent submission object
    return new CsvSubmission(
        csvOffice, csvSchedule, csvOutcomes, csvMatterStarts, csvImmigrationClr);
  }

  private List<CsvMatterStarts> toMatterStartRows(Map<String, String> values) {
    Map<String, String> remainingValues = new LinkedHashMap<>(values);
    String scheduleRef = remainingValues.remove("SCHEDULE_REF");
    String procurementArea = remainingValues.remove("PROCUREMENT_AREA");
    String accessPoint = remainingValues.remove("ACCESS_POINT");
    String deliveryLocation = remainingValues.remove("DELIVERY_LOCATION");
    remainingValues.remove("COUNT");

    List<CsvMatterStarts> matterStarts = new ArrayList<>();
    for (Map.Entry<String, String> entry : remainingValues.entrySet()) {
      String categoryCodeOrMediationType = entry.getKey();
      String count = entry.getValue();
      if (isCategoryCode(categoryCodeOrMediationType)) {
        CategoryCode categoryCode = CategoryCode.valueOf(categoryCodeOrMediationType);
        matterStarts.add(
            new CsvMatterStarts(
                scheduleRef,
                procurementArea,
                accessPoint,
                categoryCode,
                deliveryLocation,
                null,
                count));
        continue;
      }

      MediationType mediationType =
          findMediationType(categoryCodeOrMediationType)
              .orElseThrow(
                  () ->
                      new BulkSubmissionFileReadException(
                          UNSUPPORTED_CATEGORY_CODE_MEDIATION_TYPE_ERROR.formatted(
                              categoryCodeOrMediationType)));

      matterStarts.add(
          new CsvMatterStarts(
              scheduleRef,
              procurementArea,
              accessPoint,
              null,
              deliveryLocation,
              mediationType,
              count));
    }
    if (matterStarts.isEmpty()) {
      throw new BulkSubmissionFileReadException(
          "Matter start row missing category code or mediation type");
    }
    return matterStarts;
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

  private Map<String, String> getValues(List<String> row, String header) {
    Map<String, String> values = new LinkedHashMap<>();
    row.subList(1, row.size())
        .forEach(
            rowValue -> {
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
                    "Unable to read entry for %s:'%s'".formatted(header, rowValue));
              }
            });
    return values;
  }

  private CsvHeader getHeader(String rawHeader) {
    try {
      return CsvHeader.valueOf(rawHeader);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new BulkSubmissionFileReadException(
          "Failed to parse bulk submission file, found invalid header: %s".formatted(rawHeader), e);
    }
  }

  private boolean isCategoryCode(String value) {
    try {
      CategoryCode.valueOf(value);
      return true;
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  private Optional<MediationType> findMediationType(String value) {
    return Arrays.stream(MediationType.values())
        .filter(type -> type.name().startsWith(value + "_"))
        .findFirst();
  }
}
