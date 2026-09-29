package uk.gov.justice.laa.dstew.payments.claimsdata.converter;

import java.io.CharConversionException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

/**
 * Shared, narrowly-scoped helper for recognising genuine character-decoding failures raised while
 * Jackson (CSV or XML) reads a bulk submission file, and for locating an approximate line/character
 * position for such a failure.
 *
 * <p>This helper deliberately does <b>not</b>:
 *
 * <ul>
 *   <li>inspect exception messages to classify a failure (type/cause based only)
 *   <li>perform charset detection or decide which encodings are acceptable
 *   <li>pre-read the file before Jackson does
 * </ul>
 *
 * <p>It is intentionally narrow: it recognises only the confirmed character-conversion exception
 * types produced by the project's resolved Jackson/Woodstox versions. It is a "catch the common
 * cases, be helpful, not exhaustive" classifier, not a general-purpose parser-error classifier.
 */
@Slf4j
final class BulkSubmissionDecodingFailures {

  /** Maximum depth walked up the cause chain, guarding against pathological/cyclic chains. */
  private static final int MAX_CAUSE_CHAIN_DEPTH = 10;

  /**
   * Size of the reusable output buffer used to find the decoding failure position. Kept small and
   * constant (rather than sized to the file) so peak memory use does not scale with file size.
   */
  private static final int DECODE_CHUNK_CHARS = 8192;

  private BulkSubmissionDecodingFailures() {}

  /**
   * The 1-based location (line and character-within-line) of a character-decoding failure within a
   * file.
   *
   * @param line the 1-based line number
   * @param character the 1-based character position within that line
   */
  record Location(int line, int character) {}

  /**
   * Determines whether the given exception, or any exception in its cause chain, represents a
   * genuine character-decoding failure (as opposed to a structural, mapping or validation failure).
   *
   * @param throwable the exception caught at the converter boundary
   * @return true if a {@link CharConversionException} or {@link CharacterCodingException} is found
   *     anywhere in the cause chain
   */
  static boolean isCharacterDecodingFailure(Throwable throwable) {
    Set<Throwable> seen = new HashSet<>();
    Throwable current = throwable;
    int depth = 0;
    while (current != null && depth < MAX_CAUSE_CHAIN_DEPTH && seen.add(current)) {
      if (current instanceof CharConversionException
          || current instanceof CharacterCodingException) {
        return true;
      }
      current = current.getCause();
      depth++;
    }
    return false;
  }

  /**
   * Determines the 1-based line number and character-within-line position of the first byte that
   * could not be decoded as valid UTF-8 within the given raw file bytes.
   *
   * <p>This performs its own independent decode pass (rather than parsing Jackson/Woodstox
   * exception messages) because those internal position figures were found, by experimentation
   * against this project's resolved dependency versions, to be unreliable for XML (buffer-relative
   * and capped at internal read-buffer boundaries). Running a single, whole-array decode ourselves
   * gives an exact byte offset, from which a genuine line number can be derived by counting
   * newlines, consistently for both CSV/TXT and XML. The character-within-line position is derived
   * by re-decoding just the (already known-good) bytes from the start of that line up to the
   * failure, so it reflects actual characters rather than raw bytes.
   *
   * <p>This only runs on the already-confirmed decoding-failure path (i.e. after {@link
   * #isCharacterDecodingFailure(Throwable)} has matched), so it adds no cost to successful uploads.
   *
   * @param rawBytes the complete raw bytes of the uploaded file
   * @return the location of the failure, or empty if the bytes could not be shown to contain an
   *     invalid UTF-8 sequence (e.g. a non-UTF-8-specific decoding failure)
   */
  static Optional<Location> findInvalidUtf8Location(byte[] rawBytes) {
    int failurePosition = findFailureBytePosition(rawBytes);
    if (failurePosition < 0) {
      return Optional.empty();
    }

    int line = 1;
    int lineStart = 0;
    for (int i = 0; i < failurePosition && i < rawBytes.length; i++) {
      if (rawBytes[i] == '\n') {
        line++;
        lineStart = i + 1;
      }
    }

    int character = decodeCharCount(rawBytes, lineStart, failurePosition) + 1;
    return Optional.of(new Location(line, character));
  }

