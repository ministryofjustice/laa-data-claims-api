package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants;

/**
 * Java port of {@code tests/utils/scripts/dataGenerator/generateCivilFiles.ts} + {@code
 * generateCivilFilesWithOverrides.ts} from the {@code bulk-submission-and-fee-scheme-tests-}
 * project. Generates Legal Help bulk-submission files (CSV/TXT/XML) for BDD scenarios that need
 * fresh, uniquely-keyed payloads (e.g. duplicate-detection scenarios).
 *
 * <p>This is intentionally a self-contained, no-DB, no-Faker generator: the BDD harness only needs
 * syntactically-valid files whose office, period and outcome fields can be overridden per scenario.
 * The complex provider-contract / FSP-fee lookups from the TS originals are not relevant to the
 * in-process API tests.
 */
public final class LegalHelpFileGenerator {

  /** Output format selector. */
  public enum Format {
    CSV("csv"),
    TXT("txt"),
    XML("xml");

    private final String extension;

    Format(String extension) {
      this.extension = extension;
    }

    public String extension() {
      return extension;
    }

    public static Format fromString(String value) {
      String trimmed = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
      return switch (trimmed) {
        case "csv" -> CSV;
        case "txt" -> TXT;
        case "xml" -> XML;
        default ->
            throw new IllegalArgumentException(
                "Unsupported format: " + value + " (expected csv/txt/xml)");
      };
    }
  }

  /**
   * Optional per-OUTCOME overrides; mirrors the {@code claimOptions} interface from the TS
   * generator. Fields left {@code null} fall back to randomly-generated values.
   */
  public record ClaimOverride(
      String ucn,
      String ufn,
      String feeCode,
      String office,
      String caseStartDate,
      String workConcludedDate,
      String clientDateOfBirth) {
    public static ClaimOverride empty() {
      return new ClaimOverride(null, null, null, null, null, null, null);
    }

    public static ClaimOverride fromRow(Map<String, String> row) {
      return new ClaimOverride(
          trimToNull(row.get("ucn")),
          trimToNull(row.get("ufn")),
          trimToNull(row.get("feeCode")),
          trimToNull(row.get("office")),
          trimToNull(row.get("caseStartDate")),
          trimToNull(row.get("workConcludedDate")),
          trimToNull(row.get("clientDateOfBirth")));
    }

    private static String trimToNull(String value) {
      if (value == null) {
        return null;
      }
      String trimmed = value.trim();
      return trimmed.isEmpty() ? null : trimmed;
    }

    static String trimOrNull(String value) {
      return trimToNull(value);
    }
  }

  /** Result of a generation call. */
  public record GeneratedFile(Path path, String office, String submissionPeriod) {}

  private static final DateTimeFormatter DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final Path outputDir;

  public LegalHelpFileGenerator(Path outputDir) {
    this.outputDir = outputDir;
  }

  public LegalHelpFileGenerator() {
    this(defaultOutputDir());
  }

  private static Path defaultOutputDir() {
    return Paths.get(System.getProperty("java.io.tmpdir"), "laa-bdd-generated-legal");
  }

  /**
   * Generates a single Legal Help bulk-submission file.
   *
   * @param format the output format
   * @param outcomes number of OUTCOME lines to emit
   * @param defaultOffice fallback office account if no overrides provide one
   * @param submissionPeriod the {@code MMM-uuuu} schedule period
   * @param overrides per-outcome overrides (null/empty list means random fill for every outcome)
   * @return the generated file path + the resolved office + period
   */
  public GeneratedFile generate(
      Format format,
      int outcomes,
      String defaultOffice,
      String submissionPeriod,
      List<ClaimOverride> overrides)
      throws IOException {

    if (outcomes <= 0) {
      throw new IllegalArgumentException("outcomes must be > 0");
    }

    String office = resolveOffice(overrides, defaultOffice);
    String scenarioSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    String baseName = "legal_" + System.currentTimeMillis() + "_" + scenarioSuffix;
    Files.createDirectories(outputDir);

    StringBuilder body = new StringBuilder();
    body.append("OFFICE,account=").append(office).append('\n');
    body.append("SCHEDULE,submissionPeriod=")
        .append(submissionPeriod)
        .append(",areaOfLaw=LEGAL HELP,scheduleNum=")
        .append(office)
        .append("/CIVIL\n");

    for (int i = 0; i < outcomes; i++) {
      ClaimOverride override =
          (overrides != null && i < overrides.size()) ? overrides.get(i) : ClaimOverride.empty();
      body.append(buildOutcomeLine(office, i + 1, override)).append('\n');
    }

    Path csvLike =
        outputDir.resolve(baseName + "." + (format == Format.XML ? "csv" : format.extension()));
    Files.writeString(csvLike, body.toString(), StandardCharsets.UTF_8);

    Path finalPath;
    if (format == Format.XML) {
      Path xml = outputDir.resolve(baseName + ".xml");
      LegalHelpCsvToXmlConverter.convert(csvLike, xml);
      Files.deleteIfExists(csvLike);
      finalPath = xml;
    } else {
      finalPath = csvLike;
    }

    return new GeneratedFile(finalPath, office, submissionPeriod);
  }

