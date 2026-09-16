package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.ClaimValidationService.INVALID_CLAIM_STATUS_UPDATE_MESSAGE;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.AssessmentInvalidUserException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimBadRequestException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimSummaryFeeNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimAmendmentValidationException;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.VoidClaimRequest;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClaimValidationService tests")
class ClaimValidationServiceTest {

  @Mock private ClaimRepository claimRepository;

  @Mock private ClaimSummaryFeeRepository claimSummaryFeeRepository;

  @InjectMocks private ClaimValidationService validationService;

  // =====================================================
  // Validate User ID Tests
  // =====================================================
  @Nested
  @DisplayName("Validate User ID tests")
  class ValidateUserIdTests {

    @ParameterizedTest
    @MethodSource("invalidUserIds")
    @DisplayName("Should throw when user id is invalid")
    void shouldThrowWhenUserIdInvalid(
        String userId, AssessmentInvalidUserException.ErrorMessage errorMessage) {
      assertThatThrownBy(() -> validationService.validateUserId(userId))
          .isInstanceOf(AssessmentInvalidUserException.class)
          .hasMessageContaining(errorMessage.getMessage(userId));
    }

    static Stream<Object[]> invalidUserIds() {
      return Stream.of(
          new Object[] {null, AssessmentInvalidUserException.ErrorMessage.NULL_OR_BLANK},
          new Object[] {"", AssessmentInvalidUserException.ErrorMessage.NULL_OR_BLANK},
          new Object[] {"  ", AssessmentInvalidUserException.ErrorMessage.NULL_OR_BLANK},
          new Object[] {
            "INVALIDUUID", AssessmentInvalidUserException.ErrorMessage.INVALID_UUID_FORMAT
          },
          new Object[] {
            "<img src=x onerror=alert('XSS')>",
            AssessmentInvalidUserException.ErrorMessage.INVALID_UUID_FORMAT
          });
    }

    @Test
    @DisplayName("Should not throw when user id is valid")
    void shouldNotThrowWhenUserIdValid() {
      assertDoesNotThrow(() -> validationService.validateUserId(Uuid7.timeBasedUuid().toString()));
    }
  }

  // =====================================================
  // Version Validation Tests
  // =====================================================
  @Test
  @DisplayName("Should not throw when version is null")
  void shouldNotThrowWhenVersionIsNull() {
    assertDoesNotThrow(() -> validationService.validateVersionNumber(null));
  }

  @Test
  @DisplayName("Should not throw when version is positive")
  void shouldNotThrowWhenVersionIsPositive() {
    assertDoesNotThrow(() -> validationService.validateVersionNumber(1L));
  }

  @Test
  @DisplayName("Should not throw when provided version is null for claim match")
  void shouldNotThrowWhenProvidedVersionIsNullForClaimMatch() {
    UUID claimId = Uuid7.timeBasedUuid();
    Claim claim = Claim.builder().id(claimId).version(1L).build();

    assertDoesNotThrow(() -> validationService.validateClaimVersionMatches(claim, null));
  }

  @Test
  @DisplayName("Should throw ClaimAmendmentValidationException when claim is null but provided version is not")
  void shouldThrowWhenClaimIsNullButProvidedIsNot() {
    assertThatThrownBy(() -> validationService.validateClaimVersionMatches(null, 1L))
        .isInstanceOf(ClaimAmendmentValidationException.class)
        .satisfies(ex ->
            assertThat(((ClaimAmendmentValidationException) ex).getErrors().get(0).getCode())
                .isEqualTo(ClaimAmendmentValidationCode.CLAIM_VERSION_CONFLICT.name())
        );
  }

  @Test
  @DisplayName("Should not throw when provided version matches claim version")
  void shouldNotThrowWhenProvidedVersionMatchesClaimVersion() {
    UUID claimId = Uuid7.timeBasedUuid();
    Claim claim = Claim.builder().id(claimId).version(5L).build();

    assertDoesNotThrow(() -> validationService.validateClaimVersionMatches(claim, 5L));
  }

