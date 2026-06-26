package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import com.fasterxml.uuid.Generators;
import java.util.UUID;
import java.util.regex.Pattern;

/** This is used to generate UUIDs version 7 based on timestamp values. */
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
public final class Uuid7 {

  /** Canonical (RFC 4122) 8-4-4-4-12 hexadecimal UUID form. */
  private static final Pattern UUID_PATTERN =
      Pattern.compile(
          "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

  private Uuid7() {
    throw new IllegalStateException("Cannot instantiate Uuid7 class");
  }

  public static UUID timeBasedUuid() {
    return Generators.timeBasedEpochGenerator().generate();
  }

  /**
   * Checks whether the supplied value is a structurally valid UUID in the canonical (RFC 4122)
   * 8-4-4-4-12 hexadecimal form.
   *
   * <p>Unlike {@link UUID#fromString(String)}, this performs a strict check and rejects malformed,
   * non-canonical inputs (e.g. {@code "1-1-1-1-1"}) that the lenient parser would otherwise coerce
   * into a valid {@link UUID} object.
   *
   * @param value the value to validate; may be {@code null}
   * @return {@code true} if the value is a strictly valid canonical UUID; {@code false} otherwise
   */
  public static boolean isValidUuid(String value) {
    return value != null && UUID_PATTERN.matcher(value).matches();
  }
}
