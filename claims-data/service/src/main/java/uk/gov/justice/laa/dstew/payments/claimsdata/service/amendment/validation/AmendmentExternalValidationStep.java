package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.Claim;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationResult;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationSeverity;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.service.ValidationService;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentDiff;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.DiffEntry;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ValidationClaimMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.pda.PdaRequestField;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence.AmendmentDiffAssembler;

/**
 * Provider Data API (PDA) trigger/skip, call and outcome handling (DSTEW-1646, split across
 * DSTEW-1772-1774), modelled as a validation step in the amendment sequence.
 *
 * <p>Like every step it collects errors (the PDA outcome) and may enrich the {@link
 * ClaimAmendmentState}. It runs at its position in {@code STEP_ORDER} - after the fee-code gates
 * and before duplicate validation.
 *
 * <p><b>Transaction.</b> The step sequence is run with no held transaction (the orchestrator does
 * not wrap validation in one), so this external call never holds a DB connection or claim-row lock
 * open. That is exactly why PDA/FSP can sit inline with the other steps.
 */
@Component
@RequiredArgsConstructor
public class AmendmentExternalValidationStep implements ClaimAmendmentValidationStep {

  private static final String PDA_VALIDATION_STEP = "CLAIM_CATEGORY_OF_LAW";

  protected static final String[] CLAIM_VALIDATOR_CODES =
      new String[] {
        "CLAIM_SCHEMA",
        "CLAIM_CASE_DATES",
        "CLAIM_MATTER_TYPE",
        "CLAIM_STAGE_REACHED",
        "CLAIM_CLIENT_DATE_OF_BIRTH",
        "CLAIM_DISBURSEMENT_START_DATE",
        "CLAIM_DISBURSEMENTS",
        "CLAIM_DUPLICATE_CLAIM",
        "CLAIM_SCHEDULE_REFERENCE",
        "CLAIM_MANDATORY_FIELD",
        "CLAIM_OUTCOME_CODE",
        PDA_VALIDATION_STEP,
        "CLAIM_UNIQUE_FILE_NUMBER"
      };

  private final ValidationService validationService;
  private final AmendmentDiffAssembler diffAssembler;
  private final ValidationClaimMapper validationClaimMapper;

  @Override
  public List<ClaimAmendmentValidationError> validate(ClaimAmendmentState state) {

    AmendmentDiff differences = diffAssembler.assemble(state);

    Set<String> validationCodes = new LinkedHashSet<>(List.of(CLAIM_VALIDATOR_CODES));
    if (!requiresPda(differences, state.getPostAmendmentState())) {
      validationCodes.remove(PDA_VALIDATION_STEP);
    }

    Claim claim = validationClaimMapper.toValidationClaim(state.getPostAmendmentState());
    ValidationResult validationResult = validationService.validateClaim(claim, validationCodes);

    if (validationResult == null || validationResult.getIssues() == null) {
      return List.of();
    }

    return validationResult.getIssues().stream()
        .filter(issue -> issue.getSeverity() == ValidationSeverity.ERROR)
        .map(ClaimAmendmentValidationError::from)
        .toList();
  }

  /**
   * Whether the supplied diff contains any changed field that could influence the PDA request built
   * from the given merged (post-amendment) claim snapshot.
   *
   * <p>Separated into a method for testability.
   */
  private boolean requiresPda(AmendmentDiff diff, ClaimStateSnapshot mergedState) {
    if (diff == null || diff.changes() == null || mergedState == null) {
      return false;
    }
    return diff.changes().stream()
        .map(DiffEntry::fieldIdentifier)
        .anyMatch(field -> PdaRequestField.impactsPda(field, mergedState));
  }
}
