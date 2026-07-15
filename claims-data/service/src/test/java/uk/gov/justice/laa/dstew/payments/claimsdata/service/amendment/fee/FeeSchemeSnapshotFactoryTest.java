package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.CalculatedFeeDetailSnapshot;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationResponse;

class FeeSchemeSnapshotFactoryTest {

  private final FeeSchemeSnapshotFactory factory = new FeeSchemeSnapshotFactory();

  @Test
  @DisplayName("Should return null when the FSP response is null")
  void toSnapshot_withNullResponse_returnsNull() {
    assertThat(factory.toSnapshot(null)).isNull();
  }

  @Test
  @DisplayName("Should map fully populated nested response successfully")
  void toSnapshot_withFullResponse_mapsAllFields() {
    // Arrange
    FeeCalculationResponse response =
        FeeSchemeTestDataHelper.createMockResponse(500.00, 300.00, 100.00);

    // Act
    CalculatedFeeDetailSnapshot snapshot = factory.toSnapshot(response);

    // Assert
    assertThat(snapshot).isNotNull();
    assertThat(snapshot.getFeeCode()).isEqualTo("FEE001");
    assertThat(snapshot.getSchemeId()).isEqualTo("SCHEME-A");
    assertThat(snapshot.getEscapeCaseFlag()).isFalse();

    // Check BigDecimals converted from Doubles
    assertThat(snapshot.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
    assertThat(snapshot.getNetProfitCostsAmount()).isEqualByComparingTo(BigDecimal.valueOf(300.00));
    assertThat(snapshot.getBoltOnTotalFeeAmount()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
    assertThat(snapshot.getVatIndicator()).isTrue();
  }

  @Test
  @DisplayName("Should handle missing nested feeCalculation or boltOn structures gracefully")
  void toSnapshot_withMissingNestedStructures_doesNotThrowNpe() {
    // Arrange
    FeeCalculationResponse response =
        new FeeCalculationResponse()
            .feeCode("FEE002")
            .feeCalculation(null); // Missing calculation block

    // Act & Assert
    CalculatedFeeDetailSnapshot snapshot = null;
    try {
      snapshot = factory.toSnapshot(response);
    } catch (NullPointerException e) {
      fail("Should not throw NullPointerException on sparse structures");
    }

    assertThat(snapshot).isNotNull();
    assertThat(snapshot.getFeeCode()).isEqualTo("FEE002");
    assertThat(snapshot.getTotalAmount()).isNull();
    assertThat(snapshot.getBoltOnTotalFeeAmount()).isNull();
  }
}
