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
   *   <li>Otherwise, the value is scaled to the specified {@code scale} using HALF_UP rounding.
   * </ul>
   *
   * @param amount the value to scale; may be {@code null}
   * @param scale the number of decimal places to apply when scaling
   * @return the scaled {@code BigDecimal}, {@code null}
   */
  public static BigDecimal scaleNullable(BigDecimal amount, int scale) {

    return amount == null ? null : amount.setScale(scale, RoundingMode.HALF_UP);
  }

  /**
   * Scales the given {@link BigDecimal} to the specified number of decimal places using {@link
   * RoundingMode#HALF_UP}. If the input is {@code null} or numerically zero, a zero {@code
   * BigDecimal} scaled to the requested number of decimal places is returned (for example, {@code
   * 0.00} when {@code scale} is {@code 2}).
   *
   * <p>Behaviour:
   *
   * <ul>
   *   <li>If {@code amount} is {@code null}, this method returns a zero value with the given {@code
   *       scale}.
   *   <li>If {@code amount} is numerically zero (e.g. {@code 0}, {@code 0.0}, {@code 0.000}), this
   *       method returns a zero value with the given {@code scale}.
   *   <li>Otherwise, the value is scaled to {@code scale} decimal places using {@link
   *       RoundingMode#HALF_UP}.
   * </ul>
   *
   * @param amount the value to scale; may be {@code null}
   * @param scale the number of decimal places to apply
   * @return a {@code BigDecimal} scaled to {@code scale}; zero with the given scale if the input is
   *     {@code null} or numerically zero
   */
  public static BigDecimal scaleOrZeroWithScale(BigDecimal amount, int scale) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
    }
    return amount.setScale(scale, RoundingMode.HALF_UP);
  }
}
