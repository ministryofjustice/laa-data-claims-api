package uk.gov.justice.laa.dstew.payments.claimsdata.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.CharConversionException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link BulkSubmissionDecodingFailures}. */
class BulkSubmissionDecodingFailuresTests {

  @Nested
  @DisplayName("isCharacterDecodingFailure")
  class IsCharacterDecodingFailure {

    @Test
    @DisplayName("Returns true for a direct CharConversionException")
    void directCharConversionException() {
      assertThat(
              BulkSubmissionDecodingFailures.isCharacterDecodingFailure(
                  new CharConversionException("bad byte")))
          .isTrue();
    }

    @Test
    @DisplayName("Returns true for a direct CharacterCodingException")
    void directCharacterCodingException() {
      assertThat(BulkSubmissionDecodingFailures.isCharacterDecodingFailure(newCce())).isTrue();
    }

    @Test
    @DisplayName("Returns true when the decoding exception is wrapped one level")
    void wrappedOneLevel() {
      IOException wrapper = new IOException("wrapper", new CharConversionException("bad byte"));
      assertThat(BulkSubmissionDecodingFailures.isCharacterDecodingFailure(wrapper)).isTrue();
    }

    @Test
    @DisplayName(
        "Returns true when the decoding exception is wrapped two levels deep"
            + " (matches the real JsonParseException -> WstxIOException -> CharConversionException chain)")
    void wrappedTwoLevels() {
      CharConversionException root = new CharConversionException("bad byte");
      IOException middle = new IOException("middle", root);
      IOException outer = new IOException("outer", middle);
      assertThat(BulkSubmissionDecodingFailures.isCharacterDecodingFailure(outer)).isTrue();
    }

    @Test
    @DisplayName("Returns false for an unrelated IOException")
    void unrelatedIoException() {
      assertThat(
              BulkSubmissionDecodingFailures.isCharacterDecodingFailure(
                  new IOException("disk full")))
          .isFalse();
    }

    @Test
    @DisplayName("Returns false for an unrelated IOException with an unrelated cause")
    void unrelatedIoExceptionWithUnrelatedCause() {
      IOException e = new IOException("outer", new RuntimeException("unrelated"));
      assertThat(BulkSubmissionDecodingFailures.isCharacterDecodingFailure(e)).isFalse();
    }

    @Test
    @DisplayName("Returns false for null-message, null-cause exception")
    void nullMessageNullCause() {
      assertThat(BulkSubmissionDecodingFailures.isCharacterDecodingFailure(new IOException()))
          .isFalse();
    }

    @Test
    @DisplayName("Does not loop forever on a self-referential cause chain")
    void selfReferentialCauseChainDoesNotHang() {
      Throwable a = new IOException("a");
      Throwable b = new IOException("b", a);
      a.initCause(b); // artificial cycle: a -> b -> a
      assertThat(BulkSubmissionDecodingFailures.isCharacterDecodingFailure(a)).isFalse();
    }

    private CharacterCodingException newCce() {
      return new CharacterCodingException();
    }
  }

  @Nested
  @DisplayName("detectCharset")
  class DetectCharset {

