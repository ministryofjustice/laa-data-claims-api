package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import uk.gov.justice.laa.dstew.payments.claimsdata.dto.ClaimSearchRequest;

/**
 * Utility for normalising data in DTOs before processing. This is intended to be a single place for
 * simple, reusable data normalisation routines that can be applied to incoming DTOs to ensure
 * consistent handling of common issues like leading/trailing whitespace and empty strings. By
 * normalising data early, we can simplify downstream processing and reduce the risk of bugs caused
 * by unexpected input formats.
 *
 * <p>Notes:
 *
 * <ul>
 *   <li>Methods are intentionally minimal and side-effecting (they mutate the input DTO).
 *   <li>This class is thread-safe because it has no state; callers should not share mutable DTO
 *       instances between threads without their own synchronization.
 *   <li>Behaviour is intentionally small and explicit: trimming + converting whitespace-only
 *       strings to {@code null} for specific fields.
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>
 *   ClaimSearchRequest req = ...;
 *   DataNormaliser.normaliseClaimSearchRequest(req);
 *   // now req.getCaseReferenceNumber() is trimmed or null
 * </pre>
 */
public final class DataNormaliser {

  // Private constructor to prevent instantiation of this utility class.
  private DataNormaliser() {
    throw new UnsupportedOperationException(
        "DataNormaliser is a utility class and cannot be instantiated");
  }

  /**
   * Normalises the {@code ClaimSearchRequest} object by updating specific fields converting an
   * empty/whitespace-only value to {@code null}.
   *
   * <p>This method mutates the supplied {@code request} in-place. If {@code request} is {@code
   * null} the method is a no-op.
   *
   * @param request the request to normalise (may be mutated)
   */
  public static void normaliseClaimSearchRequest(ClaimSearchRequest request) {
    if (request == null) {
      return;
    }
    request.setCaseReferenceNumber(trimToNull(request.getCaseReferenceNumber()));
  }

  /**
   * Trim the supplied string and convert an empty result to {@code null}.
   *
   * <p>This is a small helper used by normalisation routines to convert whitespace-only values to
   * {@code null} so downstream code can treat missing/empty values uniformly.
   *
   * @param s the string to trim, may be {@code null}
   * @return the trimmed string, or {@code null} if the input was {@code null} or only whitespace
   */
  public static String trimToNull(String s) {
    if (s == null) {
      return null;
    }
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }
}
