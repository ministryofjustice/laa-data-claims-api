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
 * External/third-party validation step used during claim amendment processing.
 *
 * <p>Primary responsibilities:
 *
 * <ul>
 *   <li>Map the in-memory post-amendment claim snapshot to the validation model via {@link
 *       ValidationClaimMapper}.
 *   <li>Invoke the external {@link ValidationService#validateClaim(Claim, Set)} to run a scoped set
 *       of validators and collect the resulting issues.
 *   <li>Translate returned validation issues into {@link ClaimAmendmentValidationError} objects and
 *       return only error-severity issues to the amendment orchestrator.
 * </ul>
 *
 * <p>Scope of validation: this step builds a deterministic ordered set of validator codes (see
 * {@link #CLAIM_VALIDATOR_CODES}) which is passed to {@code validateClaim} as the {@code
 * validationCodes} parameter. The ValidationService implementation lives in a separate library and
 * uses this set to decide which specific validation rules (validators) should be executed for the
 * supplied claim. In short, {@code validationCodes} acts as the "scope" or "whitelist" of
 * validators to run for this invocation.
 *
 * <p>PDA (Provider Data API) behaviour: one of the validators in the default set is the {@code
 * CLAIM_CATEGORY_OF_LAW} validator (named via {@link #PDA_VALIDATION_STEP}). Running that validator
 * will cause the system to build and possibly send a PDA request. To avoid unnecessary PDA work for
 * amendments that do not change any fields that influence the PDA request, this step will remove
 * {@code CLAIM_CATEGORY_OF_LAW} from the requested validator set when {@link
 * #requiresPda(AmendmentDiff, ClaimStateSnapshot)} returns {@code false}.
 *
 * <p>Execution ordering and transactionality: this step is part of the amendment validation
 * sequence and therefore executes at its configured position. The overall sequence is run without
 * an open database transaction, so the external validation call does not hold a DB connection or
 * claim-row lock. That design allows external checks (PDA/FSP) to be performed inline without
 * risking long-lived DB locks.

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
   * Determine whether Provider Data API (PDA) validation should be executed for this amendment.
   *
   * <p>Implementation notes:
   *
   * <ul>
   *   <li>Returns {@code false} if {@code diff}, {@code diff.changes()} or {@code mergedState} are
   *       {@code null}.
   *   <li>Otherwise it iterates the changed diff entries and asks {@link PdaRequestField} whether
   *       the field identifier "impacts PDA" for the supplied merged claim snapshot. If any field
   *       can influence the built PDA request, we must include the PDA validator in the validation
   *       scope.
   * </ul>
   *
   * <p>Extracted as a separate method for readability and to allow focused unit testing of the PDA
   * gating logic without invoking the external validation service.
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
