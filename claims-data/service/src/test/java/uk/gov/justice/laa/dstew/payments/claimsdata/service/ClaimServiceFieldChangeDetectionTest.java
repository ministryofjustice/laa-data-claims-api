package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openapitools.jackson.nullable.JsonNullable;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentResult;
import org.springframework.util.ReflectionUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimAmendmentPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.AmendmentTestData;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

/**
 * Parameterised-style unit tests that exercise the ClaimService change-detection routing.
 *
 * <p>These tests intentionally construct patch fields that carry values in a different runtime type
 * to the persisted claim (for example a JsonNullable holding a String while the Claim stores a
 * LocalDate) and also cover a mapped-name case (patch property {@code isDutySolicitor} -> entity
 * field {@code dutySolicitor}). The current implementation compares the raw candidate value to
 * the reflected claim field using Objects.equals; that yields false when types differ even if the
 * logical value is the same. The expectation asserted here represents the desired behaviour
 * (no amendment when submitted value is semantically equal) and will therefore fail until the
 * production code is updated to normalise/resolve mapped names.
 */
@ExtendWith(MockitoExtension.class)
class ClaimServiceFieldChangeDetectionTest {

  @Mock private uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository claimRepository;
  // minimal mocks required for ClaimService construction - re-use real names from existing tests
  @Mock private uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository submissionRepository;
  @Mock private uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClientRepository clientRepository;
  @Mock private uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimMapper claimMapper;
  @Mock private uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClientMapper clientMapper;
  @Mock private uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationMessageLogRepository validationMessageLogRepository;
  @Mock private uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimResultSetMapper claimResultSetMapper;
  @Mock private uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository claimSummaryFeeRepository;
  @Mock private uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository calculatedFeeDetailRepository;
  @Mock private uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimCaseRepository claimCaseRepository;
  @Mock private uk.gov.justice.laa.dstew.payments.claimsdata.repository.AssessmentRepository assessmentRepository;
  @Mock private uk.gov.justice.laa.dstew.payments.claimsdata.service.ClaimValidationService claimValidationService;
  @Mock private uk.gov.justice.laa.dstew.payments.claimsdata.service.AssessmentService assessmentService;
  @Mock private uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.ClaimAmendmentService claimAmendmentService;
  @Mock private uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.ClaimAmendmentStateService claimAmendmentStateService;

  @InjectMocks private ClaimService claimService;

  @DisplayName("String-valued JSON date that matches persisted LocalDate should not trigger amendment (desired behaviour)")
  @Test
  void caseStartDate_stringValueMatchingLocalDate_shouldStayLegacy_butCurrentlyFails() {
    // Arrange: persisted claim has a LocalDate value
    Claim claim = Claim.builder().id(AmendmentTestData.CLAIM_ID).caseStartDate(AmendmentTestData.CASE_START_DATE).build();

    // Build a patch and then deliberately set the raw field to hold a String inside JsonNullable
    ClaimAmendmentPatch patch = new ClaimAmendmentPatch();
    // ensure status present so the service considers legacy status/fee path and performs field
    // change detection rather than immediately routing to amendment
    patch.setStatus(ClaimStatus.VALID);
    Field patchField = ReflectionUtils.findField(ClaimAmendmentPatch.class, "caseStartDate");
    // defensive - if the generated model has a different name this test will be obviously wrong
    if (patchField == null) {
      throw new IllegalStateException("ClaimAmendmentPatch.caseStartDate field not found");
    }
    ReflectionUtils.makeAccessible(patchField);
    // Simulate the provider submitting a string date (wire type) inside the tri-state wrapper
    ReflectionUtils.setField(patchField, patch, JsonNullable.of(AmendmentTestData.CASE_START_DATE.toString()));

    when(claimRepository.findByIdAndSubmissionId(AmendmentTestData.CLAIM_ID, AmendmentTestData.SUBMISSION_ID))
        .thenReturn(Optional.of(claim));
    // No guard required - if the amendment path is incorrectly taken the test will fail

    // Act: call updateClaim which will route based on the change-detection logic
    claimService.updateClaim(AmendmentTestData.SUBMISSION_ID, AmendmentTestData.CLAIM_ID, patch);

    // Assert: desired behaviour is to keep legacy path (no amendment). Currently this test will
    // fail with existing code; leaving this assertion expresses the intended behaviour.
    verifyNoInteractions(claimAmendmentService);
  }

  @DisplayName("Mapped patch property isDutySolicitor (provider) -> dutySolicitor (entity) should not trigger amendment when equal)")
  @Test
  void isDutySolicitor_mappedNameMatchingBoolean_shouldStayLegacy_butCurrentlyFails() {
    // persisted claim field is 'dutySolicitor'
    Claim claim = Claim.builder().id(AmendmentTestData.CLAIM_ID).dutySolicitor(true).build();

    // Patch carries isDutySolicitor present=true
    ClaimAmendmentPatch patch = new ClaimAmendmentPatch();
    patch.setStatus(ClaimStatus.VALID);
    patch.setIsDutySolicitor(JsonNullable.of(true));

    when(claimRepository.findByIdAndSubmissionId(AmendmentTestData.CLAIM_ID, AmendmentTestData.SUBMISSION_ID))
        .thenReturn(Optional.of(claim));
    // No guard required - if the amendment path is incorrectly taken the test will fail

    claimService.updateClaim(AmendmentTestData.SUBMISSION_ID, AmendmentTestData.CLAIM_ID, patch);

    // Desired behaviour: no amendment because submitted value equals persisted value on mapped field.
    verifyNoInteractions(claimAmendmentService);
  }
}





