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
import java.util.Set;
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

/** Converter responsible for converting bulk submissions in TXT/CSV format. */
@Slf4j
@Component
@RequiredArgsConstructor
public class BulkSubmissionCsvConverter implements BulkSubmissionConverter {
  private final ObjectMapper objectMapper;
  private final CsvMapper csvMapper;
  static final String MISSING_RECORD_TYPE_ERROR =
      "Some rows are missing a record type tag. Each row must start with a valid type (e.g., OUTCOME, MATTERSTARTS). Please correct and resubmit.";

  /**
   * Field keys whose values may legitimately contain printable Unicode (e.g. accented letters and
   * apostrophes in personal names such as {@code O’Brien} or {@code Siân}). Values for these keys
   * are cleaned leniently (only non-printable characters removed); every other field is restricted
   * to printable ASCII. Add new free-text/name keys here as they are introduced.
   */
  static final Set<String> NAME_FIELD_KEYS =
      Set.of("CLIENT_FORENAME", "CLIENT_SURNAME", "CLIENT2_FORENAME", "CLIENT2_SURNAME");

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
        String rawHeader = stripToAscii(row.getFirst()).trim();
        Map<String, String> values = getValues(row, rawHeader);
        // Check if this row is really empty (OK) or if only the record type is missing (Error).
        if (rawHeader.isEmpty()) {
          if (ALLOWED_MATTER_TYPE_KEYS.stream().anyMatch(values::containsKey)
              || values.containsKey("FEE_CODE")) {
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
      throw new BulkSubmissionFileReadException(
          "Failed to read bulk submission file: %s".formatted(extractReadableMessage(e)), e);
    } catch (IOException e) {
      throw new BulkSubmissionFileReadException("Failed to read bulk submission file", e);
    }

    if (csvOffice == null) {
      throw new BulkSubmissionFileReadException("Enter the office account row in the file");
    }

    if (csvSchedule == null) {
      throw new BulkSubmissionFileReadException("Enter the schedule row in the file");
    }

    // parent submission object
    return new CsvSubmission(
        csvOffice, csvSchedule, csvOutcomes, csvMatterStarts, csvImmigrationClr);
  }

  private static String extractReadableMessage(Exception e) {
    if (e.getMessage() == null) {
      return "Unknown error";
    }

    int index = e.getMessage().indexOf("(class");
    return index > 0 ? e.getMessage().substring(0, index) : e.getMessage();
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
      throw new BulkSubmissionFileReadException("Enter a matter start row in the file");
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
    row.subList(1, row.size()).forEach(rawCell -> addCell(values, header, rawCell));
    return values;
  }

  /**
   * Parses a single {@code KEY=VALUE} cell and adds it to {@code values}. Blank cells are skipped;
   * the value is cleaned according to its field key (see {@link #cleanFieldValue(String, String)}).
   *
   * @param values the map to populate
   * @param header the record type header, used only for logging context
   * @param rawCell the raw cell text straight from the parsed row
   */
  private void addCell(Map<String, String> values, String header, String rawCell) {
    // Strip only non-printable characters first so that blank detection and the '=' split work,
    // while preserving any printable Unicode that a name field may legitimately contain.
    String cell = stripNonPrintable(rawCell).trim();
    if (StringUtils.isBlank(cell)) {
      log.debug("Blank row value found for {} row. Skipping...", header);
      return;
    }
    String[] entry = cell.split("=", 2);
    if (entry.length != 2) {
      throw new BulkSubmissionFileReadException(
          "Unable to read entry for %s:'%s'".formatted(header, cell));
    }
    String key = entry[0];
    values.put(key, cleanFieldValue(key, entry[1]));
  }

  /**
   * Cleans a field value based on its key. Name fields ({@link #NAME_FIELD_KEYS}) keep printable
   * Unicode (only non-printable characters are removed); all other fields are restricted to
   * printable ASCII.
   *
   * @param key the field key
   * @param value the value already stripped of non-printable characters
   * @return the cleaned value
   */
  static String cleanFieldValue(String key, String value) {
    return NAME_FIELD_KEYS.contains(key) ? value : stripToAscii(value);
  }

  /**
   * Removes only non-printable characters (the Unicode "Other" category {@code \p{C}}: control,
   * format, surrogate, private-use and unassigned code points) such as a BOM, zero-width and
   * control bytes, while preserving all printable characters including Unicode letters.
   *
   * @param value the value to clean (may be {@code null})
   * @return the value with non-printable characters removed, or an empty string if {@code null}
   */
  static String stripNonPrintable(String value) {
    return value == null ? "" : value.replaceAll("\\p{C}", "");
  }

  /**
   * Removes every character outside printable ASCII ({@code 0x20}-{@code 0x7E}). Used for coded and
   * structured fields that must not contain non-ASCII content.
   *
   * @param value the value to clean (may be {@code null})
   * @return the value with non-ASCII characters removed, or an empty string if {@code null}
   */
  static String stripToAscii(String value) {
    return value == null ? "" : value.replaceAll("[^\\p{Print}]", "");
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
