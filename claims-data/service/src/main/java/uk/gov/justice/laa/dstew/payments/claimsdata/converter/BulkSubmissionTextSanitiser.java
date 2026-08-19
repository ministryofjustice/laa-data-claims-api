package uk.gov.justice.laa.dstew.payments.claimsdata.converter;

import java.util.regex.Pattern;

/**
 * Shared hygiene cleaning for free-text field values read from bulk submission files (CSV/TXT and
 * XML).
 *
 * <p>Only genuinely invisible/non-printable characters are removed while all printable Unicode text
 * - accented letters ({@code Siân}), curly apostrophes ({@code O’Brien}, {@code U+2019}), emoji,
 * etc. - is preserved. This deliberately replaces the previous {@code [^\p{Print}]} expression
 * which (being ASCII-only in Java) silently deleted every character outside {@code U+0020..U+007E}
 * and so corrupted valid names before validation could run.
 *
 * <p>Allow-listing which characters are acceptable per field is the responsibility of validation
 * (the single source of truth), which reports a violation rather than silently altering the value.
 */
final class BulkSubmissionTextSanitiser {

  /**
   * Matches the Unicode "Other" category {@code \p{C}}: control ({@code Cc}), format ({@code Cf} -
   * includes soft hyphen {@code U+00AD}, BOM {@code U+FEFF} and zero-width characters), surrogate
   * ({@code Cs}), private-use ({@code Co}) and unassigned ({@code Cn}) code points.
   */
  private static final Pattern INVISIBLE_CHARACTERS = Pattern.compile("\\p{C}");

  private BulkSubmissionTextSanitiser() {}

  /**
   * Removes invisible/non-printable characters from a value while preserving printable Unicode
   * text.
   *
   * <p>Non-breaking spaces ({@code U+00A0}) are normalised to a regular space rather than deleted,
   * so multi-word values (names, postcodes) keep their word break. Soft hyphen ({@code U+00AD}) is
   * a format character and is therefore removed by the {@code \p{C}} strip.
   *
   * @param value the raw cell/field value, may be {@code null}
   * @return the sanitised value, or {@code null} if the input was {@code null}
   */
  static String sanitise(String value) {
    if (value == null) {
      return null;
    }
    String withNormalisedSpaces = value.replace('\u00A0', ' ');
    return INVISIBLE_CHARACTERS.matcher(withNormalisedSpaces).replaceAll("");
  }
}
