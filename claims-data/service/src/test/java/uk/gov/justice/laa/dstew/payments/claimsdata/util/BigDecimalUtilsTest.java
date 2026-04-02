package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BigDecimalUtilsTest {

  @DisplayName("Scale Nullable Tests")
  @ParameterizedTest(name = "[{index}] amount={0}, scale={1} → expected={2}")
  @MethodSource("scaleNullableCases")
  void scaleNullable_shouldBehaveAsExpected(BigDecimal amount, int scale, BigDecimal expected) {
    assertThat(BigDecimalUtils.scaleNullable(amount, scale)).isEqualTo(expected);
  }

  private static Stream<Arguments> scaleNullableCases() {
    return Stream.of(
        Arguments.of(null, 2, null),
        Arguments.of(BigDecimal.ZERO, 2, BigDecimal.ZERO),
        Arguments.of(new BigDecimal("0.000"), 2, BigDecimal.ZERO),
        Arguments.of(new BigDecimal("12.345"), 2, new BigDecimal("12.35")),
        Arguments.of(new BigDecimal("12.344"), 2, new BigDecimal("12.34")));
  }

  @DisplayName("Scale Or Zero Tests")
  @ParameterizedTest(name = "[{index}] amount={0}, scale={1} → expected={2}")
  @MethodSource("scaleOrZeroCases")
  void scaleOrZero_shouldBehaveAsExpected(BigDecimal amount, int scale, BigDecimal expected) {
    assertThat(BigDecimalUtils.scaleOrZero(amount, scale)).isEqualTo(expected);
  }

  private static Stream<Arguments> scaleOrZeroCases() {
    return Stream.of(
        Arguments.of(null, 2, BigDecimal.ZERO),
        Arguments.of(BigDecimal.ZERO, 2, BigDecimal.ZERO),
        Arguments.of(new BigDecimal("0.000"), 2, BigDecimal.ZERO),
        Arguments.of(new BigDecimal("99.995"), 2, new BigDecimal("100.00")),
        Arguments.of(new BigDecimal("99.994"), 2, new BigDecimal("99.99")));
  }
}