  private static String resolveOffice(List<ClaimOverride> overrides, String defaultOffice) {
    if (overrides != null) {
      for (ClaimOverride o : overrides) {
        if (o != null && o.office() != null) {
          return o.office();
        }
      }
    }
    return defaultOffice;
  }

  private static String buildOutcomeLine(String office, int caseNum, ClaimOverride override) {
    String feeCode =
        override.feeCode() != null
            ? override.feeCode()
            : randomChoice(BddTestConstants.DEFAULT_FEE_CODES);

    LocalDate caseStart =
        override.caseStartDate() != null
            ? LocalDate.parse(override.caseStartDate(), DDMMYYYY)
            : randomDateBetween(LocalDate.of(2015, 1, 1), LocalDate.now().minusDays(30));
    LocalDate concluded =
        override.workConcludedDate() != null
            ? LocalDate.parse(override.workConcludedDate(), DDMMYYYY)
            : caseStart.plusDays(ThreadLocalRandom.current().nextInt(1, 60));
    LocalDate dob =
        override.clientDateOfBirth() != null
            ? LocalDate.parse(override.clientDateOfBirth(), DDMMYYYY)
            : randomDateBetween(LocalDate.of(1960, 1, 1), LocalDate.of(2000, 12, 31));

    String forename = "Forename" + caseNum;
    String surname = "Surname" + caseNum;
    String ucn =
        override.ucn() != null
            ? override.ucn()
            : String.format(
                "%s/%s/%s",
                DDMMYYYY.format(dob).replace("/", ""),
                forename.charAt(0),
                clean(surname).substring(0, Math.min(3, surname.length())));
    String ufn =
        override.ufn() != null
            ? override.ufn()
            : String.format(
                "%s/%03d", DateTimeFormatter.ofPattern("ddMMyy").format(caseStart), caseNum);

    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("FEE_CODE", feeCode);
    fields.put("matterType", "FAMX:FAPP");
    fields.put("CASE_REF_NUMBER", forename.substring(0, 3) + "/" + clean(surname));
    fields.put("CASE_START_DATE", DDMMYYYY.format(caseStart));
    fields.put("CASE_ID", String.format("%03d", caseNum));
    fields.put("UFN", ufn);
    fields.put("PROCUREMENT_AREA", "PA00120");
    fields.put("ACCESS_POINT", "AP00000");
    fields.put("CLIENT_FORENAME", forename);
    fields.put("CLIENT_SURNAME", surname);
    fields.put("CLIENT_DATE_OF_BIRTH", DDMMYYYY.format(dob));
    fields.put("UCN", ucn.toUpperCase(Locale.ROOT));
    fields.put("GENDER", "M");
    fields.put("ETHNICITY", "12");
    fields.put("DISABILITY", "NCD");
    fields.put("CLIENT_POST_CODE", "SW1H 9EA");
    fields.put("WORK_CONCLUDED_DATE", DDMMYYYY.format(concluded));
    fields.put("CASE_STAGE_LEVEL", "FPC01");
    fields.put("ADVICE_TIME", "120");
    fields.put("TRAVEL_TIME", "0");
    fields.put("WAITING_TIME", "0");
    fields.put("PROFIT_COST", "100.00");
    fields.put("DISBURSEMENTS_AMOUNT", "0.00");
    fields.put("COUNSEL_COST", "0.00");
    fields.put("DISBURSEMENTS_VAT", "0.00");
    fields.put("TRAVEL_WAITING_COSTS", "0.00");
    fields.put("VAT_INDICATOR", "Y");
    fields.put("LONDON_NONLONDON_RATE", "N");
    fields.put("TRAVEL_COSTS", "0.00");
    fields.put("OUTCOME_CODE", "FX");
    fields.put("POSTAL_APPL_ACCP", "Y");
    fields.put("NATIONAL_REF_MECHANISM_ADVICE", "Y");
    fields.put("LEGACY_CASE", "N");
    fields.put("ADDITIONAL_TRAVEL_PAYMENT", "N");
    fields.put("ELIGIBLE_CLIENT_INDICATOR", "Y");
    fields.put("IRC_SURGERY", "N");
    fields.put("SUBSTANTIVE_HEARING", "N");
    fields.put("TOLERANCE_INDICATOR", "N");
    fields.put("SURGERY_DATE", DDMMYYYY.format(concluded));
    fields.put("REP_ORDER_DATE", DDMMYYYY.format(concluded));
    fields.put("TRANSFER_DATE", DDMMYYYY.format(concluded));
    fields.put("SCHEDULE_REF", office + "/" + LocalDate.now().getYear() + "/" + caseNum);

    StringBuilder line = new StringBuilder("OUTCOME");
    for (Map.Entry<String, String> entry : fields.entrySet()) {
      line.append(',').append(entry.getKey()).append('=').append(entry.getValue());
    }
    return line.toString();
  }

