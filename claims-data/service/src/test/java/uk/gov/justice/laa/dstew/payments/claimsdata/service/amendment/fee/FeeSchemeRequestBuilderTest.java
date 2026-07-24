package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationRequest;

class FeeSchemeRequestBuilderTest {

  private final FeeSchemeRequestBuilder builder = new FeeSchemeRequestBuilder();

  @Test
  @DisplayName("Should map post-amendment state directly to FeeCalculationRequest")
  void buildRequest_mapsPostAmendmentState() {
    // Arrange: Build state with fully merged post-amendment snapshot
    ClaimStateSnapshot post =
        FeeSchemeTestDataHelper.createBaseBeforeStateBuilder()
            .feeCode("FEE-AMENDED")
            .netProfitCostsAmount(BigDecimal.valueOf(999.00))
            .travelTime(120)
            .build();

    ClaimAmendmentState state = ClaimAmendmentState.builder().postAmendmentState(post).build();

    // Act
    FeeCalculationRequest request = builder.buildRequest(state);

    // Assert: Verifies direct mapping and type conversions (e.g., BigDecimal -> Double)
    assertThat(request.getFeeCode()).isEqualTo("FEE-AMENDED");
    assertThat(request.getNetProfitCosts()).isEqualTo(999.00);
    assertThat(request.getNetTravelCosts()).isEqualTo(120.00);
  }

  @Test
  @DisplayName("Should safely handle null values during mapping without throwing exceptions")
  void buildRequest_withNulls_mapsSafely() {
    // Arrange: Values missing or explicitly cleared in the post-state
    ClaimStateSnapshot post =
        FeeSchemeTestDataHelper.createBaseBeforeStateBuilder()
            .netProfitCostsAmount(null)
            .travelTime(null)
            .build();

    ClaimAmendmentState state = ClaimAmendmentState.builder().postAmendmentState(post).build();

    // Act
    FeeCalculationRequest request = builder.buildRequest(state);

    // Assert: Maps explicitly to null
    assertThat(request.getNetProfitCosts()).isNull();
    assertThat(request.getNetTravelCosts()).isNull();
  }

  @Test
  @DisplayName("Should correctly map and convert UUID claim ID to String")
  void buildRequest_withClaimId_mapsToString() {
    // Arrange
    UUID claimId = UUID.randomUUID();
    ClaimStateSnapshot post =
        FeeSchemeTestDataHelper.createBaseBeforeStateBuilder().claimId(claimId).build();
    ClaimAmendmentState state = ClaimAmendmentState.builder().postAmendmentState(post).build();

    // Act
    FeeCalculationRequest request = builder.buildRequest(state);

    // Assert
    assertThat(request.getClaimId()).isEqualTo(claimId.toString());
  }

  @Test
  @DisplayName("Should handle missing Claim ID gracefully without NullPointerException")
  void buildRequest_withNullClaimId_mapsToNull() {
    // Arrange
    ClaimStateSnapshot post =
        FeeSchemeTestDataHelper.createBaseBeforeStateBuilder().claimId(null).build();
    ClaimAmendmentState state = ClaimAmendmentState.builder().postAmendmentState(post).build();

    // Act
    FeeCalculationRequest request = builder.buildRequest(state);

    // Assert
    assertThat(request.getClaimId()).isNull();
  }

  @Test
  @DisplayName("Should accurately map zero values across all number types")
  void buildRequest_withZeroValues_mapsCorrectly() {
    // Arrange
    ClaimStateSnapshot post =
        FeeSchemeTestDataHelper.createBaseBeforeStateBuilder()
            .netProfitCostsAmount(BigDecimal.ZERO)
            .travelTime(0)
            .build();
    ClaimAmendmentState state = ClaimAmendmentState.builder().postAmendmentState(post).build();

    // Act
    FeeCalculationRequest request = builder.buildRequest(state);

    // Assert
    assertThat(request.getNetProfitCosts()).isEqualTo(0.0);
    assertThat(request.getNetTravelCosts()).isEqualTo(0.0);
  }

  @Test
  @DisplayName("Should safely build BoltOnType even when all nested bolt-on fields are null")
  void buildRequest_withNullBoltOnFields_buildsEmptyBoltOnType() {
    // Arrange
    ClaimStateSnapshot post =
        FeeSchemeTestDataHelper.createBaseBeforeStateBuilder()
            .adjournedHearingFeeAmount(null)
            .cmrhOralCount(null)
            .cmrhTelephoneCount(null)
            .hoInterview(null)
            .isSubstantiveHearing(null)
            .build();
    ClaimAmendmentState state = ClaimAmendmentState.builder().postAmendmentState(post).build();

    // Act
    FeeCalculationRequest request = builder.buildRequest(state);

    // Assert
    assertThat(request.getBoltOns()).isNotNull();
    assertThat(request.getBoltOns().getBoltOnAdjournedHearing()).isNull();
    assertThat(request.getBoltOns().getBoltOnCmrhOral()).isNull();
    assertThat(request.getBoltOns().getBoltOnCmrhTelephone()).isNull();
    assertThat(request.getBoltOns().getBoltOnHomeOfficeInterview()).isNull();
    assertThat(request.getBoltOns().getBoltOnSubstantiveHearing()).isNull();
  }
}
