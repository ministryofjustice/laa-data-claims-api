package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.ClaimSearchRequest;

@DisplayName("DataNormaliser Tests")
class DataNormaliserTest {

  @Test
  @DisplayName("normaliseClaimSearchRequest should be no-op for null request")
  void normalise_nullRequest_noException() {
    // Should not throw
    DataNormaliser.normaliseClaimSearchRequest(null);
  }

  @Test
  @DisplayName(
      "normaliseClaimSearchRequest should trim caseReferenceNumber and preserve other fields")
  void normalise_trimsCaseReferenceAndPreservesOtherFields() {
    ClaimSearchRequest req =
        ClaimSearchRequest.builder().officeCode("OFF1").caseReferenceNumber("  ABC-1234  ").build();

    DataNormaliser.normaliseClaimSearchRequest(req);

    assertThat(req.getCaseReferenceNumber()).isEqualTo("ABC-1234");
    assertThat(req.getOfficeCode()).isEqualTo("OFF1");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t\n"})
  @DisplayName("normaliseClaimSearchRequest should convert blank caseReferenceNumber to null")
  void normalise_blankCaseReferenceBecomesNull(String value) {
    ClaimSearchRequest req = ClaimSearchRequest.builder().caseReferenceNumber(value).build();

    DataNormaliser.normaliseClaimSearchRequest(req);

    assertThat(req.getCaseReferenceNumber()).isNull();
  }

  @Test
  @DisplayName("normaliseClaimSearchRequest is idempotent")
  void normalise_idempotent() {
    ClaimSearchRequest req = ClaimSearchRequest.builder().caseReferenceNumber("  XYZ  ").build();

    DataNormaliser.normaliseClaimSearchRequest(req);
    assertThat(req.getCaseReferenceNumber()).isEqualTo("XYZ");

    // Call again - result should be the same
    DataNormaliser.normaliseClaimSearchRequest(req);
    assertThat(req.getCaseReferenceNumber()).isEqualTo("XYZ");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"abc", "  abc  "})
  @DisplayName("trimToNull should return null for null/empty and trimmed value otherwise")
  void trimToNull_behaviour(String input) {
    String result = DataNormaliser.trimToNull(input);
    if (input == null || input.trim().isEmpty()) {
      assertThat(result).isNull();
    } else {
      assertThat(result).isEqualTo(input.trim());
    }
  }
}