  /**
   * Runs a UTF-8 decode and returns the byte offset at which decoding first failed, or -1 if the
   * bytes are entirely valid UTF-8.
   *
   * <p>Decodes in small fixed-size chunks (rather than allocating an output buffer sized to the
   * whole file) so peak memory use is a small constant, not proportional to file size. This keeps
   * the failure path cheap even if the upload size limit (10MB at the time of writing, see {@code
   * spring.servlet.multipart.max-file-size}) is ever increased.
   */
  private static int findFailureBytePosition(byte[] rawBytes) {
    ByteBuffer in = ByteBuffer.wrap(rawBytes);
    CharBuffer out = CharBuffer.allocate(DECODE_CHUNK_CHARS);
    CharsetDecoder decoder = newStrictUtf8Decoder();

    while (in.hasRemaining()) {
      out.clear();
      CoderResult result = decoder.decode(in, out, true);
      if (result.isError()) {
        return in.position();
      }
    }
    return -1;
  }

  /**
   * Counts the number of characters represented by the given (already known-good, since it precedes
   * the failure position) byte range, so a byte offset can be translated into a human-meaningful
   * character-within-line position.
   *
   * <p>Rather than re-decoding the range with a {@link CharsetDecoder} (which would risk a second
   * full-file-sized decode pass in the pathological case of one very long line), this simply counts
   * the bytes that are not UTF-8 continuation bytes ({@code 10xxxxxx}). Every UTF-8 character has
   * exactly one non-continuation (leading) byte, so this is an exact, single-pass, zero-allocation
   * character count for a byte range that is already known to be valid UTF-8.
   */
  private static int decodeCharCount(byte[] rawBytes, int fromInclusive, int toExclusive) {
    int count = 0;
    for (int i = fromInclusive; i < toExclusive; i++) {
      if ((rawBytes[i] & 0xC0) != 0x80) {
        count++;
      }
    }
    return count;
  }

  private static CharsetDecoder newStrictUtf8Decoder() {
    return StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
  }

  /**
   * Builds the provider-facing message for a confirmed character-decoding failure, including a
   * line/character position when one can be determined from the raw file bytes.
   *
   * @param rawBytes the complete raw bytes of the uploaded file
   * @return the provider-facing message
   */
  static String buildMessage(byte[] rawBytes) {
    return findInvalidUtf8Location(rawBytes)
        .map(
            location ->
                BulkSubmissionConverter.CHARACTER_DECODING_ERROR_MESSAGE_WITH_LINE_TEMPLATE
                    .formatted(location.line(), location.character()))
        .orElse(BulkSubmissionConverter.CHARACTER_DECODING_ERROR_MESSAGE);
  }

  /**
   * Builds the provider-facing character-decoding-failure message for an uploaded file, including a
   * line/character position when it can be determined. Falls back to the location-less message if
   * the raw bytes cannot be re-read for any reason (defensive; should not normally happen since
   * {@link MultipartFile#getBytes()} reads from already-buffered content).
   *
   * <p>Shared by both {@link BulkSubmissionCsvConverter} and {@link BulkSubmissionXmlConverter} so
   * the logic is not duplicated.
   *
   * @param file the uploaded file
   * @return the provider-facing message
   */
  static String characterDecodingMessage(MultipartFile file) {
    try {
      return buildMessage(file.getBytes());
    } catch (IOException e) {
      log.debug("Unable to re-read file bytes to determine decoding failure location", e);
      return BulkSubmissionConverter.CHARACTER_DECODING_ERROR_MESSAGE;
    }
  }
}
