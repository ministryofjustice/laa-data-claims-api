package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Uuid7 utility tests")
class Uuid7Test {

  @Test
  @DisplayName("timeBasedUuid returns a non-null UUID")
  void timeBasedUuid_returnsNonNull() {
    UUID id = Uuid7.timeBasedUuid();
    assertThat(id).isNotNull();
  }

  @Test
  @DisplayName("private constructor throws IllegalStateException and is covered")
  void privateConstructor_throwsIllegalStateException() throws Exception {
    Constructor<Uuid7> ctor = Uuid7.class.getDeclaredConstructor();
    ctor.setAccessible(true);
    try {
      ctor.newInstance();
      org.junit.jupiter.api.Assertions.fail("Expected IllegalStateException");
    } catch (InvocationTargetException e) {
      assertThat(e.getCause()).isInstanceOf(IllegalStateException.class);
    }
  }
}
