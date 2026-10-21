package uk.gov.justice.laa.dstew.payments.claimsdata.validator;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.BulkSubmissionFileReadException;

class BulkSubmissionOutcomeFieldValidatorTest {

  private static Stream<String> inquestFields() {
    return Stream.of(
        "INQ_CLIENT_MEANS_TESTED",
        "DECEASED_FIRST_NAME",
        "DECEASED_SURNAME",
        "DATE_OF_DEATH",
        "INQUEST_REF",
        "GOV_DEPT_1",
        "GOV_DEPT_2",
        "GOV_DEPT_3",
        "GOV_DEPT_4",
        "GOV_DEPT_5",
        "GOV_DEPT_6",
        "GOV_DEPT_7",
        "GOV_DEPT_8",
        "GOV_DEPT_9",
        "GOV_DEPT_10");
  }

  @ParameterizedTest
  @MethodSource("inquestFields")
  void rejectsInquestFieldsWhenDisabled(String field) {
    assertThatThrownBy(() -> BulkSubmissionOutcomeFieldValidator.validate(field, false))
        .isInstanceOf(BulkSubmissionFileReadException.class)
        .hasMessage(
            "The file contains an unrecognised field %s. Correct or remove the field and try again."
                .formatted(field));
  }

  @ParameterizedTest
  @MethodSource("inquestFields")
  void acceptsInquestFieldsWhenEnabled(String field) {
    assertDoesNotThrow(() -> BulkSubmissionOutcomeFieldValidator.validate(field, true));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void ignoresNonInquestFields(boolean inquestsEnabled) {
    assertDoesNotThrow(
        () -> BulkSubmissionOutcomeFieldValidator.validate("FEE_CODE", inquestsEnabled));
    assertDoesNotThrow(
        () -> BulkSubmissionOutcomeFieldValidator.validate("UNKNOWN_FIELD", inquestsEnabled));
  }
}
