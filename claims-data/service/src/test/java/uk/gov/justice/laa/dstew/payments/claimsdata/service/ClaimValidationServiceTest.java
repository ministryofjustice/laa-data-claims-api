package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
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
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@ExtendWith(MockitoExtension.class)
class ClaimValidationServiceTest {

  @Mock private ClaimRepository claimRepository;

  @Mock private ClaimSummaryFeeRepository claimSummaryFeeRepository;

  @InjectMocks private ClaimValidationService validationService;

  // =====================================================
  // Validate User ID Tests
  // =====================================================
  @Nested
  class ValidateUserIdTests {

    @ParameterizedTest
    @MethodSource("invalidUserIds")
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
    void shouldNotThrowWhenUserIdValid() {
      assertDoesNotThrow(() -> validationService.validateUserId(Uuid7.timeBasedUuid().toString()));
    }
  }

  // =====================================================
  // Void Claim Parameter Tests
  // =====================================================
  @ParameterizedTest
  @MethodSource("invalidVoidClaimParameters")
  void shouldThrowWhenVoidClaimParametersInvalid(
      UUID claimId, UUID createdByUserId, String reason, String expectedMessage) {
    assertThatThrownBy(
            () -> validationService.validateVoidClaimParameters(claimId, createdByUserId, reason))
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
  void shouldNotThrowWhenVoidClaimParametersValid() {
    UUID claimId = Uuid7.timeBasedUuid();
    UUID userId = Uuid7.timeBasedUuid();
    String reason = "Valid reason";

    assertDoesNotThrow(
        () -> validationService.validateVoidClaimParameters(claimId, userId, reason));
  }

  // =====================================================
  // Claim Summary Fee Tests
  // =====================================================
  @Test
  void shouldThrowWhenClaimSummaryFeeNotFound() {
    UUID claimId = Uuid7.timeBasedUuid();
    when(claimSummaryFeeRepository.findByClaimId(claimId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> validationService.getClaimSummaryFeeByClaimIdOrThrow(claimId))
        .isInstanceOf(ClaimSummaryFeeNotFoundException.class)
        .hasMessageContaining(claimId.toString());

    verify(claimSummaryFeeRepository).findByClaimId(claimId);
  }

  @Test
  void shouldReturnClaimSummaryFeeWhenExists() {
    UUID claimId = Uuid7.timeBasedUuid();
    ClaimSummaryFee fee = new ClaimSummaryFee();
    when(claimSummaryFeeRepository.findByClaimId(claimId)).thenReturn(Optional.of(fee));

    ClaimSummaryFee result = validationService.getClaimSummaryFeeByClaimIdOrThrow(claimId);
    assertThat(result).isSameAs(fee);
  }

  @Test
  void shouldThrowWhenClaimSummaryFeeDoesNotExistById() {
    UUID feeId = Uuid7.timeBasedUuid();
    when(claimSummaryFeeRepository.existsById(feeId)).thenReturn(false);

    assertThatThrownBy(() -> validationService.getClaimSummaryFeeByIdOrThrow(feeId))
        .isInstanceOf(ClaimSummaryFeeNotFoundException.class)
        .hasMessageContaining(feeId.toString());
  }

  @Test
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
  void shouldThrowWhenClaimNotFound() {
    UUID claimId = Uuid7.timeBasedUuid();
    when(claimRepository.findById(claimId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> validationService.getValidClaimOrThrow(claimId))
        .isInstanceOf(ClaimNotFoundException.class)
        .hasMessageContaining(claimId.toString());
  }

  @Test
  void shouldReturnClaimWhenValid() {
    UUID claimId = Uuid7.timeBasedUuid();
    Claim claim = Claim.builder().id(claimId).status(ClaimStatus.VALID).build();
    when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));

    Claim result = validationService.getValidClaimOrThrow(claimId);
    assertThat(result).isSameAs(claim);
  }

  // =====================================================
  // Assessment Type Tests
  // =====================================================
  @Test
  void shouldThrowWhenAssessmentTypeIsVoid() {
    assertThatThrownBy(() -> validationService.ensureAssessmentTypeIsNotVoid(AssessmentType.VOID))
        .isInstanceOf(ClaimBadRequestException.class);
  }

  @Test
  void shouldNotThrowForOtherAssessmentTypes() {
    assertDoesNotThrow(
        () ->
            validationService.ensureAssessmentTypeIsNotVoid(AssessmentType.ESCAPE_CASE_ASSESSMENT));
  }

  // =====================================================
  // Claim Status Tests
  // =====================================================
  @ParameterizedTest
  @EnumSource(
      value = ClaimStatus.class,
      names = {"VALID"},
      mode = EnumSource.Mode.EXCLUDE)
  void shouldThrowWhenClaimDoesNotHaveValidStatus(ClaimStatus status) {
    UUID claimId = Uuid7.timeBasedUuid();
    Claim claim = Claim.builder().id(claimId).status(status).build();

    assertThatThrownBy(() -> validationService.ensureClaimIsValid(claim))
        .isInstanceOf(ClaimBadRequestException.class)
        .hasMessageContaining(claimId.toString());
  }
}
