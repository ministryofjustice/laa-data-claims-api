package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.slf4j.LoggerFactory;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;

class ClaimVersionValidationStepTest {

  private static final UUID CLAIM_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

  private ClaimVersionValidationStep step;
  private ListAppender<ILoggingEvent> logAppender;
  private Logger stepLogger;

  @BeforeEach
  void setUp() {
    step = new ClaimVersionValidationStep();

    stepLogger = (Logger) LoggerFactory.getLogger(ClaimVersionValidationStep.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    stepLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    stepLogger.detachAppender(logAppender);
  }

  /**
   * Builds a realistic amendment state with a concrete before-state version and a presence-aware
   * submitted version (an undefined {@link JsonNullable} models "client sent no version").
   */
  private static ClaimAmendmentState stateWith(Long currentVersion, JsonNullable<Long> submitted) {
    ClaimStateSnapshot before =
        ClaimStateSnapshot.builder().claimId(CLAIM_ID).version(currentVersion).build();
    ClaimAmendmentPayload payload = ClaimAmendmentPayload.builder().version(submitted).build();
    return ClaimAmendmentState.builder().beforeState(before).requestPayload(payload).build();
  }

  @Test
  @DisplayName("validate returns INVALID_NULL_VERSION when state object is null")
  void shouldReturnErrorWhenStateIsNull() {
    List<ClaimAmendmentValidationError> errors = step.validate(null);

    assertThat(errors).hasSize(1);
    assertThat(errors.getFirst().getCode())
        .isEqualTo(ClaimAmendmentValidationCode.INVALID_NULL_VERSION.toString());
  }

  @Test
  @DisplayName("validate returns INVALID_NULL_VERSION when database snapshot is null")
  void shouldReturnErrorWhenBeforeStateIsNull() {
    ClaimAmendmentState state = mock(ClaimAmendmentState.class);
    when(state.getBeforeState()).thenReturn(null);

    List<ClaimAmendmentValidationError> errors = step.validate(state);

    assertThat(errors).hasSize(1);
    assertThat(errors.getFirst().getCode())
        .isEqualTo(ClaimAmendmentValidationCode.INVALID_NULL_VERSION.toString());
  }

  @Test
  @DisplayName("validate returns INVALID_NULL_VERSION when request payload is null")
  void shouldReturnErrorWhenRequestPayloadIsNull() {
    ClaimAmendmentState state = mock(ClaimAmendmentState.class, RETURNS_DEEP_STUBS);
    when(state.getRequestPayload()).thenReturn(null);

    List<ClaimAmendmentValidationError> errors = step.validate(state);

    assertThat(errors).hasSize(1);
    assertThat(errors.getFirst().getCode())
        .isEqualTo(ClaimAmendmentValidationCode.INVALID_NULL_VERSION.toString());
  }

  @Test
  @DisplayName("validate returns empty list when expected and received versions match")
  void shouldReturnEmptyListWhenVersionsMatch() {
    assertThat(step.validate(stateWith(5L, JsonNullable.of(5L)))).isEmpty();
  }

  @Test
  @DisplayName("validate returns empty list at the version boundary (both zero)")
  void shouldReturnEmptyListWhenVersionsMatchAtZeroBoundary() {
    assertThat(step.validate(stateWith(0L, JsonNullable.of(0L)))).isEmpty();
  }

  @Test
  @DisplayName("validate returns CLAIM_VERSION_CONFLICT when versions do not match")
  void shouldReturnErrorWhenVersionsMismatch() {
    List<ClaimAmendmentValidationError> errors = step.validate(stateWith(5L, JsonNullable.of(4L)));

    assertThat(errors).hasSize(1);
    ClaimAmendmentValidationError error = errors.getFirst();
    assertThat(error.getCode())
        .isEqualTo(ClaimAmendmentValidationCode.CLAIM_VERSION_CONFLICT.toString())
        .isEqualTo("CLAIM_VERSION_CONFLICT");
    assertThat(error.getHttpStatus().value()).isEqualTo(409);
    assertThat(error.isFatal()).isTrue();
  }

  @Test
  @DisplayName("validate detects a conflict when the submitted version is exactly one behind")
  void shouldReturnConflictWhenSubmittedVersionOneBehind() {
    List<ClaimAmendmentValidationError> errors = step.validate(stateWith(10L, JsonNullable.of(9L)));

    assertThat(errors).hasSize(1);
    assertThat(errors.getFirst().getCode()).isEqualTo("CLAIM_VERSION_CONFLICT");
  }

  @Test
  @DisplayName("validate detects a conflict when the submitted version is ahead of the stored one")
  void shouldReturnConflictWhenSubmittedVersionAhead() {
    List<ClaimAmendmentValidationError> errors = step.validate(stateWith(3L, JsonNullable.of(4L)));

    assertThat(errors).hasSize(1);
    assertThat(errors.getFirst().getCode()).isEqualTo("CLAIM_VERSION_CONFLICT");
  }

  @Test
  @DisplayName(
      "a version conflict logs a WARN with safe structured fields (event, claim id, versions, "
          + "conflict point) at the initial_check point")
  void shouldLogWarnWithSafeStructuredFieldsOnConflict() {
    step.validate(stateWith(5L, JsonNullable.of(4L)));

    List<ILoggingEvent> warnEvents =
        logAppender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
    assertThat(warnEvents).hasSize(1);
    String formatted = warnEvents.getFirst().getFormattedMessage();

    assertThat(formatted).contains("event=CLAIM_VERSION_CONFLICT");
    assertThat(formatted).contains("claimId=" + CLAIM_ID);
    assertThat(formatted).contains("submittedClaimVersion=4");
    assertThat(formatted).contains("currentClaimVersion=5");
    assertThat(formatted).contains("conflictPoint=initial_check");
  }

  @Test
  @DisplayName("the conflict WARN log never contains amendment payload values or financial details")
  void shouldNotLogAmendmentPayloadOrFinancialDetails() {
    BigDecimal sentinelFinancialValue = new BigDecimal("123456.78");
    String sentinelCaseReference = "SENSITIVE-CASE-REF-9999";
    ClaimStateSnapshot before =
        ClaimStateSnapshot.builder()
            .claimId(CLAIM_ID)
            .version(5L)
            .netProfitCostsAmount(sentinelFinancialValue)
            .caseReferenceNumber(sentinelCaseReference)
            .build();
    ClaimAmendmentPayload payload =
        ClaimAmendmentPayload.builder()
            .version(JsonNullable.of(4L))
            .caseReferenceNumber(JsonNullable.of(sentinelCaseReference))
            .build();
    ClaimAmendmentState state =
        ClaimAmendmentState.builder().beforeState(before).requestPayload(payload).build();

    step.validate(state);

    String formatted =
        logAppender.list.stream()
            .filter(e -> e.getLevel() == Level.WARN)
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (a, b) -> a + b);

    assertThat(formatted).doesNotContain(sentinelFinancialValue.toPlainString());
    assertThat(formatted).doesNotContain(sentinelCaseReference);
  }

