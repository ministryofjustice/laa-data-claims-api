package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Uuid7")
class Uuid7Test {
  @Test
  @DisplayName("timeBasedUuid generates a version 7 UUID")
  void timeBasedUuidGeneratesVersion7() {
    UUID uuid = Uuid7.timeBasedUuid();
    assertThat(uuid).isNotNull();
    assertThat(uuid.version()).isEqualTo(7);
  }

  @Test
  @DisplayName("timeBasedUuid generates unique values")
  void timeBasedUuidGeneratesUniqueValues() {
    assertThat(Uuid7.timeBasedUuid()).isNotEqualTo(Uuid7.timeBasedUuid());
  }

  @Test
  @DisplayName("the constructor cannot be invoked")
  void constructorCannotBeInvoked() throws Exception {
    Constructor<Uuid7> constructor = Uuid7.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    assertThatThrownBy(constructor::newInstance).hasCauseInstanceOf(IllegalStateException.class);
  }

  @ParameterizedTest
  @DisplayName("isValidUuid accepts canonical UUID strings")
  @ValueSource(
      strings = {
        "0190b6a0-9b7e-7c8a-9e2d-2f3a4b5c6d7e",
        "00000000-0000-0000-0000-000000000000",
        "FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF",
        "123e4567-e89b-12d3-a456-426614174000"
      })
  void isValidUuidAcceptsCanonical(String value) {
    assertThat(Uuid7.isValidUuid(value)).isTrue();
  }

  @ParameterizedTest
  @DisplayName("isValidUuid rejects null, blank, lenient and malformed values")
  @NullSource
  @ValueSource(
      strings = {
        "",
        "   ",
        "not-a-uuid",
        "1-1-1-1-1",
        "a-b-c-d-e",
        "0190b6a0-9b7e-7c8a-9e2d-2f3a4b5c6d7",
        "0190b6a0-9b7e-7c8a-9e2d-2f3a4b5c6d7ee",
        "0190b6a0-9b7e-7c8a-9e2d-2f3a4b5c6d7g",
        "0190b6a09b7e7c8a9e2d2f3a4b5c6d7e"
      })
  void isValidUuidRejectsInvalid(String value) {
    assertThat(Uuid7.isValidUuid(value)).isFalse();
  }
}