    @Test
    @DisplayName("Detects UTF-8 from its BOM")
    void detectsUtf8Bom() {
      byte[] prefix = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'a'};
      assertThat(BulkSubmissionDecodingFailures.detectCharset(prefix, false))
          .contains(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Detects UTF-16BE from its BOM")
    void detectsUtf16BeBom() {
      byte[] prefix = {(byte) 0xFE, (byte) 0xFF, 0, 'a'};
      assertThat(BulkSubmissionDecodingFailures.detectCharset(prefix, false))
          .contains(StandardCharsets.UTF_16BE);
    }

    @Test
    @DisplayName("Detects UTF-16LE from its BOM")
    void detectsUtf16LeBom() {
      byte[] prefix = {(byte) 0xFF, (byte) 0xFE, 'a', 0};
      assertThat(BulkSubmissionDecodingFailures.detectCharset(prefix, false))
          .contains(StandardCharsets.UTF_16LE);
    }

    @Test
    @DisplayName("Falls back to the no-BOM heuristic to detect UTF-16LE ASCII content")
    void detectsUtf16LeWithoutBomByHeuristic() {
      byte[] prefix = "OFFICE".getBytes(StandardCharsets.UTF_16LE);
      assertThat(BulkSubmissionDecodingFailures.detectCharset(prefix, false))
          .contains(StandardCharsets.UTF_16LE);
    }

    @Test
    @DisplayName("Falls back to the no-BOM heuristic to detect UTF-16BE ASCII content")
    void detectsUtf16BeWithoutBomByHeuristic() {
      byte[] prefix = "OFFICE".getBytes(StandardCharsets.UTF_16BE);
      assertThat(BulkSubmissionDecodingFailures.detectCharset(prefix, false))
          .contains(StandardCharsets.UTF_16BE);
    }

    @Test
    @DisplayName("Defaults to UTF-8 for plain ASCII/UTF-8 content with no BOM")
    void defaultsToUtf8ForPlainContent() {
      byte[] prefix = "OFFICE,account=0U099L".getBytes(StandardCharsets.UTF_8);
      assertThat(BulkSubmissionDecodingFailures.detectCharset(prefix, false))
          .contains(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Detects a declared XML encoding attribute when no BOM is present")
    void detectsDeclaredXmlEncoding() {
      byte[] prefix =
          "<?xml version='1.0' encoding='windows-1252'?><submission>"
              .getBytes(StandardCharsets.US_ASCII);
      assertThat(BulkSubmissionDecodingFailures.detectCharset(prefix, true))
          .contains(Charset.forName("windows-1252"));
    }

    @Test
    @DisplayName("Does not treat an encoding attribute in XML content as a declaration")
    void ignoresEncodingAttributeOutsideXmlDeclaration() {
      byte[] prefix =
          "<submission>caf\u00e9 encoding=\"US-ASCII\"</submission>"
              .getBytes(StandardCharsets.UTF_8);
      assertThat(BulkSubmissionDecodingFailures.detectCharset(prefix, true))
          .contains(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Ignores a declared XML encoding attribute for non-XML (CSV) content")
    void ignoresDeclaredEncodingForNonXml() {
      // Looks like it contains an "encoding=" attribute, but this is CSV, not XML.
      byte[] prefix = "OFFICE,encoding='windows-1252'".getBytes(StandardCharsets.UTF_8);
      assertThat(BulkSubmissionDecodingFailures.detectCharset(prefix, false))
          .contains(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Falls back to the heuristic when the declared XML encoding name is unrecognised")
    void fallsBackWhenDeclaredEncodingUnrecognised() {
      byte[] prefix =
          "<?xml version='1.0' encoding='not-a-real-charset'?>".getBytes(StandardCharsets.UTF_8);
      assertThat(BulkSubmissionDecodingFailures.detectCharset(prefix, true))
          .contains(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Returns empty for an unsupported UTF-32LE BOM rather than guessing wrongly")
    void returnsEmptyForUtf32Bom() {
      byte[] prefix = {(byte) 0xFF, (byte) 0xFE, 0, 0};
      assertThat(BulkSubmissionDecodingFailures.detectCharset(prefix, false)).isEmpty();
    }
  }

  @Nested
  @DisplayName("findFailureLocation / buildMessage")
  class FindFailureLocation {

    @Test
    @DisplayName("Finds line 1, character 4 when the invalid byte follows 3 ASCII characters")
    void findsLineOneWhenBadByteOnFirstLine() throws IOException {
      byte[] bytes = concat("abc".getBytes(StandardCharsets.UTF_8), invalidUtf8Byte());
      assertThat(findUtf8Location(bytes))
          .contains(new BulkSubmissionDecodingFailures.Location(1, 4));
    }

    @Test
    @DisplayName("Finds the correct later line/character position when preceded by newlines")
    void findsLaterLineNumber() throws IOException {
      byte[] bytes =
          concat(
              "line one\nline two\nline three-".getBytes(StandardCharsets.UTF_8),
              invalidUtf8Byte());
      // "line three-" is 11 characters, so the bad byte is the 12th character on line 3.
      assertThat(findUtf8Location(bytes))
          .contains(new BulkSubmissionDecodingFailures.Location(3, 12));
    }

    @Test
    @DisplayName("Returns empty for valid UTF-8 bytes")
    void emptyForValidUtf8() throws IOException {
      byte[] bytes = "all valid utf-8".getBytes(StandardCharsets.UTF_8);
      assertThat(findUtf8Location(bytes)).isEmpty();
    }

    @Test
    @DisplayName(
        "Character position counts decoded characters, not raw bytes, when a multi-byte"
            + " character precedes the failure on the same line")
    void characterPositionCountsCharactersNotBytes() throws IOException {
      // "caf\u00e9" (cafe with an e-acute) is 4 characters but 5 bytes in UTF-8 (e-acute is
      // 2 bytes), so a byte-based position would incorrectly report character 6.
      byte[] bytes =
          concat("caf\u00e9".getBytes(StandardCharsets.UTF_8), invalidUtf8Byte()); // e-acute
      assertThat(findUtf8Location(bytes))
          .contains(new BulkSubmissionDecodingFailures.Location(1, 5));
    }

    @Test
    @DisplayName("Treats a lone \\r as a line break (old Mac-style line endings)")
    void treatsLoneCrAsLineBreak() throws IOException {
      byte[] bytes =
          concat(
              "line one\rline two\rline three-".getBytes(StandardCharsets.UTF_8),
              invalidUtf8Byte());
      assertThat(findUtf8Location(bytes))
          .contains(new BulkSubmissionDecodingFailures.Location(3, 12));
    }

    @Test
    @DisplayName("Treats \\r\\n as a single line break, not two")
    void treatsCrLfAsSingleLineBreak() throws IOException {
      byte[] bytes =
          concat(
              "line one\r\nline two\r\nline three-".getBytes(StandardCharsets.UTF_8),
              invalidUtf8Byte());
      assertThat(findUtf8Location(bytes))
          .contains(new BulkSubmissionDecodingFailures.Location(3, 12));
    }

    @Test
    @DisplayName(
        "Reports the correct location for a decoding failure in a non-UTF-8 (UTF-16LE) file,"
            + " rather than stopping at an unrelated early byte")
    void findsCorrectLocationInUtf16LeFile() throws IOException {
      String validPrefix = "line one\nline two-";
      byte[] validBytes = validPrefix.getBytes(StandardCharsets.UTF_16LE);
      // A lone low surrogate (with no preceding high surrogate) is malformed UTF-16.
      byte[] loneLowSurrogate = {0x00, (byte) 0xDC};
      byte[] bytes = concat(validBytes, loneLowSurrogate);

      Optional<BulkSubmissionDecodingFailures.Location> location;
      try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
        location =
            BulkSubmissionDecodingFailures.findFailureLocation(
                inputStream, StandardCharsets.UTF_16LE);
      }

      assertThat(location)
          .contains(new BulkSubmissionDecodingFailures.Location(2, "line two-".length() + 1));
    }

    @Test
    @DisplayName("buildMessage includes the line and character position when determinable")
    void buildMessageIncludesLineNumber() {
      String message =
          BulkSubmissionDecodingFailures.buildMessage(
              Optional.of(new BulkSubmissionDecodingFailures.Location(2, 10)));
      assertThat(message)
          .isEqualTo(
              BulkSubmissionConverter.CHARACTER_DECODING_ERROR_MESSAGE_WITH_LINE_TEMPLATE.formatted(
                  2, 10));
    }

    @Test
    @DisplayName("buildMessage falls back to the location-less message when empty")
    void buildMessageFallsBackForValidBytes() {
      assertThat(BulkSubmissionDecodingFailures.buildMessage(Optional.empty()))
          .isEqualTo(BulkSubmissionConverter.CHARACTER_DECODING_ERROR_MESSAGE);
    }

    @Test
    @DisplayName(
        "Finds the correct location when a single line is much longer than the internal decode"
            + " buffers (proves the streaming, bounded-memory decode loop is correct across"
            + " multiple internal buffer refills, not just for small files)")
    void findsCorrectLocationAcrossMultipleInternalDecodeChunks() throws IOException {
      // Deliberately larger than the 8192-byte internal chunk buffers, and with no newlines, so a
      // single call must refill its buffers many times before reaching the failure - this is the
      // worst case the streaming/chunking approach targets.
      String longAsciiLine = "a".repeat(50_000);
      byte[] bytes = concat(longAsciiLine.getBytes(StandardCharsets.UTF_8), invalidUtf8Byte());

      assertThat(findUtf8Location(bytes))
          .contains(new BulkSubmissionDecodingFailures.Location(1, longAsciiLine.length() + 1));
    }

    @Test
    @DisplayName(
        "Handles the worst case for a file at the upload size limit (a single ~10MB line with the"
            + " bad byte at the very end) correctly and quickly, without buffering the whole file")
    void handlesUploadSizeLimitWorstCaseQuickly() throws IOException {
      // 10MB is the configured spring.servlet.multipart.max-file-size / max-request-size limit
      // (see application.yml). A single line with no newlines and the bad byte right at the end is
      // the worst case: every byte must be streamed and examined.
      int tenMegabytes = 10 * 1024 * 1024;
      String longAsciiLine = "a".repeat(tenMegabytes - 10);
      byte[] bytes = concat(longAsciiLine.getBytes(StandardCharsets.UTF_8), invalidUtf8Byte());

      long start = System.nanoTime();
      var location = findUtf8Location(bytes);
      long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

      assertThat(location)
          .contains(new BulkSubmissionDecodingFailures.Location(1, longAsciiLine.length() + 1));
      // Generous bound: this typically completes in well under 100ms; 5s guards against a
      // regression back to an approach whose cost scales badly with file size, without making the
      // test flaky on a slow CI runner.
      assertThat(elapsedMillis).isLessThan(5_000);
    }

    private Optional<BulkSubmissionDecodingFailures.Location> findUtf8Location(byte[] bytes)
        throws IOException {
      try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
        return BulkSubmissionDecodingFailures.findFailureLocation(
            inputStream, StandardCharsets.UTF_8);
      }
    }

    private byte[] invalidUtf8Byte() {
      return new byte[] {(byte) 0xE2, (byte) 0x28, (byte) 0xA1}; // invalid continuation byte
    }

    private byte[] concat(byte[] a, byte[] b) {
      byte[] out = new byte[a.length + b.length];
      System.arraycopy(a, 0, out, 0, a.length);
      System.arraycopy(b, 0, out, a.length, b.length);
      return out;
    }
  }
}
