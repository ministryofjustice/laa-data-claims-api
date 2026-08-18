package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationRequest;

class FeeSchemeRequestBuilderTest {

  private final FeeSchemeRequestBuilder builder =
      new FeeSchemeRequestBuilder(
          Mappers.getMapper(
              uk.gov.justice.laa.dstew.payments.claimsdata.mapper.FeeSchemeMapper.class));

  @Test
  @DisplayName("Should map post-amendment state directly to FeeCalculationRequest")
  void buildRequest_mapsPostAmendmentState() {
    // Arrange: Build state with fully merged post-amendment snapshot
    ClaimStateSnapshot post =
        FeeSchemeTestDataHelper.createBaseBeforeStateBuilder()
            .feeCode("FEE-AMENDED")
            .netProfitCostsAmount(BigDecimal.valueOf(999.00))
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .travelWaitingCostsAmount(BigDecimal.valueOf(120))
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
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
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
        FeeSchemeTestDataHelper.createBaseBeforeStateBuilder()
            .claimId(claimId)
            .areaOfLaw(AreaOfLaw.MEDIATION)
            .build();
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
        FeeSchemeTestDataHelper.createBaseBeforeStateBuilder()
            .claimId(null)
            .areaOfLaw(AreaOfLaw.MEDIATION)
            .build();
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
            .areaOfLaw(AreaOfLaw.CRIME_LOWER)
            .travelWaitingCostsAmount(BigDecimal.ZERO)
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
            .areaOfLaw(AreaOfLaw.MEDIATION)
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

  @Test
  @DisplayName("buildRequest throws when state is null")
  void buildRequest_throwsWhenStateIsNull() {
    assertThatThrownBy(() -> builder.buildRequest(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("ClaimAmendmentState must not be null");
  }

  @Test
  @DisplayName("buildRequest throws when postAmendmentState is null")
  void buildRequest_throwsWhenPostIsNull() {
    ClaimAmendmentState state = ClaimAmendmentState.builder().postAmendmentState(null).build();

    assertThatThrownBy(() -> builder.buildRequest(state))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("postAmendmentState must not be null");
  }

  @Test
  @DisplayName("buildRequest throws when areaOfLaw is missing on post snapshot")
  void buildRequest_throwsWhenAreaOfLawMissing() {
    ClaimStateSnapshot post =
        FeeSchemeTestDataHelper.createBaseBeforeStateBuilder().areaOfLaw(null).build();
    ClaimAmendmentState state = ClaimAmendmentState.builder().postAmendmentState(post).build();

    assertThatThrownBy(() -> builder.buildRequest(state))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("areaOfLaw must be present on ClaimStateSnapshot");
  }

  @Test
  @DisplayName("buildRequest throws IllegalStateException when mapper returns null")
  void buildRequest_throwsWhenMapperReturnsNull() {
    // Arrange: mock a mapper that returns null to simulate mapping failure
    uk.gov.justice.laa.dstew.payments.claimsdata.mapper.FeeSchemeMapper mockMapper =
        org.mockito.Mockito.mock(
            uk.gov.justice.laa.dstew.payments.claimsdata.mapper.FeeSchemeMapper.class);

    FeeSchemeRequestBuilder localBuilder = new FeeSchemeRequestBuilder(mockMapper);

    ClaimStateSnapshot post =
        FeeSchemeTestDataHelper.createBaseBeforeStateBuilder()
            .feeCode("FEE-NULL")
            .areaOfLaw(AreaOfLaw.MEDIATION)
            .build();
    ClaimAmendmentState state = ClaimAmendmentState.builder().postAmendmentState(post).build();

    org.mockito.Mockito.when(mockMapper.mapToFeeCalculationRequest(post, post.getAreaOfLaw()))
        .thenReturn(null);

    // Act / Assert
    assertThatThrownBy(() -> localBuilder.buildRequest(state))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unable to build FeeCalculationRequest");
  }
}
