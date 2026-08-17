package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BddStepFailures")
class BddStepFailuresTest {

  @Test
  @DisplayName("prefixes wrapped failures with the standard BDD step message format")
  void prefixesWrappedFailuresWithStandardBddStepMessageFormat() {
    AssertionError thrown =
        catchThrowableOfType(
            AssertionError.class,
            () ->
                BddStepFailures.step(
                    "Requesting amendment reference data for claim=123",
                    () -> {
                      throw new IOException("reference data service unavailable");
                    }));

    assertThat(thrown)
        .hasMessage(
            "[BDD step failed] Requesting amendment reference data for claim=123"
                + " — reference data service unavailable");
  }

  @Test
  @DisplayName("preserves the original AssertionError message when rewrapping")
  void preservesOriginalAssertionErrorMessageWhenRewrapping() {
    AssertionError original = new AssertionError("expected status 200 but was 503");

    AssertionError thrown =
        catchThrowableOfType(
            AssertionError.class,
            () ->
                BddStepFailures.step(
                    "Verifying amendment response for claim=123",
                    () -> {
                      throw original;
                    }));

    assertThat(thrown)
        .hasMessage(
            "[BDD step failed] Verifying amendment response for claim=123"
                + " — expected status 200 but was 503");
  }

  @Test
  @DisplayName("chains the original throwable as the cause")
  void chainsOriginalThrowableAsCause() {
    IllegalStateException original = new IllegalStateException("malformed amendment payload");

    AssertionError thrown =
        catchThrowableOfType(
            AssertionError.class,
            () ->
                BddStepFailures.step(
                    "Parsing amendment payload for claim=123",
                    () -> {
                      throw original;
                    }));

    assertThat(thrown.getCause()).isSameAs(original);
  }

  @Test
  @DisplayName("falls back to the throwable type when the original message is null")
  void fallsBackToThrowableTypeWhenOriginalMessageIsNull() {
    AssertionError original = new AssertionError((String) null);

    AssertionError thrown =
        catchThrowableOfType(
            AssertionError.class,
            () ->
                BddStepFailures.step(
                    "Verifying amendment validation outcome for claim=123",
                    () -> {
                      throw original;
                    }));

    assertThat(thrown)
        .hasMessage(
            "[BDD step failed] Verifying amendment validation outcome for claim=123"
                + " — AssertionError");
    assertThat(thrown.getCause()).isSameAs(original);
  }
}
