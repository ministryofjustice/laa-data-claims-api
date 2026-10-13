package uk.gov.justice.laa.dstew.payments.claimsdata.converter;

import java.io.CharConversionException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Shared, narrowly-scoped helper for recognizing genuine character-decoding failures raised while
 * Jackson (CSV or XML) reads a bulk submission file, and for locating an approximate line/character
 * position for such a failure.
 *
 * <p>This helper deliberately does <b>not</b>:
 *
 * <ul>
 *   <li>inspect exception messages to classify a failure (type/cause based only)
 *   <li>decide which encodings are acceptable (that remains entirely up to Jackson/Woodstox)
 *   <li>pre-read the file before Jackson does
 * </ul>
 *
 * <p>It is intentionally narrow: it recognizes only the confirmed character-conversion exception
 * types produced by the project's resolved Jackson/Woodstox versions, and only reports a location
 * when it can determine, with the same cheap signals Jackson itself uses (BOM, byte-pattern
 * heuristic, or declared XML encoding), which charset the failing decode was actually using. It is
 * a "catch the common cases, be helpful, not exhaustive" classifier, not a general-purpose
 * parser-error classifier.
 */
@Slf4j
final class BulkSubmissionDecodingFailures {

  /**
   * Determines whether the given exception, or any exception in its cause chain, represents a
   * genuine character-decoding failure (as opposed to a structural, mapping or validation failure).
   *
   * <p>Uses {@link ExceptionUtils#getThrowableList(Throwable)} (Commons Lang, already a dependency
   * of this project) to walk the cause chain, rather than a hand-rolled loop: it provides the same
   * cycle protection we would otherwise have to write and test ourselves.
   *
   * @param throwable the exception caught at the converter boundary
   * @return true if a {@link CharConversionException} or {@link CharacterCodingException} is found
   *     anywhere in the cause chain
   */
  static boolean isCharacterDecodingFailure(Throwable throwable) {
    return ExceptionUtils.getThrowableList(throwable).stream()
        .anyMatch(
            t -> t instanceof CharConversionException || t instanceof CharacterCodingException);
  }

  /**
   * Size of the fixed-size internal byte buffer used while streaming the file for location-finding.
   * Kept small and constant (rather than sized to the file) so peak memory use does not scale with
   * file size, regardless of how large an upload is ever allowed to be.
   */
  private static final int DECODE_BUFFER_BYTES = 8192;

  private static final int DECODE_BUFFER_CHARS = 8192;

  /** Number of leading bytes read (once, cheaply) to sniff a BOM or declared XML encoding. */
  private static final int CHARSET_SNIFF_PREFIX_BYTES = 1024;

  // Safe here because this pattern is only ever evaluated against the bounded 1 KiB charset-sniff
  // prefix, and it is anchored to the start of an XML declaration.
  @SuppressWarnings("java:S8786")
  private static final Pattern XML_ENCODING_DECLARATION_PATTERN =
      Pattern.compile(
          "\\A<\\?xml\\s+[^?]*\\bencoding\\s*=\\s*[\"']([\\w.:-]+)[\"'][^?]*\\?>",
          Pattern.CASE_INSENSITIVE);

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
   * Determines the character set that Jackson itself would most likely have used to read the file,
   * using only the same cheap signals it uses: a byte-order-mark, and (for XML only) a declared
   * {@code encoding="..."} attribute in the prologue. Falls back to a byte-pattern heuristic
   * equivalent to the one Jackson uses when no BOM is present.
   *
   * <p>Returns empty when none of these signals gives a confident answer (e.g. a UTF-32 BOM, or an
   * unrecognised declared encoding name) - in that case the caller should omit a location rather
   * than risk reporting one against the wrong charset.
   *
   * @param prefixBytes the leading bytes of the file (does not need to be the whole file)
   * @param xml whether the file is XML (enables declared-encoding sniffing)
   * @return the detected charset, or empty if it could not be determined confidently
   */
  static Optional<Charset> detectCharset(byte[] prefixBytes, boolean xml) {
    Optional<Charset> bomCharset = detectBom(prefixBytes);
    if (bomCharset.isPresent()) {
      return bomCharset;
    }
    if (xml) {
      Optional<Charset> declaredCharset = detectXmlDeclaredEncoding(prefixBytes);
      if (declaredCharset.isPresent()) {
        return declaredCharset;
      }
    }
    return detectByHeuristic(prefixBytes);
  }