  @Test
  @DisplayName("Should throw ClaimAmendmentValidationException when provided version does not match claim version")
  void shouldThrowWhenProvidedVersionDoesNotMatchClaimVersion() {
    UUID claimId = Uuid7.timeBasedUuid();
    Claim claim = Claim.builder().id(claimId).version(2L).build();

    assertThatThrownBy(() -> validationService.validateClaimVersionMatches(claim, 3L))
        .isInstanceOf(ClaimAmendmentValidationException.class)
        .satisfies(ex ->
            assertThat(((ClaimAmendmentValidationException) ex).getErrors().get(0).getCode())
                .isEqualTo(ClaimAmendmentValidationCode.CLAIM_VERSION_CONFLICT.name())
        );
  }

  @Test
  @DisplayName("Should throw ClaimAmendmentValidationException when claim version is null but provided is not")
  void shouldThrowWhenClaimVersionIsNullButProvidedIsNot() {
    UUID claimId = Uuid7.timeBasedUuid();
    Claim claim = Claim.builder().id(claimId).build();

    assertThatThrownBy(() -> validationService.validateClaimVersionMatches(claim, 1L))
        .isInstanceOf(ClaimAmendmentValidationException.class)
        .satisfies(ex ->
            assertThat(((ClaimAmendmentValidationException) ex).getErrors().get(0).getCode())
                .isEqualTo(ClaimAmendmentValidationCode.CLAIM_VERSION_CONFLICT.name())
        );
  }

  // =====================================================
  // Void Claim Parameter Tests
  // =====================================================
  @ParameterizedTest
  @MethodSource("invalidVoidClaimParameters")
  @DisplayName("Should throw when void claim parameters are invalid")
  void shouldThrowWhenVoidClaimParametersInvalid(
      UUID claimId, UUID createdByUserId, String reason, String expectedMessage) {
    VoidClaimRequest voidClaimRequest =
        VoidClaimRequest.builder()
            .createdByUserId(createdByUserId)
            .version(1L)
            .assessmentReason(reason)
            .build();

    assertThatThrownBy(() -> validationService.validateVoidClaimRequest(claimId, voidClaimRequest))
        .isInstanceOf(ClaimBadRequestException.class)
        .hasMessageContaining(expectedMessage);
  }

  static Stream<Object[]> invalidVoidClaimParameters() {
    UUID validUserId = Uuid7.timeBasedUuid();
    UUID validClaimId = Uuid7.timeBasedUuid();
    return Stream.of(
        new Object[] {null, validUserId, "Valid reason", "claimId must be provided"},
        new Object[] {validClaimId, null, "Valid reason", "createdByUserId must be provided"},
        new Object[] {validClaimId, validUserId, " ", "assessmentReason must be provided"});
  }

  @Test
  @DisplayName("Should not throw when void claim parameters are valid")
  void shouldNotThrowWhenVoidClaimParametersValid() {
    UUID claimId = Uuid7.timeBasedUuid();
    UUID userId = Uuid7.timeBasedUuid();
    String reason = "Valid reason";

    VoidClaimRequest voidClaimRequest =
        VoidClaimRequest.builder()
            .createdByUserId(userId)
            .version(1L)
            .assessmentReason(reason)
            .build();

    assertDoesNotThrow(() -> validationService.validateVoidClaimRequest(claimId, voidClaimRequest));
  }

  @Test
  @DisplayName("Should throw when claimId is null")
  void shouldThrowWhenClaimIdIsNull() {
    UUID userId = Uuid7.timeBasedUuid();
    VoidClaimRequest voidClaimRequest =
        VoidClaimRequest.builder()
            .createdByUserId(userId)
            .version(1L)
            .assessmentReason("reason")
            .build();

    assertThatThrownBy(() -> validationService.validateVoidClaimRequest(null, voidClaimRequest))
        .isInstanceOf(ClaimBadRequestException.class)
        .hasMessageContaining("claimId must be provided");
  }

  @Test
  @DisplayName("Should throw when createdByUserId is null")
  void shouldThrowWhenCreatedByUserIdIsNull() {
    UUID claimId = Uuid7.timeBasedUuid();
    VoidClaimRequest voidClaimRequest =
        VoidClaimRequest.builder().version(1L).assessmentReason("reason").build();

    assertThatThrownBy(() -> validationService.validateVoidClaimRequest(claimId, voidClaimRequest))
        .isInstanceOf(ClaimBadRequestException.class)
        .hasMessageContaining("createdByUserId must be provided");
  }

