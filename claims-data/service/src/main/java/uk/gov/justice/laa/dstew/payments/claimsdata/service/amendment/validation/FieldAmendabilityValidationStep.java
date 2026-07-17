package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentDiff;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ChangeSource;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.DiffEntry;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence.AmendmentDiffAssembler;

/**
 * Field amendability gate (DSTEW-1593).
 *
 * <p>For every provider-requested changed field, checks it is amendable for the claim's {@link
 * AreaOfLaw} against the {@link AmendableClaimFields} registry (derived from the signed-off AaBC
 * artefact <em>"Amend MVP - fields for amendment"</em>). Each non-amendable changed field yields a
 * {@link ClaimAmendmentValidationCode#INVALID_FIELD_NOT_AMENDABLE_FOR_AREA_OF_LAW} error whose
 * message names the offending field so the failure is attributed to that field.
 *
 * <p>Only {@link ChangeSource#REQUESTED} diff entries are gated: FSP-sourced consequence changes
 * are not provider field edits and so are out of scope for amendability. An absent/unknown area of
 * law makes every changed field non-amendable, which the {@link AmendableClaimFields} registry
 * enforces.
 *
 * <p>The errors are non-fatal so they are collected alongside every other step's findings for the
 * downstream aggregation (Step 12); because any non-empty error set fails the amendment, a
 * non-amendable field prevents persistence of any claim update, amendment record, calculated-fee
 * row or event.
 */
@Component
@RequiredArgsConstructor
public class FieldAmendabilityValidationStep implements ClaimAmendmentValidationStep {

  private final AmendmentDiffAssembler diffAssembler;

  @Override
  public List<ClaimAmendmentValidationError> validate(ClaimAmendmentState state) {
    ClaimStateSnapshot beforeState = state.getBeforeState();
    AreaOfLaw areaOfLaw = beforeState == null ? null : beforeState.getAreaOfLaw();

    AmendmentDiff diff = diffAssembler.assemble(state);

    return diff.changes().stream()
        .filter(change -> change.changeSource() == ChangeSource.REQUESTED)
        .filter(change -> !AmendableClaimFields.isAmendable(change.fieldIdentifier(), areaOfLaw))
        .map(change -> notAmendableError(change, areaOfLaw))
        .toList();
  }

  private static ClaimAmendmentValidationError notAmendableError(
      DiffEntry change, AreaOfLaw areaOfLaw) {
    return ClaimAmendmentValidationError.of(
        ClaimAmendmentValidationCode.INVALID_FIELD_NOT_AMENDABLE_FOR_AREA_OF_LAW,
        change.fieldIdentifier(),
        areaOfLaw);
  }
}
