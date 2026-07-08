package uk.gov.justice.laa.dstew.payments.claimsdata.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BulkSubmissionTextSanitiser")
class BulkSubmissionTextSanitiserTest {

  @Test
  @DisplayName("Returns null when the input is null")
  void returnsNullForNull() {
    assertThat(BulkSubmissionTextSanitiser.sanitise(null)).isNull();
  }

  @Test
  @DisplayName("Returns an empty string when the input is empty")
  void returnsEmptyForEmpty() {
    assertThat(BulkSubmissionTextSanitiser.sanitise("")).isEmpty();
  }

  @Test
  @DisplayName("Leaves plain printable ASCII unchanged")
  void preservesPlainAscii() {
    assertThat(BulkSubmissionTextSanitiser.sanitise("O'Brien 123")).isEqualTo("O'Brien 123");
  }

  @Test
  @DisplayName("Preserves accented letters and curly apostrophes")
  void preservesAccentedLettersAndCurlyApostrophes() {
    assertThat(BulkSubmissionTextSanitiser.sanitise("Si\u00e2n")).isEqualTo("Si\u00e2n"); // â
    assertThat(BulkSubmissionTextSanitiser.sanitise("O\u2019Brien")).isEqualTo("O\u2019Brien"); // ’
    assertThat(BulkSubmissionTextSanitiser.sanitise("\u0141ukasz")).isEqualTo("\u0141ukasz"); // Ł
  }

  @Test
  @DisplayName("Preserves supplementary characters such as emoji (valid surrogate pairs)")
  void preservesEmoji() {
    String emoji = new String(Character.toChars(0x1F600)); // grinning face, U+1F600
    assertThat(BulkSubmissionTextSanitiser.sanitise("Hi " + emoji)).isEqualTo("Hi " + emoji);
  }

  @Test
  @DisplayName("Removes control and format characters (BOM, zero-width, soft hyphen, bell)")
  void removesControlAndFormatCharacters() {
    assertThat(BulkSubmissionTextSanitiser.sanitise("\uFEFFa\u200bb\u00adc\u0007"))
        .isEqualTo("abc");
  }

  @Test
  @DisplayName("Removes private-use and unassigned code points")
  void removesPrivateUseAndUnassignedCodePoints() {
    String privateUse = new String(Character.toChars(0xE000)); // private-use area, U+E000
    assertThat(BulkSubmissionTextSanitiser.sanitise("a" + privateUse + "b")).isEqualTo("ab");
  }

  @Test
  @DisplayName("Normalises a non-breaking space to a regular space so word breaks survive")
  void normalisesNonBreakingSpaceToRegularSpace() {
    assertThat(BulkSubmissionTextSanitiser.sanitise("SW1H\u00a09EA")).isEqualTo("SW1H 9EA"); // NBSP
  }

  @Test
  @DisplayName("Normalises multiple consecutive non-breaking spaces")
  void normalisesMultipleNonBreakingSpaces() {
    assertThat(BulkSubmissionTextSanitiser.sanitise("a\u00a0\u00a0b")).isEqualTo("a  b"); // 2x NBSP
  }
}