  @Test
  @DisplayName("Should throw when assessment reason is null")
  void shouldThrowWhenAssessmentReasonIsNull() {
    assertThatThrownBy(() -> validationService.validateAssessmentReason(null))
        .isInstanceOf(ClaimBadRequestException.class)
        .hasMessageContaining("assessmentReason must be provided");
  }

  @Test
  @DisplayName("Should throw when assessment reason is blank or empty")
  void shouldThrowWhenAssessmentReasonIsBlankOrEmpty() {
    assertThatThrownBy(() -> validationService.validateAssessmentReason(""))
        .isInstanceOf(ClaimBadRequestException.class)
        .hasMessageContaining("assessmentReason must be provided");

    assertThatThrownBy(() -> validationService.validateAssessmentReason("   "))
        .isInstanceOf(ClaimBadRequestException.class)
        .hasMessageContaining("assessmentReason must be provided");
  }

  @Test
  @DisplayName("Should not throw when assessment reason is valid")
  void shouldNotThrowWhenAssessmentReasonIsValid() {
    assertDoesNotThrow(() -> validationService.validateAssessmentReason("valid reason"));
  }

  // =====================================================
  // Claim Summary Fee Tests
  // =====================================================
  @Test
  @DisplayName("Should throw when claim summary fee not found")
  void shouldThrowWhenClaimSummaryFeeNotFound() {
    UUID claimId = Uuid7.timeBasedUuid();
    when(claimSummaryFeeRepository.findByClaimId(claimId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> validationService.getClaimSummaryFeeByClaimIdOrThrow(claimId))
        .isInstanceOf(ClaimSummaryFeeNotFoundException.class)
        .hasMessageContaining(claimId.toString());

    verify(claimSummaryFeeRepository).findByClaimId(claimId);
  }

  @Test
  @DisplayName("Should return claim summary fee when it exists")
  void shouldReturnClaimSummaryFeeWhenExists() {
    UUID claimId = Uuid7.timeBasedUuid();
    ClaimSummaryFee fee = new ClaimSummaryFee();
    when(claimSummaryFeeRepository.findByClaimId(claimId)).thenReturn(Optional.of(fee));

    ClaimSummaryFee result = validationService.getClaimSummaryFeeByClaimIdOrThrow(claimId);
    assertThat(result).isSameAs(fee);
  }

  @Test
  @DisplayName("Should throw when claim summary fee does not exist by id")
  void shouldThrowWhenClaimSummaryFeeDoesNotExistById() {
    UUID feeId = Uuid7.timeBasedUuid();
    when(claimSummaryFeeRepository.existsById(feeId)).thenReturn(false);

    assertThatThrownBy(() -> validationService.getClaimSummaryFeeByIdOrThrow(feeId))
        .isInstanceOf(ClaimSummaryFeeNotFoundException.class)
        .hasMessageContaining(feeId.toString());
  }

  @Test
  @DisplayName("Should return reference when claim summary fee exists by id")
  void shouldReturnReferenceWhenClaimSummaryFeeExistsById() {
    UUID feeId = Uuid7.timeBasedUuid();
    ClaimSummaryFee fee = new ClaimSummaryFee();
    when(claimSummaryFeeRepository.existsById(feeId)).thenReturn(true);
    when(claimSummaryFeeRepository.getReferenceById(feeId)).thenReturn(fee);

    ClaimSummaryFee result = validationService.getClaimSummaryFeeByIdOrThrow(feeId);
    assertThat(result).isSameAs(fee);
  }

  // =====================================================
  // Claim Tests
  // =====================================================
  @Test
  @DisplayName("Should throw when claim not found")
  void shouldThrowWhenClaimNotFound() {
    UUID claimId = Uuid7.timeBasedUuid();
    when(claimRepository.findById(claimId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> validationService.getValidClaimOrThrow(claimId))
        .isInstanceOf(ClaimNotFoundException.class)
        .hasMessageContaining(claimId.toString());
  }

  @Test
  @DisplayName("Should return claim when valid")
  void shouldReturnClaimWhenValid() {
    UUID claimId = Uuid7.timeBasedUuid();
    Claim claim = Claim.builder().id(claimId).status(ClaimStatus.VALID).build();
    when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));

    Claim result = validationService.getValidClaimOrThrow(claimId);
    assertThat(result).isSameAs(claim);
  }

