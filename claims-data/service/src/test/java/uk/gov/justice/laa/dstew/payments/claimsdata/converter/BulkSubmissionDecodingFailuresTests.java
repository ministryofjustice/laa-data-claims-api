package uk.gov.justice.laa.dstew.payments.claimsdata.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.CharConversionException;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
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
  @DisplayName("findInvalidUtf8Location / buildMessage")
  class FindInvalidUtf8Location {

    @Test
    @DisplayName("Finds line 1, character 4 when the invalid byte follows 3 ASCII characters")
    void findsLineOneWhenBadByteOnFirstLine() {
      byte[] bytes = concat("abc".getBytes(StandardCharsets.UTF_8), invalidUtf8Byte());
      assertThat(BulkSubmissionDecodingFailures.findInvalidUtf8Location(bytes))
          .contains(new BulkSubmissionDecodingFailures.Location(1, 4));
    }

    @Test
    @DisplayName("Finds the correct later line/character position when preceded by newlines")
    void findsLaterLineNumber() {
      byte[] bytes =
          concat(
              "line one\nline two\nline three-".getBytes(StandardCharsets.UTF_8),
              invalidUtf8Byte());
      // "line three-" is 11 characters, so the bad byte is the 12th character on line 3.
      assertThat(BulkSubmissionDecodingFailures.findInvalidUtf8Location(bytes))
          .contains(new BulkSubmissionDecodingFailures.Location(3, 12));
    }

    @Test
    @DisplayName("Returns empty for valid UTF-8 bytes")
    void emptyForValidUtf8() {
      byte[] bytes = "all valid utf-8".getBytes(StandardCharsets.UTF_8);
      assertThat(BulkSubmissionDecodingFailures.findInvalidUtf8Location(bytes)).isEmpty();
    }

    @Test
    @DisplayName(
        "Character position counts decoded characters, not raw bytes, when a multi-byte"
            + " character precedes the failure on the same line")
    void characterPositionCountsCharactersNotBytes() {
      // "caf\u00e9" (cafe with an e-acute) is 4 characters but 5 bytes in UTF-8 (e-acute is
      // 2 bytes), so a byte-based position would incorrectly report character 6.
      byte[] bytes =
          concat("caf\u00e9".getBytes(StandardCharsets.UTF_8), invalidUtf8Byte()); // e-acute
      assertThat(BulkSubmissionDecodingFailures.findInvalidUtf8Location(bytes))
          .contains(new BulkSubmissionDecodingFailures.Location(1, 5));
    }

    @Test
    @DisplayName("buildMessage includes the line and character position when determinable")
    void buildMessageIncludesLineNumber() {
      byte[] bytes =
          concat("line one\nline two-".getBytes(StandardCharsets.UTF_8), invalidUtf8Byte());
      String message = BulkSubmissionDecodingFailures.buildMessage(bytes);
      // "line two-" is 9 characters, so the bad byte is the 10th character on line 2.
      assertThat(message)
          .isEqualTo(
              BulkSubmissionConverter.CHARACTER_DECODING_ERROR_MESSAGE_WITH_LINE_TEMPLATE.formatted(
                  2, 10));
    }

    @Test
    @DisplayName("buildMessage falls back to the location-less message for valid UTF-8 bytes")
    void buildMessageFallsBackForValidBytes() {
      byte[] bytes = "all valid utf-8".getBytes(StandardCharsets.UTF_8);
      assertThat(BulkSubmissionDecodingFailures.buildMessage(bytes))
          .isEqualTo(BulkSubmissionConverter.CHARACTER_DECODING_ERROR_MESSAGE);
    }

    @Test
    @DisplayName(
        "Finds the correct location when a single line is much longer than the internal decode"
            + " chunk buffer (proves the bounded-memory chunked decode loop is correct across"
            + " multiple internal buffer refills, not just for small files)")
    void findsCorrectLocationAcrossMultipleInternalDecodeChunks() {
      // Deliberately larger than the 8192-character internal chunk buffer, and with no newlines,
      // so a single call to findInvalidUtf8Location must refill its small output buffer many times
      // before reaching the failure - this is the worst case the chunking optimisation targets.
      String longAsciiLine = "a".repeat(50_000);
      byte[] bytes = concat(longAsciiLine.getBytes(StandardCharsets.UTF_8), invalidUtf8Byte());

      assertThat(BulkSubmissionDecodingFailures.findInvalidUtf8Location(bytes))
          .contains(new BulkSubmissionDecodingFailures.Location(1, longAsciiLine.length() + 1));
    }

    @Test
    @DisplayName(
        "Handles the worst case for a file at the upload size limit (a single ~10MB line with the"
            + " bad byte at the very end) correctly and quickly, with no proportional-to-file-size"
            + " re-decode")
    void handlesUploadSizeLimitWorstCaseQuickly() {
      // 10MB is the configured spring.servlet.multipart.max-file-size / max-request-size limit
      // (see application.yml). A single line with no newlines and the bad byte right at the end is
      // the worst case for both the failure-position search and the character count: every byte
      // must be examined, and (before the chunking/lead-byte-counting optimisation) would have
      // triggered a second near-whole-file re-decode.
      int tenMegabytes = 10 * 1024 * 1024;
      String longAsciiLine = "a".repeat(tenMegabytes - 10);
      byte[] bytes = concat(longAsciiLine.getBytes(StandardCharsets.UTF_8), invalidUtf8Byte());

      long start = System.nanoTime();
      var location = BulkSubmissionDecodingFailures.findInvalidUtf8Location(bytes);
      long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

      assertThat(location)
          .contains(new BulkSubmissionDecodingFailures.Location(1, longAsciiLine.length() + 1));
      // Generous bound: this typically completes in well under 100ms; 5s guards against a
      // regression back to an approach whose cost scales badly with file size, without making the
      // test flaky on a slow CI runner.
      assertThat(elapsedMillis).isLessThan(5_000);
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
