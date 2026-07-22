package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.Claim;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ClaimValidationResult;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ResolvedClaimData;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationIssue;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationSeverity;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.service.ValidationService;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.validator.claim.ClaimValidatorCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentDiff;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ChangeSource;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.DiffEntry;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ValidationClaimMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence.AmendmentDiffAssembler;

/**
 * Unit tests for {@link AmendmentExternalValidationStep}.
 *
 * <p>The step is exercised in isolation with its collaborators mocked: the {@link
 * AmendmentDiffAssembler} controls the diff feeding the PDA-trigger decision, the {@link
 * ValidationClaimMapper} is a pass-through and the {@link ValidationService} returns a canned
 * {@link ClaimValidationResult}. The tests pin two behaviours: (1) how the returned {@link
 * ClaimValidationResult} is mapped into amendment errors and (2) whether the {@code
 * CLAIM_CATEGORY_OF_LAW} (PDA) validation code is included in the set handed to the validation
 * service based on the diff.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AmendmentExternalValidationStep Tests")
class AmendmentExternalValidationStepTest {

  private static final ClaimValidatorCode PDA_CODE =
      ClaimValidatorCode.CLAIM_CATEGORY_OF_LAW_VALIDATOR;

  @Mock private ValidationService validationService;
  @Mock private AmendmentDiffAssembler diffAssembler;
  @Mock private ValidationClaimMapper validationClaimMapper;

  @InjectMocks private AmendmentExternalValidationStep step;

  // ---------------------------------------------------------------------------
  // Fixtures
  // ---------------------------------------------------------------------------

  private ClaimAmendmentState stateWith(ClaimStateSnapshot postAmendmentState) {
    return ClaimAmendmentState.builder().postAmendmentState(postAmendmentState).build();
  }

  private ClaimAmendmentState feeCodeChangeState(AreaOfLaw claimAreaOfLaw, String newFeeCode) {
    return ClaimAmendmentState.builder()
        .beforeState(ClaimStateSnapshot.builder().areaOfLaw(claimAreaOfLaw).build())
        .postAmendmentState(ClaimStateSnapshot.builder().feeCode(newFeeCode).build())
        .build();
  }

  private DiffEntry change(String fieldIdentifier) {
    return new DiffEntry(fieldIdentifier, ChangeSource.REQUESTED, "before", "after");
  }

  private ValidationIssue issue(String code, String message, ValidationSeverity severity) {
    return ValidationIssue.builder().code(code).message(message).severity(severity).build();
  }

  /** Stubs the mapper (pass-through) and the validation service to return the given result. */
  private void stubValidation(ClaimValidationResult result) {
    when(validationClaimMapper.toValidationClaim(any())).thenReturn(mock(Claim.class));
    when(validationService.validateClaim(any(), anySet())).thenReturn(result);
  }

  @SuppressWarnings("unchecked")
  private Set<ClaimValidatorCode> capturedValidationCodes() {
    ArgumentCaptor<Set<ClaimValidatorCode>> captor = ArgumentCaptor.forClass(Set.class);
    verify(validationService).validateClaim(any(), captor.capture());
    return captor.getValue();
  }

  @Nested
  @DisplayName("validation outcome mapping")
  class OutcomeMapping {

    @Test
    @DisplayName("null validation result yields no errors")
    void nullResult() {
      when(diffAssembler.assemble(any())).thenReturn(AmendmentDiff.of(List.of()));
      stubValidation(null);

      assertThat(step.validate(stateWith(ClaimStateSnapshot.builder().build()))).isEmpty();
    }

    @Test
    @DisplayName("result with null issues yields no errors")
    void nullIssues() {
      when(diffAssembler.assemble(any())).thenReturn(AmendmentDiff.of(List.of()));
      stubValidation(ClaimValidationResult.builder().isValid(false).issues(null).build());

      assertThat(step.validate(stateWith(ClaimStateSnapshot.builder().build()))).isEmpty();
    }

    @Test
    @DisplayName("result with empty issues yields no errors")
    void emptyIssues() {
      when(diffAssembler.assemble(any())).thenReturn(AmendmentDiff.of(List.of()));
      stubValidation(ClaimValidationResult.builder().isValid(true).issues(List.of()).build());

      assertThat(step.validate(stateWith(ClaimStateSnapshot.builder().build()))).isEmpty();
    }

    @Test
    @DisplayName("ERROR-severity issues are mapped to amendment errors preserving code and message")
    void mapsErrorIssues() {
      when(diffAssembler.assemble(any())).thenReturn(AmendmentDiff.of(List.of()));
      stubValidation(
          ClaimValidationResult.builder()
              .isValid(false)
              .issues(List.of(issue("CLAIM_SCHEMA", "Schema failed", ValidationSeverity.ERROR)))
              .build());

      List<ClaimAmendmentValidationError> errors =
          step.validate(stateWith(ClaimStateSnapshot.builder().build()));

      assertThat(errors).hasSize(1);
      ClaimAmendmentValidationError error = errors.getFirst();
      assertThat(error.getCode()).isEqualTo("CLAIM_SCHEMA");
      assertThat(error.getMessage()).isEqualTo("Schema failed");
      assertThat(error.isFatal()).isFalse();
    }

    @Test
    @DisplayName("non-ERROR issues (WARNING/INFO) are filtered out")
    void filtersNonErrorIssues() {
      when(diffAssembler.assemble(any())).thenReturn(AmendmentDiff.of(List.of()));
      stubValidation(
          ClaimValidationResult.builder()
              .isValid(false)
              .issues(
                  List.of(
                      issue("W1", "a warning", ValidationSeverity.WARNING),
                      issue("I1", "some info", ValidationSeverity.INFO),
                      issue("E1", "an error", ValidationSeverity.ERROR)))
              .build());

      assertThat(step.validate(stateWith(ClaimStateSnapshot.builder().build())))
          .extracting(ClaimAmendmentValidationError::getCode)
          .containsExactly("E1");
    }
  }

  @Nested
  @DisplayName("PDA trigger decision")
  class PdaTrigger {

    @Test
    @DisplayName("PDA-impacting change includes the CLAIM_CATEGORY_OF_LAW code")
    void includesPdaCodeWhenImpacted() {
      // claim.feeCode always impacts the PDA request.
      when(diffAssembler.assemble(any()))
          .thenReturn(AmendmentDiff.of(List.of(change("claim.feeCode"))));
      stubValidation(ClaimValidationResult.builder().isValid(true).build());

      step.validate(stateWith(ClaimStateSnapshot.builder().build()));

      assertThat(capturedValidationCodes()).contains(PDA_CODE);
    }

    @Test
    @DisplayName("non-impacting change omits the CLAIM_CATEGORY_OF_LAW code")
    void omitsPdaCodeWhenNotImpacted() {
      when(diffAssembler.assemble(any()))
          .thenReturn(AmendmentDiff.of(List.of(change("client.clientSurname"))));
      stubValidation(ClaimValidationResult.builder().isValid(true).build());

      step.validate(stateWith(ClaimStateSnapshot.builder().build()));

      assertThat(capturedValidationCodes()).doesNotContain(PDA_CODE);
    }

    @Test
    @DisplayName("null diff omits the CLAIM_CATEGORY_OF_LAW code")
    void omitsPdaCodeWhenDiffNull() {
      when(diffAssembler.assemble(any())).thenReturn(null);
      stubValidation(ClaimValidationResult.builder().isValid(true).build());

      step.validate(stateWith(ClaimStateSnapshot.builder().build()));

      assertThat(capturedValidationCodes()).doesNotContain(PDA_CODE);
    }

    @Test
    @DisplayName("diff with null changes omits the CLAIM_CATEGORY_OF_LAW code")
    void omitsPdaCodeWhenChangesNull() {
      when(diffAssembler.assemble(any())).thenReturn(new AmendmentDiff(1, null));
      stubValidation(ClaimValidationResult.builder().isValid(true).build());

      step.validate(stateWith(ClaimStateSnapshot.builder().build()));

      assertThat(capturedValidationCodes()).doesNotContain(PDA_CODE);
    }

    @Test
    @DisplayName("empty changes omit the CLAIM_CATEGORY_OF_LAW code")
    void omitsPdaCodeWhenChangesEmpty() {
      when(diffAssembler.assemble(any())).thenReturn(AmendmentDiff.of(List.of()));
      stubValidation(ClaimValidationResult.builder().isValid(true).build());

      step.validate(stateWith(ClaimStateSnapshot.builder().build()));

      assertThat(capturedValidationCodes()).doesNotContain(PDA_CODE);
    }

    @Test
    @DisplayName("null post-amendment state omits the CLAIM_CATEGORY_OF_LAW code")
    void omitsPdaCodeWhenMergedStateNull() {
      when(diffAssembler.assemble(any()))
          .thenReturn(AmendmentDiff.of(List.of(change("claim.feeCode"))));
      stubValidation(ClaimValidationResult.builder().isValid(true).build());

      step.validate(stateWith(null));

      assertThat(capturedValidationCodes()).doesNotContain(PDA_CODE);
    }

    @Test
    @DisplayName("the full non-PDA validator set is always included")
    void includesAllNonPdaCodes() {
      when(diffAssembler.assemble(any())).thenReturn(AmendmentDiff.of(List.of()));
      stubValidation(ClaimValidationResult.builder().isValid(true).build());

      step.validate(stateWith(ClaimStateSnapshot.builder().build()));

      assertThat(capturedValidationCodes())
          .contains(
              ClaimValidatorCode.CLAIM_SCHEMA_VALIDATOR,
              ClaimValidatorCode.CLAIM_CASE_DATES_VALIDATOR,
              ClaimValidatorCode.CLAIM_MATTER_TYPE_VALIDATOR,
              ClaimValidatorCode.CLAIM_STAGE_REACHED_VALIDATOR,
              ClaimValidatorCode.CLAIM_CLIENT_DATE_OF_BIRTH_VALIDATOR,
              ClaimValidatorCode.CLAIM_DISBURSEMENT_START_DATE_VALIDATOR,
              ClaimValidatorCode.CLAIM_DISBURSEMENTS_VALIDATOR,
              ClaimValidatorCode.CLAIM_DUPLICATE_VALIDATOR,
              ClaimValidatorCode.CLAIM_SCHEDULE_REFERENCE_VALIDATOR,
              ClaimValidatorCode.CLAIM_MANDATORY_FIELD_VALIDATOR,
              ClaimValidatorCode.CLAIM_OUTCOME_CODE_VALIDATOR,
              ClaimValidatorCode.CLAIM_UNIQUE_FILE_NUMBER_VALIDATOR);
    }
  }

  @Nested
  @DisplayName("fee code Area of Law gate")
  class FeeCodeAreaOfLaw {

    private static final String FEE_CODE_FIELD = "claim.feeCode";

    /** Stubs the mapper and returns a validation result carrying the given resolved Area of Law. */
    private void stubValidationWithResolvedAreaOfLaw(String resolvedAreaOfLaw) {
      when(validationClaimMapper.toValidationClaim(any())).thenReturn(mock(Claim.class));
      when(validationService.validateClaim(any(), anySet()))
          .thenReturn(
              ClaimValidationResult.builder()
                  .isValid(true)
                  .issues(List.of())
                  .resolvedData(new ResolvedClaimData(null, resolvedAreaOfLaw, null))
                  .build());
    }

    @Test
    @DisplayName("different Area of Law fee code change is a fatal terminal rejection")
    void differentAreaOfLawRejected() {
      when(diffAssembler.assemble(any()))
          .thenReturn(AmendmentDiff.of(List.of(change(FEE_CODE_FIELD))));
      // Genuine Fee Scheme format: the enum name form "LEGAL_HELP" against a CRIME_LOWER claim.
      stubValidationWithResolvedAreaOfLaw("LEGAL_HELP");

      List<ClaimAmendmentValidationError> errors =
          step.validate(feeCodeChangeState(AreaOfLaw.CRIME_LOWER, "NEWCODE"));

      assertThat(errors).hasSize(1);
      ClaimAmendmentValidationError error = errors.getFirst();
      assertThat(error.getCode()).isEqualTo("INVALID_FEE_CODE_AREA_OF_LAW_CHANGE");
      assertThat(error.isFatal()).isTrue();
    }

    @Test
    @DisplayName("same Area of Law (exact name form) does not raise the gate")
    void sameAreaOfLawExactMatchAllowed() {
      when(diffAssembler.assemble(any()))
          .thenReturn(AmendmentDiff.of(List.of(change(FEE_CODE_FIELD))));
      stubValidationWithResolvedAreaOfLaw("CRIME_LOWER");

      assertThat(step.validate(feeCodeChangeState(AreaOfLaw.CRIME_LOWER, "NEWCODE"))).isEmpty();
    }

    @Test
    @DisplayName(
        "a differently-formatted (spaced) Area of Law is not an exact match and is rejected")
    void differentlyFormattedAreaOfLawIsRejected() {
      when(diffAssembler.assemble(any()))
          .thenReturn(AmendmentDiff.of(List.of(change(FEE_CODE_FIELD))));
      // "LEGAL HELP" (space) is not the exact name form "LEGAL_HELP", so it is not a match.
      stubValidationWithResolvedAreaOfLaw("LEGAL HELP");

      assertThat(step.validate(feeCodeChangeState(AreaOfLaw.LEGAL_HELP, "NEWCODE")))
          .extracting(ClaimAmendmentValidationError::getCode)
          .containsExactly("INVALID_FEE_CODE_AREA_OF_LAW_CHANGE");
    }

    @Test
    @DisplayName("an unknown (non-empty) Area of Law is not an exact match and is rejected")
    void unknownAreaOfLawIsRejected() {
      when(diffAssembler.assemble(any()))
          .thenReturn(AmendmentDiff.of(List.of(change(FEE_CODE_FIELD))));
      // A value the Fee Scheme returned that is not any known area of law: still not an exact
      // match,
      // so it is rejected.
      stubValidationWithResolvedAreaOfLaw("FAMILY");

      assertThat(step.validate(feeCodeChangeState(AreaOfLaw.CRIME_LOWER, "NEWCODE")))
          .extracting(ClaimAmendmentValidationError::getCode)
          .containsExactly("INVALID_FEE_CODE_AREA_OF_LAW_CHANGE");
    }

    @Test
    @DisplayName(
        "absent Area of Law fails via the reusable technical error, not the area-of-law gate")
    void absentAreaOfLawFailsViaTechnicalErrorNotGate() {
      when(diffAssembler.assemble(any()))
          .thenReturn(AmendmentDiff.of(List.of(change(FEE_CODE_FIELD))));
      // When the Fee Scheme cannot resolve an area of law it returns a null value AND a technical
      // error (a blank areaOfLaw / 404 / lookup failure always yields
      // TECHNICAL_ERROR_FEE_SCHEME_API).
      // The amendment therefore still fails - via that technical error, down the controlled no-save
      // path - and the gate deliberately does not pile on a second, misleading "area of law change"
      // rejection for what is actually a lookup failure.
      when(validationClaimMapper.toValidationClaim(any())).thenReturn(mock(Claim.class));
      when(validationService.validateClaim(any(), anySet()))
          .thenReturn(
              ClaimValidationResult.builder()
                  .isValid(false)
                  .issues(
                      List.of(
                          issue(
                              "TECHNICAL_ERROR_FEE_SCHEME_API",
                              "technical failure",
                              ValidationSeverity.ERROR)))
                  .resolvedData(new ResolvedClaimData(null, null, null))
                  .build());

      assertThat(step.validate(feeCodeChangeState(AreaOfLaw.CRIME_LOWER, "NEWCODE")))
          .extracting(ClaimAmendmentValidationError::getCode)
          .containsExactly("TECHNICAL_ERROR_FEE_SCHEME_API");
    }

    @Test
    @DisplayName("gate does not fire when the fee code did not change")
    void feeCodeUnchangedSkipsGate() {
      when(diffAssembler.assemble(any()))
          .thenReturn(AmendmentDiff.of(List.of(change("client.clientSurname"))));
      stubValidationWithResolvedAreaOfLaw("LEGAL HELP");

      assertThat(step.validate(feeCodeChangeState(AreaOfLaw.CRIME_LOWER, "NEWCODE"))).isEmpty();
    }
  }
}
