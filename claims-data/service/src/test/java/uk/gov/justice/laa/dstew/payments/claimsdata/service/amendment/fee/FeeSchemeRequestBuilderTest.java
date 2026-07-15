package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationRequest;

class FeeSchemeRequestBuilderTest {

  private final FeeSchemeRequestBuilder builder = new FeeSchemeRequestBuilder();

  @Test
  @DisplayName("Should use fallback snapshot values when the amendment payload is sparse/undefined")
  void buildRequest_withUndefinedPayload_usesBeforeStateSnapshot() {
    // Arrange: Build state with empty patch payload
    ClaimStateSnapshot before = FeeSchemeTestDataHelper.createBaseBeforeStateBuilder().build();
    ClaimAmendmentPayload patch =
        ClaimAmendmentPayload.builder().build(); // Omitted fields default to undefined

    ClaimAmendmentState state =
        ClaimAmendmentState.builder().beforeState(before).requestPayload(patch).build();

    // Act
    FeeCalculationRequest request = builder.buildRequest(state);

    // Assert: Verifies fallbacks mapped correctly
    assertThat(request.getFeeCode()).isEqualTo("FEE001");
    assertThat(request.getStartDate()).isEqualTo(before.getCaseStartDate());
    assertThat(request.getNetProfitCosts()).isEqualTo(150.00);
    assertThat(request.getNetTravelCosts()).isEqualTo(120.00);
  }

  @Test
  @DisplayName("Should override fallback values when amendment payload contains updates")
  void buildRequest_withPayloadValueUpdates_overridesSnapshot() {
    // Arrange: Build state with defined patch values
    ClaimStateSnapshot before = FeeSchemeTestDataHelper.createBaseBeforeStateBuilder().build();
    ClaimAmendmentPayload patch =
        ClaimAmendmentPayload.builder()
            .feeCode(JsonNullable.of("FEE-AMENDED"))
            .netProfitCostsAmount(JsonNullable.of(BigDecimal.valueOf(999.00)))
            .build();

    ClaimAmendmentState state =
        ClaimAmendmentState.builder().beforeState(before).requestPayload(patch).build();

    // Act
    FeeCalculationRequest request = builder.buildRequest(state);

    // Assert: Overridden values verified
    assertThat(request.getFeeCode()).isEqualTo("FEE-AMENDED");
    assertThat(request.getNetProfitCosts()).isEqualTo(999.00);

    // Omitted fields should still fall back
    assertThat(request.getNetTravelCosts()).isEqualTo(120.00);
  }

  @Test
  @DisplayName("Should respect and map explicit null requests from the provider")
  void buildRequest_withExplicitNullPayload_clearsValueInRequest() {
    // Arrange: Value explicitly cleared
    ClaimStateSnapshot before = FeeSchemeTestDataHelper.createBaseBeforeStateBuilder().build();
    ClaimAmendmentPayload patch =
        ClaimAmendmentPayload.builder()
            .netProfitCostsAmount(JsonNullable.of(null)) // Requested clear
            .build();

    ClaimAmendmentState state =
        ClaimAmendmentState.builder().beforeState(before).requestPayload(patch).build();

    // Act
    FeeCalculationRequest request = builder.buildRequest(state);

    // Assert: Maps explicitly to null, ignoring the fallback value
    assertThat(request.getNetProfitCosts()).isNull();
  }
}