  private static Optional<Charset> detectBom(byte[] bytes) {
    if (startsWith(bytes, 0xEF, 0xBB, 0xBF)) {
      return Optional.of(StandardCharsets.UTF_8);
    }
    if (startsWith(bytes, 0x00, 0x00, 0xFE, 0xFF) || startsWith(bytes, 0xFF, 0xFE, 0x00, 0x00)) {
      // UTF-32 BOM (BE or LE) - not supported by the location-finding logic, so treat as
      // undetectable rather than mis-decode as UTF-16.
      return Optional.empty();
    }
    if (startsWith(bytes, 0xFE, 0xFF)) {
      return Optional.of(StandardCharsets.UTF_16BE);
    }
    if (startsWith(bytes, 0xFF, 0xFE)) {
      return Optional.of(StandardCharsets.UTF_16LE);
    }
    return Optional.empty();
  }

  private static boolean startsWith(byte[] bytes, int... unsignedBytePattern) {
    if (bytes.length < unsignedBytePattern.length) {
      return false;
    }
    for (int i = 0; i < unsignedBytePattern.length; i++) {
      if ((bytes[i] & 0xFF) != unsignedBytePattern[i]) {
        return false;
      }
    }
    return true;
  }

  /**
   * Mirrors the byte-pattern heuristic Jackson's own factory uses to guess an encoding when no BOM
   * is present (based on the positions of zero bytes in the first four bytes, per the RFC 4627
   * appendix B algorithm): a genuine UTF-16/UTF-32 file with ASCII-range content in its first
   * character will have a zero byte in a predictable position, whereas UTF-8 (or ASCII-compatible
   * single-byte) content will not.
   */
  private static Optional<Charset> detectByHeuristic(byte[] bytes) {
    if (bytes.length < 4) {
      return Optional.of(StandardCharsets.UTF_8);
    }
    int b0 = bytes[0] & 0xFF;
    int b1 = bytes[1] & 0xFF;
    int b2 = bytes[2] & 0xFF;
    int b3 = bytes[3] & 0xFF;
    if ((b0 == 0 && b1 == 0) || (b2 == 0 && b3 == 0)) {
      // Looks like UTF-32 without a BOM - not supported, treat as undetectable.
      return Optional.empty();
    }
    if (b0 == 0) {
      return Optional.of(StandardCharsets.UTF_16BE);
    }
    if (b1 == 0) {
      return Optional.of(StandardCharsets.UTF_16LE);
    }
    return Optional.of(StandardCharsets.UTF_8);
  }

