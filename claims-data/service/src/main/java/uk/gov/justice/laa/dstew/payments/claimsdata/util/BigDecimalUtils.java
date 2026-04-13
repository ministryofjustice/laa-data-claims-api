package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility methods for working with {@link java.math.BigDecimal} values in a consistent way across
 * the claims data processing services.
 *
 * <p>This class provides helper methods for scaling monetary or numeric values to a specified
 * number of decimal places using {@link java.math.RoundingMode#HALF_UP}, with explicit handling for
 * {@code null} and zero values.
 *
 * <p>The methods in this class are stateless and do not depend on any domain-specific objects,
 * making them suitable for reuse across multiple services and components.
 */
public final class BigDecimalUtils {

  /**
   * Scales the given {@link BigDecimal} to the specified number of decimal places using {@link
   * RoundingMode#HALF_UP}, with special handling for {@code null} and zero values.
   *
   * <p>Behaviour:
   *
   * <ul>
   *   <li>If {@code amount} is {@code null}, this method returns {@code null}.
   *   <li>If {@code amount} is numerically zero (e.g., 0, 0.0, 0.000), the method returns {@link
   *       BigDecimal#ZERO} without applying scaling.
   *   <li>Otherwise, the value is scaled to the specified {@code scale} using HALF_UP rounding.
   * </ul>
   *
   * @param amount the value to scale; may be {@code null}
   * @param scale the number of decimal places to apply when scaling
   * @return the scaled {@code BigDecimal}, {@code null}, or {@code BigDecimal.ZERO}
   */
  public static BigDecimal scaleNullable(BigDecimal amount, int scale) {
    if (amount == null) {
      return null;
    }
    if (amount.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    return amount.setScale(scale, RoundingMode.HALF_UP);
  }

  /**
   * Scales the given {@link BigDecimal} to the specified number of decimal places using {@link
   * RoundingMode#HALF_UP}, returning {@link BigDecimal#ZERO} when the input is {@code null} or
   * numerically zero.
   *
   * <p>Behaviour:
   *
   * <ul>
   *   <li>If {@code amount} is {@code null}, this method returns {@link BigDecimal#ZERO}.
   *   <li>If {@code amount} is numerically zero (e.g., 0, 0.0, 0.000), this method returns {@link
   *       BigDecimal#ZERO}.
   *   <li>Otherwise, the value is scaled to the specified {@code scale} using {@code
   *       RoundingMode.HALF_UP}.
   * </ul>
   *
   * @param amount the value to scale; may be {@code null}
   * @param scale the number of decimal places to apply when scaling
   * @return the scaled {@code BigDecimal}, or {@link BigDecimal#ZERO} if the input is {@code null}
   *     or numerically zero
   */
  public static BigDecimal scaleOrZero(BigDecimal amount, int scale) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    return amount.setScale(scale, RoundingMode.HALF_UP);
  }
}
