package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.CalculatedFeeDetailSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence.AmendmentChangeDetector;

@DisplayName("AssessedClaimPricingValidationStep")
class AssessedClaimPricingValidationStepTest {

  @Test
  @DisplayName("Assessed claim with pricing-impacting change -> fatal error")
  void assessedClaimWithPricingChangeFails() {
    ClaimStateSnapshot before =
        ClaimStateSnapshot.builder()
            .hasAssessment(true)
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .netProfitCostsAmount(BigDecimal.valueOf(100))
            .build();

    ClaimStateSnapshot after =
        ClaimStateSnapshot.builder()
            .hasAssessment(true)
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .netProfitCostsAmount(BigDecimal.valueOf(200))
            .build();

    ClaimAmendmentState state =
        ClaimAmendmentState.builder().beforeState(before).postAmendmentState(after).build();

    AssessedClaimPricingValidationStep step =
        new AssessedClaimPricingValidationStep(new AmendmentChangeDetector());
    List<ClaimAmendmentValidationError> errors = step.validate(state);

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).getCode())
        .isEqualTo(
            ClaimAmendmentValidationCode.INVALID_PRICING_AMENDMENT_ON_ASSESSED_CLAIM.toString());
    assertThat(errors.get(0).isFatal()).isTrue();
  }

  @Test
  @DisplayName("Assessed claim with non-pricing change -> passes")
  void assessedClaimWithNonPricingChangePasses() {
    ClaimStateSnapshot before =
        ClaimStateSnapshot.builder()
            .hasAssessment(true)
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .clientSurname("Jones")
            .build();
    ClaimStateSnapshot after =
        ClaimStateSnapshot.builder()
            .hasAssessment(true)
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .clientSurname("Smith")
            .build();

    ClaimAmendmentState state =
        ClaimAmendmentState.builder().beforeState(before).postAmendmentState(after).build();

    AssessedClaimPricingValidationStep step =
        new AssessedClaimPricingValidationStep(new AmendmentChangeDetector());
    List<ClaimAmendmentValidationError> errors = step.validate(state);

    assertThat(errors).isEmpty();
  }

  @Test
  @DisplayName("Unassessed claim with pricing change -> passes")
  void unassessedClaimWithPricingChangePasses() {
    ClaimStateSnapshot before =
        ClaimStateSnapshot.builder()
            .hasAssessment(false)
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .netProfitCostsAmount(BigDecimal.valueOf(100))
            .build();

    ClaimStateSnapshot after =
        ClaimStateSnapshot.builder()
            .hasAssessment(false)
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .netProfitCostsAmount(BigDecimal.valueOf(200))
            .build();

    ClaimAmendmentState state =
        ClaimAmendmentState.builder().beforeState(before).postAmendmentState(after).build();

    AssessedClaimPricingValidationStep step =
        new AssessedClaimPricingValidationStep(new AmendmentChangeDetector());
    List<ClaimAmendmentValidationError> errors = step.validate(state);

    assertThat(errors).isEmpty();
  }

  @Test
  @DisplayName("FSP-sourced fee change on an assessed claim -> ignored by this rule")
  void fspFeeChangeOnAssessedClaimIsIgnored() {
    // before-state indicates the claim is assessed so the rule would normally apply to REQUESTED
    // changes, but FSP-sourced changes must be ignored.
    ClaimStateSnapshot before =
        ClaimStateSnapshot.builder().hasAssessment(true).areaOfLaw(AreaOfLaw.CRIME_LOWER).build();

    // Fee snapshots differ to produce an FSP-tagged diff entry from the change detector.
    var beforeFee =
        CalculatedFeeDetailSnapshot.builder().totalAmount(BigDecimal.valueOf(100)).build();
    var afterFee =
        CalculatedFeeDetailSnapshot.builder().totalAmount(BigDecimal.valueOf(150)).build();

    ClaimAmendmentState state =
        ClaimAmendmentState.builder()
            .beforeState(before)
            .beforeFee(beforeFee)
            .afterFee(afterFee)
            .build();

    AssessedClaimPricingValidationStep step =
        new AssessedClaimPricingValidationStep(new AmendmentChangeDetector());
    List<ClaimAmendmentValidationError> errors = step.validate(state);

    // The fee change is tagged FSP by the detector and must not trigger the assessed-claim pricing
    // validation error, so no errors should be returned.
    assertThat(errors).isEmpty();
  }
}
