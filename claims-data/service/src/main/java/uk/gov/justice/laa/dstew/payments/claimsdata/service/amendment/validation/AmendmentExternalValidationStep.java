package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.Claim;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ClaimValidationResult;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ResolvedClaimData;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationSeverity;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.service.ValidationService;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.validator.claim.ClaimValidatorCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentDiff;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentFieldIdentifiers.ClaimFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.DiffEntry;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ValidationClaimMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
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

  private static final ClaimValidatorCode PDA_VALIDATION_STEP =
      ClaimValidatorCode.CLAIM_CATEGORY_OF_LAW_VALIDATOR;

  protected static final ClaimValidatorCode[] CLAIM_VALIDATOR_CODES = ClaimValidatorCode.values();

  private final ValidationService validationService;
  private final AmendmentDiffAssembler diffAssembler;
  private final ValidationClaimMapper validationClaimMapper;

  @Override
  public List<ClaimAmendmentValidationError> validate(ClaimAmendmentState state) {

    AmendmentDiff differences = diffAssembler.assemble(state);

    Set<ClaimValidatorCode> validationCodes = new LinkedHashSet<>(List.of(CLAIM_VALIDATOR_CODES));
    if (!requiresPda(differences, state.getPostAmendmentState())) {
      validationCodes.remove(PDA_VALIDATION_STEP);
    }

    Claim claim = validationClaimMapper.toValidationClaim(state.getPostAmendmentState());
    ClaimValidationResult validationResult =
        validationService.validateClaim(claim, validationCodes);

    // Terminal gate: a fee code may only change to another fee code within the same Area of Law.
    // The Area of Law of the (new) fee code is resolved by the reusable validation package during
    // the call above and surfaced on the result's resolvedData, so no additional lookup is made.
    ClaimAmendmentValidationError areaOfLawRejection =
        feeCodeAreaOfLawRejection(differences, validationResult, state);
    if (areaOfLawRejection != null) {
      return List.of(areaOfLawRejection);
    }

    if (validationResult == null || validationResult.getIssues() == null) {
      return List.of();
    }

    return validationResult.getIssues().stream()
        .filter(issue -> issue.getSeverity() == ValidationSeverity.ERROR)
        .map(ClaimAmendmentValidationError::from)
        .toList();
  }

  /**
   * Determines whether the amendment changes the fee code to a code in a different Area of Law, and
   * if so builds the terminal {@link
   * ClaimAmendmentValidationCode#INVALID_FEE_CODE_AREA_OF_LAW_CHANGE
   * INVALID_FEE_CODE_AREA_OF_LAW_CHANGE} rejection.
   *
   * <p>The rule only applies when {@code claim.feeCode} is one of the changed diff fields. The Area
   * of Law of the new fee code is read from the reusable validation package's {@link
   * ResolvedClaimData#feeSchemeAreaOfLaw()} (populated during {@link
   * ValidationService#validateClaim(Claim, Set)}); the claim's current Area of Law is taken from
   * the before-state snapshot. The comparison is an <em>exact</em> match against the {@link
   * Enum#name() name} form the Fee Scheme reports (e.g. {@code "LEGAL_HELP"}): any other value is
   * treated as a different Area of Law. When the resolved Area of Law is absent, no rejection is
   * raised here - that condition is surfaced as a fee-scheme technical issue by the reusable
   * validators, so this gate does not duplicate it.
   *
   * @param diff the amendment diff; may be {@code null}
   * @param result the reusable validation result carrying the resolved fee-scheme data
   * @param state the in-memory amendment state
   * @return the terminal rejection error, or {@code null} if the gate does not fire
   */
  private ClaimAmendmentValidationError feeCodeAreaOfLawRejection(
      AmendmentDiff diff, ClaimValidationResult result, ClaimAmendmentState state) {
    if (diff == null || diff.changes() == null || result == null) {
      return null;
    }
    boolean feeCodeChanged =
        diff.changes().stream()
            .map(DiffEntry::fieldIdentifier)
            .anyMatch(ClaimFields.FEE_CODE::equals);
    if (!feeCodeChanged) {
      return null;
    }

    ResolvedClaimData resolved = result.getResolvedData();
    String resolvedAreaOfLaw = resolved == null ? null : resolved.feeSchemeAreaOfLaw();
    AreaOfLaw claimAreaOfLaw =
        state.getBeforeState() == null ? null : state.getBeforeState().getAreaOfLaw();

    // Nothing to compare against: a null/blank resolved area of law is surfaced by the reusable
    // validators as a fee-scheme technical error, so we do not add a rejection here.
    if (resolvedAreaOfLaw == null || resolvedAreaOfLaw.isBlank() || claimAreaOfLaw == null) {
      return null;
    }

    // Exact match required. The Fee Scheme reports the area of law in the enum name form (e.g.
    // "LEGAL_HELP"); any other value - including a differently-formatted one such as "LEGAL HELP" -
    // is treated as a different area of law and rejected. No normalisation is applied.
    if (resolvedAreaOfLaw.equals(claimAreaOfLaw.name())) {
      return null;
    }

    String newFeeCode =
        state.getPostAmendmentState() == null ? null : state.getPostAmendmentState().getFeeCode();
    return ClaimAmendmentValidationError.of(
        ClaimAmendmentValidationCode.INVALID_FEE_CODE_AREA_OF_LAW_CHANGE,
        newFeeCode,
        resolvedAreaOfLaw,
        claimAreaOfLaw.name());
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
