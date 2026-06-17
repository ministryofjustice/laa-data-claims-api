package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentEligibilityError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentErrorCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

/**
 * Tests for {@link ClaimAmendmentEligibilityValidator}.
 *
 * <p>The gate is a pure function with no repositories or clients, so "no PDA/FSP call and no
 * persistence on rejection" holds by construction - there is nothing for it to invoke. These tests
 * pin the eligible/ineligible outcomes and error codes.
 */
@DisplayName("ClaimAmendmentEligibilityValidator Tests")
class ClaimAmendmentEligibilityValidatorTest {

  private final ClaimAmendmentEligibilityValidator validator =
      new ClaimAmendmentEligibilityValidator();

  @Test
  @DisplayName("VALID claim is eligible and can proceed")
  void validClaimIsEligible() {
    assertThat(validator.checkEligibility(ClaimStatus.VALID)).isEmpty();
  }

  @Test
  @DisplayName("Voided claim is rejected with INVALID_VOIDED_CLAIM_NOT_AMENDABLE")
  void voidedClaimRejected() {
    Optional<ClaimAmendmentEligibilityError> result = validator.checkEligibility(ClaimStatus.VOID);

    assertThat(result).isPresent();
    ClaimAmendmentEligibilityError error = result.get();
    assertThat(error.getCode())
        .isEqualTo(ClaimAmendmentErrorCode.INVALID_VOIDED_CLAIM_NOT_AMENDABLE);
    assertThat(error.getClaimStatus()).isEqualTo(ClaimStatus.VOID);
    assertThat(error.getMessage())
        .isEqualTo(ClaimAmendmentEligibilityValidator.VOIDED_CLAIM_MESSAGE);
  }

  @ParameterizedTest
  @EnumSource(
      value = ClaimStatus.class,
      names = {"READY_TO_PROCESS", "INVALID"})
  @DisplayName("Other non-VALID statuses are rejected with INVALID_CLAIM_STATE_NOT_AMENDABLE")
  void otherNonValidStatusRejected(ClaimStatus status) {
    Optional<ClaimAmendmentEligibilityError> result = validator.checkEligibility(status);

    assertThat(result).isPresent();
    ClaimAmendmentEligibilityError error = result.get();
    assertThat(error.getCode())
        .isEqualTo(ClaimAmendmentErrorCode.INVALID_CLAIM_STATE_NOT_AMENDABLE);
    // the current status is carried through for the structured response
    assertThat(error.getClaimStatus()).isEqualTo(status);
    assertThat(error.getMessage()).contains(String.valueOf(status));
  }
}
