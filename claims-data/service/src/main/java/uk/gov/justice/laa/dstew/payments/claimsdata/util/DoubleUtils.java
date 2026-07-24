package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import java.math.BigDecimal;

/**
 * Utility methods for working with {@link Double} values and performing safe numeric conversions
 * across the claims data processing services.
 *
 * <p>This class provides helper methods for converting nullable numeric types (such as {@link
 * BigDecimal} and {@link Integer}) into standard {@link Double} values with explicit {@code null}
 * safety.
 *
 * <p>The methods in this class are stateless and do not depend on any domain-specific objects,
 * making them suitable for reuse across multiple services, builders, and components.
 */
public final class DoubleUtils {

  private DoubleUtils() {
    // Private constructor to prevent instantiation of utility class
  }

  /**
   * Safely converts the given {@link BigDecimal} to a {@link Double}.
   *
   * <p>Behaviour:
   *
   * <ul>
   *   <li>If {@code value} is {@code null}, this method returns {@code null}.
   *   <li>Otherwise, it delegates to {@link BigDecimal#doubleValue()}.
   * </ul>
   *
   * @param value the {@link BigDecimal} value to convert; may be {@code null}
   * @return the resulting {@link Double}, or {@code null} if the input is {@code null}
   */
  public static Double toDouble(BigDecimal value) {
    return value == null ? null : value.doubleValue();
  }

  /**
   * Safely converts the given {@link Integer} to a {@link Double}.
   *
   * <p>Behaviour:
   *
   * <ul>
   *   <li>If {@code value} is {@code null}, this method returns {@code null}.
   *   <li>Otherwise, it delegates to {@link Integer#doubleValue()}.
   * </ul>
   *
   * @param value the {@link Integer} value to convert; may be {@code null}
   * @return the resulting {@link Double}, or {@code null} if the input is {@code null}
   */
  public static Double toDouble(Integer value) {
    return value == null ? null : value.doubleValue();
  }
}
