package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Java port of {@code generateTwoLegalHelpDuplicateFiles.ts} and {@code
 * generateTwoLegalHelpAcceptedFiles.ts} from the {@code bulk-submission-and-fee-scheme-tests-}
 * project. Produces two Legal Help files spread {@code monthsDiff} months apart with case-start
 * and work-concluded dates positioned either side of the duplicate-detection cutoff date.
 *
 * <p>The cut-off rule replicated here matches the original TypeScript helper:
 *
 * <ul>
 *   <li>{@code anchorPeriod} = later of the two submission periods.
 *   <li>{@code cutoffPeriod} = anchorPeriod − 3 months.
 *   <li>{@code cutoffDate} = the 20th of the month <em>after</em> {@code cutoffPeriod}.
 *   <li>For <em>duplicate-reject</em> files both work-concluded dates fall <strong>after</strong>
 *       the cut-off (file1 = cutoff + 1 day, file2 = cutoff + 2 days).
 *   <li>For <em>accept</em> files the first work-concluded date falls <strong>on</strong> the
 *       cut-off (file2 = cutoff + 1 day).
 * </ul>
 *
 * The output files are otherwise valid Legal Help files built via {@link LegalHelpFileGenerator}.
 */
public final class TwoLegalHelpFilesGenerator {

  private static final DateTimeFormatter DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final List<String> MONTHS =
      List.of(
          "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC");

  /**
   * Holds both generated file paths and the metadata describing how they were positioned around
   * the cut-off, for diagnostics / assertion attachments.
   */
  public record TwoFiles(
      Path firstFile,
      Path secondFile,
      String office,
      String firstSubmissionPeriod,
      String secondSubmissionPeriod,
      String cutoffDate,
      String firstWorkConcludedDate,
      String secondWorkConcludedDate) {}

  private final LegalHelpFileGenerator generator;

  public TwoLegalHelpFilesGenerator(LegalHelpFileGenerator generator) {
    this.generator = generator;
  }

  /**
   * Generates two Legal Help files configured for the duplicate-detection rule to <em>reject</em>
   * the second submission (both files are inside the cut-off window).
   */
  public TwoFiles generateDuplicateRejectPair(
      LegalHelpFileGenerator.Format format,
      String office,
      int monthsDiff,
      LegalHelpFileGenerator.ClaimOverride firstClaim,
      LegalHelpFileGenerator.ClaimOverride secondClaim)
      throws IOException {
    PeriodPair periods = periodsApart(monthsDiff);
    LocalDate cutoff = cutoffDate(periods.first(), periods.second());

    LocalDate firstCcd = cutoff.plusDays(1);
    LocalDate secondCcd = cutoff.plusDays(2);

    return buildPair(format, office, periods, cutoff, firstCcd, secondCcd, firstClaim, secondClaim);
  }

  /**
   * Generates two Legal Help files configured for the duplicate-detection rule to <em>accept</em>
   * the second submission because the first file's CCD falls on (or before) the cut-off.
   */
  public TwoFiles generateOutsideCutoffPair(
      LegalHelpFileGenerator.Format format,
      String office,
      LegalHelpFileGenerator.ClaimOverride firstClaim,
      LegalHelpFileGenerator.ClaimOverride secondClaim)
      throws IOException {

    // Two months apart is comfortably outside the 3-month window and matches the TS default.
    PeriodPair periods = periodsApart(2);
    LocalDate cutoff = cutoffDate(periods.first(), periods.second());

    LocalDate firstCcd = cutoff;
    LocalDate secondCcd = cutoff.plusDays(1);

    return buildPair(format, office, periods, cutoff, firstCcd, secondCcd, firstClaim, secondClaim);
  }

  private TwoFiles buildPair(
      LegalHelpFileGenerator.Format format,
      String office,
      PeriodPair periods,
      LocalDate cutoff,
      LocalDate firstCcd,
      LocalDate secondCcd,
      LegalHelpFileGenerator.ClaimOverride firstClaim,
      LegalHelpFileGenerator.ClaimOverride secondClaim)
      throws IOException {

    LegalHelpFileGenerator.ClaimOverride firstFinal =
        withDates(firstClaim, periods.first(), firstCcd);
    LegalHelpFileGenerator.ClaimOverride secondFinal =
        withDates(secondClaim, periods.second(), secondCcd);

    LegalHelpFileGenerator.GeneratedFile first =
        generator.generate(format, 1, office, periods.first(), List.of(firstFinal));
    LegalHelpFileGenerator.GeneratedFile second =
        generator.generate(format, 1, office, periods.second(), List.of(secondFinal));

    return new TwoFiles(
        first.path(),
        second.path(),
        office,
        periods.first(),
        periods.second(),
        DDMMYYYY.format(cutoff),
        DDMMYYYY.format(firstCcd),
        DDMMYYYY.format(secondCcd));
  }

  /**
   * Picks two recent submission periods that are exactly {@code monthsDiff} months apart, with the
   * later one being last month and the earlier one being last month minus {@code monthsDiff}.
   */
  private static PeriodPair periodsApart(int monthsDiff) {
    YearMonth later = YearMonth.from(LocalDate.now().minusMonths(1));
    YearMonth earlier = later.minusMonths(Math.max(monthsDiff, 0));
    return new PeriodPair(formatPeriod(earlier), formatPeriod(later));
  }

  private static String formatPeriod(YearMonth ym) {
    return MONTHS.get(ym.getMonthValue() - 1) + "-" + ym.getYear();
  }

  private static LocalDate cutoffDate(String period1, String period2) {
    YearMonth anchor = laterOf(parsePeriod(period1), parsePeriod(period2));
    YearMonth cutoffMonth = anchor.minusMonths(3);
    YearMonth followingMonth = cutoffMonth.plusMonths(1);
    return followingMonth.atDay(20);
  }

  private static YearMonth laterOf(YearMonth a, YearMonth b) {
    return a.isAfter(b) ? a : b;
  }

  private static YearMonth parsePeriod(String period) {
    String[] parts = period.split("-");
    int monthIdx = MONTHS.indexOf(parts[0].toUpperCase(Locale.ROOT));
    if (monthIdx < 0) {
      throw new IllegalArgumentException("Unknown month in period: " + period);
    }
    return YearMonth.of(Integer.parseInt(parts[1]), monthIdx + 1);
  }

  /**
   * Returns a copy of {@code base} with the case-start date set to (period − 3 months) and the
   * work-concluded date set to {@code workConcluded}. All other fields are preserved.
   */
  private static LegalHelpFileGenerator.ClaimOverride withDates(
      LegalHelpFileGenerator.ClaimOverride base, String period, LocalDate workConcluded) {
    LegalHelpFileGenerator.ClaimOverride safe =
        base == null ? LegalHelpFileGenerator.ClaimOverride.empty() : base;
    LocalDate caseStart = parsePeriod(period).minusMonths(3).atDay(1);
    return new LegalHelpFileGenerator.ClaimOverride(
        safe.ucn(),
        safe.ufn(),
        safe.feeCode(),
        safe.office(),
        DDMMYYYY.format(caseStart),
        DDMMYYYY.format(workConcluded),
        safe.clientDateOfBirth());
  }

  private record PeriodPair(String first, String second) {}
}