  private static String clean(String value) {
    return value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
  }

  private static <T> T randomChoice(List<T> list) {
    return list.get(ThreadLocalRandom.current().nextInt(list.size()));
  }

  private static LocalDate randomDateBetween(LocalDate startInclusive, LocalDate endInclusive) {
    long start = startInclusive.toEpochDay();
    long end = endInclusive.toEpochDay();
    if (end <= start) {
      return startInclusive;
    }
    long randomDay = ThreadLocalRandom.current().nextLong(start, end + 1);
    return LocalDate.ofEpochDay(randomDay);
  }

  /**
   * Picks a default submission period — a randomly-chosen historical {@code MMM-yyyy} between
   * {@code 2018-01} and 24 months ago. Deterministic per scenario isn't required; we just need a
   * period the API will accept.
   */
  public static String defaultSubmissionPeriod() {
    LocalDate today = LocalDate.now();
    LocalDate latest = today.minusMonths(24).withDayOfMonth(1);
    LocalDate earliest = LocalDate.of(BddTestConstants.EARLIEST_SUBMISSION_YEAR, 1, 1);
    long monthsBetween = java.time.temporal.ChronoUnit.MONTHS.between(earliest, latest);
    long randomOffset = ThreadLocalRandom.current().nextLong(0, Math.max(1, monthsBetween + 1));
    LocalDate picked = earliest.plusMonths(randomOffset);
    return BddTestConstants.MONTHS.get(picked.getMonthValue() - 1) + "-" + picked.getYear();
  }

  /**
   * Helper for tests that pass a list of {@code Map<String,String>} rows from a Gherkin DataTable.
   */
  public static List<ClaimOverride> overridesFromRows(List<Map<String, String>> rows) {
    if (rows == null || rows.isEmpty()) {
      return List.of();
    }
    List<ClaimOverride> result = new ArrayList<>(rows.size());
    for (Map<String, String> row : rows) {
      result.add(ClaimOverride.fromRow(row));
    }
    return result;
  }

  // ---------------------------------------------------------------------------
  // Paired-file generation (used by disbursement duplicate-check scenarios)
  // ---------------------------------------------------------------------------

  /** Paired-file result: two generated files with the same claim data but different periods. */
  public record GeneratedPair(GeneratedFile first, GeneratedFile second) {}

  /**
   * Generates two Legal Help files for the given office whose submission periods are {@code
   * monthsApart} months apart. Both files carry the same claim data (from {@code rows}); the caller
   * decides via {@code feeCode1}/{@code feeCode2} table columns whether the fee codes differ.
   *
   * <p>Each row is expected to expose (at least) {@code ucn}, {@code ufn}, {@code feeCode1}, {@code
   * feeCode2}. Extra columns like {@code office} are ignored — the office comes from the method
   * argument.
   */
  public GeneratedPair generatePair(
      Format format,
      String office,
      int monthsApart,
      String firstPeriod,
      String secondPeriod,
      List<Map<String, String>> rows)
      throws IOException {

    List<ClaimOverride> firstOverrides = new ArrayList<>();
    List<ClaimOverride> secondOverrides = new ArrayList<>();
    for (Map<String, String> row : rows) {
      firstOverrides.add(
          overrideFor(row, row.getOrDefault("feeCode1", row.get("feeCode")), office));
      secondOverrides.add(
          overrideFor(row, row.getOrDefault("feeCode2", row.get("feeCode")), office));
    }

    GeneratedFile first =
        generate(format, Math.max(rows.size(), 1), office, firstPeriod, firstOverrides);
    GeneratedFile second =
        generate(format, Math.max(rows.size(), 1), office, secondPeriod, secondOverrides);
    return new GeneratedPair(first, second);
  }

  private static ClaimOverride overrideFor(Map<String, String> row, String feeCode, String office) {
    return new ClaimOverride(
        ClaimOverride.trimOrNull(row.get("ucn")),
        ClaimOverride.trimOrNull(row.get("ufn")),
        ClaimOverride.trimOrNull(feeCode),
        office,
        ClaimOverride.trimOrNull(row.get("caseStartDate")),
        ClaimOverride.trimOrNull(row.get("workConcludedDate")),
        ClaimOverride.trimOrNull(row.get("clientDateOfBirth")));
  }
}
