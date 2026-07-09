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
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationIssue;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationResult;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationSeverity;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.service.ValidationService;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentDiff;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ChangeSource;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.DiffEntry;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ValidationClaimMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence.AmendmentDiffAssembler;

/**
 * Unit tests for {@link AmendmentExternalValidationStep}.
 *
 * <p>The step is exercised in isolation with its collaborators mocked: the {@link
 * AmendmentDiffAssembler} controls the diff feeding the PDA-trigger decision, the {@link
 * ValidationClaimMapper} is a pass-through and the {@link ValidationService} returns a canned
 * {@link ValidationResult}. The tests pin two behaviours: (1) how the returned {@link
 * ValidationResult} is mapped into amendment errors and (2) whether the {@code
 * CLAIM_CATEGORY_OF_LAW} (PDA) validation code is included in the set handed to the validation
 * service based on the diff.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AmendmentExternalValidationStep Tests")
class AmendmentExternalValidationStepTest {

  private static final String PDA_CODE = "CLAIM_CATEGORY_OF_LAW";

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

  private DiffEntry change(String fieldIdentifier) {
    return new DiffEntry(fieldIdentifier, ChangeSource.REQUESTED, "before", "after");
  }

  private ValidationIssue issue(String code, String message, ValidationSeverity severity) {
    return ValidationIssue.builder().code(code).message(message).severity(severity).build();
  }

  /** Stubs the mapper (pass-through) and the validation service to return the given result. */
  private void stubValidation(ValidationResult result) {
    when(validationClaimMapper.toValidationClaim(any())).thenReturn(mock(Claim.class));
    when(validationService.validateClaim(any(), anySet())).thenReturn(result);
  }

  @SuppressWarnings("unchecked")
  private Set<String> capturedValidationCodes() {
    ArgumentCaptor<Set<String>> captor = ArgumentCaptor.forClass(Set.class);
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
      stubValidation(ValidationResult.builder().isValid(false).issues(null).build());

      assertThat(step.validate(stateWith(ClaimStateSnapshot.builder().build()))).isEmpty();
    }

    @Test
    @DisplayName("result with empty issues yields no errors")
    void emptyIssues() {
      when(diffAssembler.assemble(any())).thenReturn(AmendmentDiff.of(List.of()));
      stubValidation(ValidationResult.builder().isValid(true).issues(List.of()).build());

      assertThat(step.validate(stateWith(ClaimStateSnapshot.builder().build()))).isEmpty();
    }

    @Test
    @DisplayName("ERROR-severity issues are mapped to amendment errors preserving code and message")
    void mapsErrorIssues() {
      when(diffAssembler.assemble(any())).thenReturn(AmendmentDiff.of(List.of()));
      stubValidation(
          ValidationResult.builder()
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
          ValidationResult.builder()
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
      stubValidation(ValidationResult.builder().isValid(true).build());

      step.validate(stateWith(ClaimStateSnapshot.builder().build()));

      assertThat(capturedValidationCodes()).contains(PDA_CODE);
    }

    @Test
    @DisplayName("non-impacting change omits the CLAIM_CATEGORY_OF_LAW code")
    void omitsPdaCodeWhenNotImpacted() {
      when(diffAssembler.assemble(any()))
          .thenReturn(AmendmentDiff.of(List.of(change("client.clientSurname"))));
      stubValidation(ValidationResult.builder().isValid(true).build());

      step.validate(stateWith(ClaimStateSnapshot.builder().build()));

      assertThat(capturedValidationCodes()).doesNotContain(PDA_CODE);
    }

    @Test
    @DisplayName("null diff omits the CLAIM_CATEGORY_OF_LAW code")
    void omitsPdaCodeWhenDiffNull() {
      when(diffAssembler.assemble(any())).thenReturn(null);
      stubValidation(ValidationResult.builder().isValid(true).build());

      step.validate(stateWith(ClaimStateSnapshot.builder().build()));

      assertThat(capturedValidationCodes()).doesNotContain(PDA_CODE);
    }

    @Test
    @DisplayName("diff with null changes omits the CLAIM_CATEGORY_OF_LAW code")
    void omitsPdaCodeWhenChangesNull() {
      when(diffAssembler.assemble(any())).thenReturn(new AmendmentDiff(1, null));
      stubValidation(ValidationResult.builder().isValid(true).build());

      step.validate(stateWith(ClaimStateSnapshot.builder().build()));

      assertThat(capturedValidationCodes()).doesNotContain(PDA_CODE);
    }

    @Test
    @DisplayName("empty changes omit the CLAIM_CATEGORY_OF_LAW code")
    void omitsPdaCodeWhenChangesEmpty() {
      when(diffAssembler.assemble(any())).thenReturn(AmendmentDiff.of(List.of()));
      stubValidation(ValidationResult.builder().isValid(true).build());

      step.validate(stateWith(ClaimStateSnapshot.builder().build()));

      assertThat(capturedValidationCodes()).doesNotContain(PDA_CODE);
    }

    @Test
    @DisplayName("null post-amendment state omits the CLAIM_CATEGORY_OF_LAW code")
    void omitsPdaCodeWhenMergedStateNull() {
      when(diffAssembler.assemble(any()))
          .thenReturn(AmendmentDiff.of(List.of(change("claim.feeCode"))));
      stubValidation(ValidationResult.builder().isValid(true).build());

      step.validate(stateWith(null));

      assertThat(capturedValidationCodes()).doesNotContain(PDA_CODE);
    }

    @Test
    @DisplayName("the full non-PDA validator set is always included")
    void includesAllNonPdaCodes() {
      when(diffAssembler.assemble(any())).thenReturn(AmendmentDiff.of(List.of()));
      stubValidation(ValidationResult.builder().isValid(true).build());

      step.validate(stateWith(ClaimStateSnapshot.builder().build()));

      assertThat(capturedValidationCodes())
          .contains(
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
              "CLAIM_UNIQUE_FILE_NUMBER");
    }
  }
}
