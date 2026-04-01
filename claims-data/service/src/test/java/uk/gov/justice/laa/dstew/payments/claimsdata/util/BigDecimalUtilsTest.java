package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BigDecimalUtilsTest {

  @Test
  void scaleNullable_shouldReturnNull_whenAmountIsNull() {
    assertThat(BigDecimalUtils.scaleNullable(null, 2)).isNull();
  }

  @Test
  void scaleNullable_shouldReturnZero_whenAmountIsZero() {
    BigDecimal result = BigDecimalUtils.scaleNullable(BigDecimal.ZERO, 2);

    assertThat(result).isEqualTo(BigDecimal.ZERO);
  }

  @Test
  void scaleNullable_shouldReturnZero_whenAmountIsZeroWithDifferentScale() {
    BigDecimal result = BigDecimalUtils.scaleNullable(new BigDecimal("0.000"), 2);

    assertThat(result).isEqualTo(BigDecimal.ZERO);
  }

  @Test
  void scaleNullable_shouldScaleAmount_whenAmountIsNonZero() {
    BigDecimal result = BigDecimalUtils.scaleNullable(new BigDecimal("12.345"), 2);

    assertThat(result).isEqualTo(new BigDecimal("12.35"));
  }

  @Test
  void scaleNullable_shouldRoundHalfUp_whenRequired() {
    BigDecimal result = BigDecimalUtils.scaleNullable(new BigDecimal("12.344"), 2);

    assertThat(result).isEqualTo(new BigDecimal("12.34"));
  }

  @Test
  void scaleOrZero_shouldReturnZero_whenAmountIsNull() {
    BigDecimal result = BigDecimalUtils.scaleOrZero(null, 2);

    assertThat(result).isEqualTo(BigDecimal.ZERO);
  }

  @Test
  void scaleOrZero_shouldReturnZero_whenAmountIsZero() {
    BigDecimal result = BigDecimalUtils.scaleOrZero(BigDecimal.ZERO, 2);

    assertThat(result).isEqualTo(BigDecimal.ZERO);
  }

  @Test
  void scaleOrZero_shouldReturnZero_whenAmountIsZeroWithDifferentScale() {
    BigDecimal result = BigDecimalUtils.scaleOrZero(new BigDecimal("0.000"), 2);

    assertThat(result).isEqualTo((BigDecimal.ZERO));
  }

  @Test
  void scaleOrZero_shouldScaleAmount_whenAmountIsNonZero() {
    BigDecimal result = BigDecimalUtils.scaleOrZero(new BigDecimal("99.995"), 2);

    assertThat(result).isEqualTo(new BigDecimal("100.00"));
  }

  @Test
  void scaleOrZero_shouldRoundHalfUp_whenRequired() {
    BigDecimal result = BigDecimalUtils.scaleOrZero(new BigDecimal("99.994"), 2);

    assertThat(result).isEqualTo(new BigDecimal("99.99"));
  }
}
