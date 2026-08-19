package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentDiff;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentFieldIdentifiers.ClaimFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentFieldIdentifiers.ClaimSummaryFeeFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ChangeSource;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.DiffEntry;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ValidationSeverity;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence.AmendmentDiffAssembler;

/**
 * Tests for {@link FieldAmendabilityValidationStep}, the field amendability gate.
 *
 * <p>The step is a pure function over the assembled diff and the before-state area of law: the
 * {@link AmendmentDiffAssembler} is stubbed so each test drives an exact set of changed fields. The
 * step makes no external calls and persists nothing, so the "no write on rejection" guarantee holds
 * by construction and is proven at the pipeline level elsewhere.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FieldAmendabilityValidationStep Tests")
class FieldAmendabilityValidationStepTest {

  @Mock private AmendmentDiffAssembler diffAssembler;

  private FieldAmendabilityValidationStep step;

  private static final String NOT_AMENDABLE_CODE =
      ClaimAmendmentValidationCode.INVALID_FIELD_NOT_AMENDABLE_FOR_AREA_OF_LAW.toString();

  private ClaimAmendmentState stateWith(AreaOfLaw areaOfLaw, DiffEntry... changes) {
    step = new FieldAmendabilityValidationStep(diffAssembler);
    ClaimAmendmentState state =
        ClaimAmendmentState.builder()
            .beforeState(ClaimStateSnapshot.builder().areaOfLaw(areaOfLaw).build())
            .build();
    when(diffAssembler.assemble(any())).thenReturn(AmendmentDiff.of(List.of(changes)));
    return state;
  }

  private static DiffEntry requested(String fieldIdentifier) {
    return new DiffEntry(fieldIdentifier, ChangeSource.REQUESTED, "before", "after");
  }

  @Test
  @DisplayName("a non-amendable changed field collects the not-amendable error for that field")
  void nonAmendableFieldCollectsError() {
    // Scheme ID is amendable for Crime Lower only, so it is not amendable for Legal Help.
    ClaimAmendmentState state = stateWith(AreaOfLaw.LEGAL_HELP, requested(ClaimFields.SCHEME_ID));

    List<ClaimAmendmentValidationError> errors = step.validate(state);

    assertThat(errors).hasSize(1);
    ClaimAmendmentValidationError error = errors.getFirst();
    assertThat(error.getCode()).isEqualTo(NOT_AMENDABLE_CODE);
    assertThat(error.getMessage())
        .contains(ClaimFields.SCHEME_ID)
        .contains(AreaOfLaw.LEGAL_HELP.toString());
  }

  @Test
  @DisplayName("a field amendable for the claim's area of law produces no error")
  void amendableFieldProducesNoError() {
    // Fee Code is amendable for every area of law.
    ClaimAmendmentState state = stateWith(AreaOfLaw.CRIME_LOWER, requested(ClaimFields.FEE_CODE));

    assertThat(step.validate(state)).isEmpty();
  }

  @Test
  @DisplayName("a changed field absent from the registry is never amendable and is gated")
  void unregisteredFieldIsGated() {
    // line_number is tracked by the change detector but is not a provider-amendable field.
    ClaimAmendmentState state =
        stateWith(AreaOfLaw.CRIME_LOWER, requested(ClaimFields.LINE_NUMBER));

    List<ClaimAmendmentValidationError> errors = step.validate(state);

    assertThat(errors).hasSize(1);
    assertThat(errors.getFirst().getCode()).isEqualTo(NOT_AMENDABLE_CODE);
  }

  @Test
  @DisplayName("the not-amendable error is non-fatal so it aggregates with later findings")
  void notAmendableErrorIsNonFatal() {
    ClaimAmendmentState state = stateWith(AreaOfLaw.MEDIATION, requested(ClaimFields.SCHEME_ID));

    ClaimAmendmentValidationError error = step.validate(state).getFirst();

    assertThat(error.isFatal()).isFalse();
    assertThat(error.getSeverity()).isEqualTo(ValidationSeverity.ERROR);
  }

  @Test
  @DisplayName("only the non-amendable fields are reported when some changed fields are amendable")
  void reportsOnlyNonAmendableFields() {
    ClaimAmendmentState state =
        stateWith(
            AreaOfLaw.LEGAL_HELP,
            requested(ClaimFields.FEE_CODE), // amendable everywhere
            requested(ClaimFields.SCHEME_ID), // crime lower only
            requested(ClaimSummaryFeeFields.NET_WAITING_COSTS_AMOUNT)); // crime lower only

    List<ClaimAmendmentValidationError> errors = step.validate(state);

    assertThat(errors)
        .extracting(ClaimAmendmentValidationError::getMessage)
        .hasSize(2)
        .anySatisfy(message -> assertThat(message).contains(ClaimFields.SCHEME_ID))
        .anySatisfy(
            message ->
                assertThat(message).contains(ClaimSummaryFeeFields.NET_WAITING_COSTS_AMOUNT));
  }

  @Test
  @DisplayName("FSP-sourced changes are not gated, even for non-amendable fields")
  void fspSourcedChangesAreIgnored() {
    // Scheme ID is not amendable for Legal Help, but an FSP-sourced change is out of the gate's
    // scope (only provider-requested changes are policed).
    DiffEntry fspChange = new DiffEntry(ClaimFields.SCHEME_ID, ChangeSource.FSP, "before", "after");
    ClaimAmendmentState state = stateWith(AreaOfLaw.LEGAL_HELP, fspChange);

    assertThat(step.validate(state)).isEmpty();
  }

  @Test
  @DisplayName("no changed fields produces no error")
  void emptyDiffProducesNoError() {
    ClaimAmendmentState state = stateWith(AreaOfLaw.CRIME_LOWER);

    assertThat(step.validate(state)).isEmpty();
  }

  @Test
  @DisplayName("a missing area of law makes every requested changed field non-amendable")
  void missingAreaOfLawRejectsAllChangedFields() {
    ClaimAmendmentState state =
        stateWith(null, requested(ClaimFields.FEE_CODE), requested(ClaimFields.SCHEME_ID));

    List<ClaimAmendmentValidationError> errors = step.validate(state);

    assertThat(errors)
        .hasSize(2)
        .allSatisfy(error -> assertThat(error.getCode()).isEqualTo(NOT_AMENDABLE_CODE));
  }

  @Test
  @DisplayName("each non-amendable field is attributed to its own field in a distinct error")
  void eachNonAmendableFieldAttributedIndividually() {
    ClaimAmendmentState state =
        stateWith(
            AreaOfLaw.MEDIATION,
            requested(ClaimFields.SCHEME_ID),
            requested(ClaimFields.REPRESENTATION_ORDER_DATE));

    assertThat(step.validate(state))
        .extracting(ClaimAmendmentValidationError::getMessage)
        .anySatisfy(message -> assertThat(message).contains(ClaimFields.SCHEME_ID))
        .anySatisfy(message -> assertThat(message).contains(ClaimFields.REPRESENTATION_ORDER_DATE));
  }
}