  /**
   * Looks for a declared {@code encoding="..."} attribute in an XML prologue. The prefix is decoded
   * as ISO-8859-1 purely to sniff this attribute name: that decode can never itself fail (every
   * byte maps to a character 1:1), regardless of the file's real encoding, so it is safe to use
   * even before the real encoding is known.
   */
  private static Optional<Charset> detectXmlDeclaredEncoding(byte[] prefixBytes) {
    String prolog = new String(prefixBytes, StandardCharsets.ISO_8859_1);
    Matcher matcher = XML_ENCODING_DECLARATION_PATTERN.matcher(prolog);
    if (!matcher.find()) {
      return Optional.empty();
    }
    try {
      return Optional.of(Charset.forName(matcher.group(1)));
    } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
      log.debug("Declared XML encoding '{}' is not recognised", matcher.group(1), e);
      return Optional.empty();
    }
  }

  /**
   * Streams the given input through a decoder for the given charset, in small fixed-size buffers,
   * tracking line and character-within-line position as it goes, until either a decoding error is
   * found or the stream is exhausted.
   *
   * <p>Treats {@code \n}, {@code \r} and {@code \r\n} all as a single line break, so files using
   * any of the three common line-ending conventions are reported correctly.
   *
   * <p>Uses the low-level {@link CharsetDecoder#decode(ByteBuffer, CharBuffer, boolean)} API
   * directly, rather than wrapping it in a {@link java.io.Reader}: a {@code Reader}'s {@code
   * read(...)} methods report a decoding failure by throwing, and in doing so, they do not reliably
   * expose how many characters were already decoded successfully within that same call (the
   * standard {@code Reader.read(CharBuffer)} default implementation only advances the buffer's
   * position on a normal return, not when an exception propagates) - which is exactly the
   * information this method needs to report an accurate location. {@link CharsetDecoder#decode}
   * itself never throws; it returns a {@link CoderResult} while still having advanced the output
   * buffer for everything decoded so far, which is what makes an accurate position possible here.
   *
   * <p>Peak memory use is a small constant (two ~8KB buffers), not proportional to file size, since
   * the input is streamed rather than read into a single byte array.
   *
   * @param inputStream the raw file content
   * @param charset the charset to decode with (should be the same one the real parser used)
   * @return the location of the first decoding failure, or empty if the stream is entirely valid
   *     under the given charset
   */
  static Optional<Location> findFailureLocation(InputStream inputStream, Charset charset)
      throws IOException {
    CharsetDecoder decoder =
        charset
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);

    ByteBuffer in = ByteBuffer.allocate(DECODE_BUFFER_BYTES);
    CharBuffer out = CharBuffer.allocate(DECODE_BUFFER_CHARS);
    byte[] readBuffer = new byte[DECODE_BUFFER_BYTES];
    LineTracker tracker = new LineTracker();

    in.limit(0); // start empty, ready for the first compact()/read()
    boolean streamExhausted = false;
    while (true) {
      in.compact();
      if (!streamExhausted && in.hasRemaining()) {
        int read = inputStream.read(readBuffer, 0, Math.min(readBuffer.length, in.remaining()));
        if (read < 0) {
          streamExhausted = true;
        } else if (read > 0) {
          in.put(readBuffer, 0, read);
        }
      }
      in.flip();

      out.clear();
      CoderResult result = decoder.decode(in, out, streamExhausted);
      out.flip();
      tracker.consume(out);

      if (result.isError()) {
        return Optional.of(tracker.currentLocation());
      }
      if (streamExhausted && !in.hasRemaining()) {
        out.clear();
        CoderResult flushResult = decoder.flush(out);
        out.flip();
        tracker.consume(out);
        if (flushResult.isError()) {
          return Optional.of(tracker.currentLocation());
        }
        return Optional.empty();
      }
    }
  }

  /** Tracks 1-based line/character-within-line position as decoded characters are consumed. */
  private static final class LineTracker {
    private int line = 1;
    private int charInLine = 0;
    private boolean previousWasCr = false;

    void consume(CharBuffer decodedChars) {
      while (decodedChars.hasRemaining()) {
        char c = decodedChars.get();
        if (c == '\n') {
          if (!previousWasCr) {
            line++;
          }
          charInLine = 0;
          previousWasCr = false;
        } else if (c == '\r') {
          line++;
          charInLine = 0;
          previousWasCr = true;
        } else {
          charInLine++;
          previousWasCr = false;
        }
      }
    }

    Location currentLocation() {
      return new Location(line, charInLine + 1);
    }
  }

  /**
   * Builds the provider-facing message for a confirmed character-decoding failure, including a
   * line/character position when one could be determined.
   *
   * @param location the location of the failure, or empty if it could not be determined
   * @return the provider-facing message
   */
  static String buildMessage(Optional<Location> location) {
    return location
        .map(
            loc ->
                BulkSubmissionConverter.CHARACTER_DECODING_ERROR_MESSAGE_WITH_LINE_TEMPLATE
                    .formatted(loc.line(), loc.character()))
        .orElse(BulkSubmissionConverter.CHARACTER_DECODING_ERROR_MESSAGE);
  }

  /**
   * Builds the provider-facing character-decoding-failure message for an uploaded file, including a
   * line/character position when it can be determined. Falls back to the location-less message if
   * the charset can't be confidently determined, or the file can't be re-read for any reason
   * (defensive; should not normally happen since {@link MultipartFile} supports being read more
   * than once).
   *
   * <p>Shared by both {@link BulkSubmissionCsvConverter} and {@link BulkSubmissionXmlConverter} so
   * the logic is not duplicated.
   *
   * @param file the uploaded file
   * @param xml whether the file is XML (enables declared-encoding sniffing)
   * @return the provider-facing message
   */
  static String characterDecodingMessage(MultipartFile file, boolean xml) {
    try {
      byte[] prefix;
      try (InputStream prefixStream = file.getInputStream()) {
        prefix = prefixStream.readNBytes(CHARSET_SNIFF_PREFIX_BYTES);
      }
      Optional<Charset> charset = detectCharset(prefix, xml);
      if (charset.isEmpty()) {
        return BulkSubmissionConverter.CHARACTER_DECODING_ERROR_MESSAGE;
      }
      try (InputStream inputStream = file.getInputStream()) {
        return buildMessage(findFailureLocation(inputStream, charset.get()));
      }
    } catch (IOException e) {
      log.debug("Unable to re-read file to determine decoding failure location", e);
      return BulkSubmissionConverter.CHARACTER_DECODING_ERROR_MESSAGE;
    }
  }
}