  @Test
  @DisplayName("Should throw when claim status is VOID")
  void shouldThrowWhenClaimStatusIsVoid() {
    UUID claimId = Uuid7.timeBasedUuid();
    Claim claim = Claim.builder().id(claimId).status(ClaimStatus.VOID).build();
    when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));

    assertThatThrownBy(() -> validationService.getValidClaimOrThrow(claimId))
        .isInstanceOf(ClaimBadRequestException.class)
        .hasMessageContaining(claimId.toString());
  }

  // =====================================================
  // Assessment Type Tests
  // =====================================================
  @Test
  @DisplayName("Should throw when assessment type is null")
  void shouldThrowWhenAssessmentTypeIsNull() {
    assertThatThrownBy(() -> validationService.validateAssessmentType(null))
        .isInstanceOf(ClaimBadRequestException.class)
        .hasMessageContaining("assessmentType must be provided");
  }

  @Test
  @DisplayName("Should throw when assessment type is VOID")
  void shouldThrowWhenAssessmentTypeIsVoid() {
    assertThatThrownBy(() -> validationService.validateAssessmentType(AssessmentType.VOID))
        .isInstanceOf(ClaimBadRequestException.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = AssessmentType.class,
      names = {"VOID"},
      mode = EnumSource.Mode.EXCLUDE)
  @DisplayName("Should not throw for non-VOID assessment types")
  void shouldNotThrowForNonVoidAssessmentTypes(AssessmentType type) {
    assertDoesNotThrow(() -> validationService.validateAssessmentType(type));
  }

  // =====================================================
  // Claim Status Tests
  // =====================================================
  @Test
  @DisplayName("Should not throw when claim has VALID status")
  void shouldNotThrowWhenClaimHasValidStatus() {
    UUID claimId = Uuid7.timeBasedUuid();
    Claim claim = Claim.builder().id(claimId).status(ClaimStatus.VALID).build();

    assertDoesNotThrow(() -> validationService.ensureClaimIsValid(claim));
  }

  @ParameterizedTest
  @EnumSource(
      value = ClaimStatus.class,
      names = {"VALID"},
      mode = EnumSource.Mode.EXCLUDE)
  @DisplayName("Should throw when claim does not have VALID status")
  void shouldThrowWhenClaimDoesNotHaveValidStatus(ClaimStatus status) {
    UUID claimId = Uuid7.timeBasedUuid();
    Claim claim = Claim.builder().id(claimId).status(status).build();

    assertThatThrownBy(() -> validationService.ensureClaimIsValid(claim))
        .isInstanceOf(ClaimBadRequestException.class)
        .hasMessageContaining(claimId.toString());
  }

  // =====================================================
  // Ensure Status Is Not Void Tests
  // =====================================================
  @Nested
  @DisplayName("Ensure status is not VOID tests")
  class EnsureStatusIsNotVoidTests {

    @ParameterizedTest
    @EnumSource(
        value = ClaimStatus.class,
        names = {"VOID"})
    @DisplayName("Should throw when status is VOID")
    void shouldThrowWhenStatusIsVoid(ClaimStatus status) {
      assertThatThrownBy(() -> validationService.ensureStatusIsNotVoid(status))
          .isInstanceOf(ClaimBadRequestException.class)
          .hasMessageContaining(INVALID_CLAIM_STATUS_UPDATE_MESSAGE.formatted("update claim"));
    }

    @ParameterizedTest
    @EnumSource(
        value = ClaimStatus.class,
        names = {"VOID"},
        mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Should not throw for non-VOID statuses")
    void shouldNotThrowForNonVoidStatuses(ClaimStatus status) {
      assertDoesNotThrow(() -> validationService.ensureStatusIsNotVoid(status));
    }
  }
}