  @Test
  @DisplayName("validate returns INVALID_NULL_VERSION when submitted version is undefined")
  void shouldReturnErrorWhenSubmittedVersionIsUndefined() {
    List<ClaimAmendmentValidationError> errors =
        step.validate(stateWith(5L, JsonNullable.undefined()));

    assertThat(errors).hasSize(1);
    assertThat(errors.getFirst().getCode())
        .isEqualTo(ClaimAmendmentValidationCode.INVALID_NULL_VERSION.toString());
  }

  @Test
  @DisplayName("validate returns INVALID_NULL_VERSION when submitted version is an explicit null")
  void shouldReturnErrorWhenSubmittedVersionIsNull() {
    List<ClaimAmendmentValidationError> errors =
        step.validate(stateWith(5L, JsonNullable.of(null)));

    assertThat(errors).hasSize(1);
    assertThat(errors.getFirst().getCode())
        .isEqualTo(ClaimAmendmentValidationCode.INVALID_NULL_VERSION.toString());
  }

  @Test
  @DisplayName(
      "validate flags a conflict (without NPE) when the stored version is null but a version is "
          + "submitted")
  void shouldReturnConflictWithoutNpeWhenStoredVersionIsNull() {
    // Defensive: the stored version is NOT NULL in the schema, but the step must not NPE if it is
    // ever absent - it cannot confirm the submitted version matches, so it rejects as a conflict.
    List<ClaimAmendmentValidationError> errors =
        step.validate(stateWith(null, JsonNullable.of(1L)));

    assertThat(errors).hasSize(1);
    assertThat(errors.getFirst().getCode()).isEqualTo("CLAIM_VERSION_CONFLICT");
  }

  @Test
  @DisplayName(
      "validate detects a conflict for large versions that would collide under int truncation")
  void shouldDetectConflictForLargeVersionsBeyondIntRange() {
    // 2^32 and 0 share the same 32-bit int value; a naive intValue() comparison would wrongly treat
    // these distinct versions as a match. Full Long equality must flag the conflict.
    long twoToThe32 = 4_294_967_296L;
    List<ClaimAmendmentValidationError> errors =
        step.validate(stateWith(twoToThe32, JsonNullable.of(0L)));

    assertThat(errors).hasSize(1);
    assertThat(errors.getFirst().getCode()).isEqualTo("CLAIM_VERSION_CONFLICT");
  }

  @Test
  @DisplayName("validate treats large equal versions as a match (no false conflict)")
  void shouldMatchLargeEqualVersions() {
    long largeVersion = 9_000_000_000L;
    assertThat(step.validate(stateWith(largeVersion, JsonNullable.of(largeVersion)))).isEmpty();
  }
}
