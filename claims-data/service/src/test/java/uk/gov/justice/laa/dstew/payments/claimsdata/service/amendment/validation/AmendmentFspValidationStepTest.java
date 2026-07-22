package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import uk.gov.justice.laa.dstew.payments.claimsdata.client.FeeSchemePlatformRestClient;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentDiff;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.CalculatedFeeDetailSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.DiffEntry;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimStateSnapshotMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee.FeeSchemeRequestBuilder;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence.AmendmentDiffAssembler;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationRequest;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationResponse;

@ExtendWith(MockitoExtension.class)
class AmendmentFspValidationStepTest {

  @Mock private FeeSchemeRequestBuilder requestBuilder;
  @Mock private FeeSchemePlatformRestClient fspClient;
  @Mock private ClaimStateSnapshotMapper claimStateSnapshotMapper;
  @Mock private AmendmentDiffAssembler diffAssembler;
  @InjectMocks private AmendmentFspValidationStep validationStep;

  private ClaimAmendmentState.ClaimAmendmentStateBuilder stateBuilder;
  private ClaimStateSnapshot.ClaimStateSnapshotBuilder postStateBuilder;
  private ClaimStateSnapshot.ClaimStateSnapshotBuilder beforeStateBuilder;

  @BeforeEach
  void setUp() {
    // Setup clean identical baselines globally
    beforeStateBuilder =
        ClaimStateSnapshot.builder()
            .amended(false)
            .feeCode("FEE01") // Matching initial code
            .calculatedFeeDetail(
                CalculatedFeeDetailSnapshot.builder()
                    .totalAmount(BigDecimal.valueOf(100.00))
                    .build());

    postStateBuilder =
        ClaimStateSnapshot.builder()
            .amended(true)
            .feeCode("FEE01"); // Initially identical so skip tests skip naturally

    stateBuilder = ClaimAmendmentState.builder();

    lenient()
        .when(requestBuilder.buildRequest(any()))
        .thenReturn(new FeeCalculationRequest("FEE01"));

    // Default mock to allow tests to pass the pricing guard by default
    AmendmentDiff defaultDiff =
        AmendmentDiff.of(List.of(new DiffEntry("claim.feeCode", null, "FEE01", "FEE02")));
    lenient().when(diffAssembler.assemble(any(ClaimAmendmentState.class))).thenReturn(defaultDiff);
  }

  @Test
  @DisplayName("1595-B: Should skip execution when proposed post-amendment state is not amended")
  void validate_whenNotAmended_skipsFspCall() {
    // Arrange: Both have feeCode "FEE01", and amended is false
    ClaimAmendmentState state =
        stateBuilder
            .beforeState(beforeStateBuilder.build())
            .postAmendmentState(postStateBuilder.amended(false).build())
            .build();

    AmendmentDiff noChangesDiff = AmendmentDiff.of(List.of());
    when(diffAssembler.assemble(any(ClaimAmendmentState.class))).thenReturn(noChangesDiff);

    // Act
    List<ClaimAmendmentValidationError> errors = validationStep.validate(state);

    // Assert
    assertThat(errors).isEmpty();
    verifyNoInteractions(fspClient);
  }

  @Test
  @DisplayName(
      "1595-B: Should skip execution when baseline state has no calculated fee details snapshot")
  void validate_whenNoBeforeFeeCalculated_skipsFspCall() {
    // Arrange: Explicitly trigger an update code, but missing prior snapshot bypasses the pricing
    // step
    ClaimAmendmentState state =
        stateBuilder
            .beforeState(beforeStateBuilder.calculatedFeeDetail(null).build())
            .postAmendmentState(
                postStateBuilder.feeCode("FEE02").build()) // Specific pricing change
            .build();

    // Act
    List<ClaimAmendmentValidationError> errors = validationStep.validate(state);

    // Assert
    assertThat(errors).isEmpty();
    verifyNoInteractions(fspClient);
  }

  @Test
  @DisplayName(
      "1595-D & F: Should cache FSP response and populate snap-diff blocks on successful request")
  void validate_onSuccess_populatesFspContextAndFeeSnapshots() {
    // Arrange: Introduce a distinct feeCode mutation to force execution past the guard
    ClaimAmendmentState state =
        stateBuilder
            .beforeState(beforeStateBuilder.areaOfLaw(AreaOfLaw.CRIME_LOWER).build())
            .postAmendmentState(
                postStateBuilder.areaOfLaw(AreaOfLaw.CRIME_LOWER).feeCode("FEE02").build())
            .build();

    FeeCalculationResponse mockFspResponse =
        new FeeCalculationResponse().feeCode("FEE02"); // Match request
    CalculatedFeeDetailSnapshot mockAfterSnapshot =
        CalculatedFeeDetailSnapshot.builder().totalAmount(BigDecimal.valueOf(150.00)).build();

    when(fspClient.calculateFee(any())).thenReturn(ResponseEntity.ok(mockFspResponse));
    when(claimStateSnapshotMapper.toSnapshot(mockFspResponse)).thenReturn(mockAfterSnapshot);
    AmendmentDiff pricingImpactingDiff =
        AmendmentDiff.of(List.of(new DiffEntry("claim.feeCode", null, "FEE01", "FEE02")));
    when(diffAssembler.assemble(any(ClaimAmendmentState.class))).thenReturn(pricingImpactingDiff);
    // Act
    List<ClaimAmendmentValidationError> errors = validationStep.validate(state);

    // Assert
    assertThat(errors).isEmpty();
    assertThat(state.getFspResponseContext()).isEqualTo(mockFspResponse);
    assertThat(state.getBeforeFee()).isEqualTo(beforeStateBuilder.build().getCalculatedFeeDetail());
    assertThat(state.getAfterFee()).isEqualTo(mockAfterSnapshot);
  }

  @Test
  @DisplayName(
      "1595-E: Should capture BadRequest (400) rejections and map them to semantic validation errors")
  void validate_onWebClientBadRequestException_returnsFspValidationError() {
    // Arrange: Mutate feeCode explicitly
    ClaimAmendmentState state =
        stateBuilder
            .beforeState(beforeStateBuilder.areaOfLaw(AreaOfLaw.CRIME_LOWER).build())
            .postAmendmentState(
                postStateBuilder.areaOfLaw(AreaOfLaw.CRIME_LOWER).feeCode("FEE02").build())
            .build();

    WebClientResponseException badRequestException =
        WebClientResponseException.create(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            null,
            "FSP Rejected: Invalid combinations".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8);

    when(fspClient.calculateFee(any())).thenThrow(badRequestException);

    // Act
    List<ClaimAmendmentValidationError> errors = validationStep.validate(state);

    // Assert
    assertThat(errors).hasSize(1);
    ClaimAmendmentValidationError error = errors.get(0);
    assertThat(error.getCode())
        .isEqualTo(ClaimAmendmentValidationCode.INVALID_FSP_VALIDATION_FAILURE.toString());
    assertThat(error.getMessage())
        .isEqualTo("The fee calculation failed validation: FSP Rejected: Invalid combinations");
  }

  @Test
  @DisplayName(
      "1595-E: Should capture remote connection or 500 errors and map to controlled tech repricing failure codes")
  void validate_onTechnicalException_returnsRepricingTechnicalError() {
    // Arrange: Mutate feeCode explicitly
    ClaimAmendmentState state =
        stateBuilder
            .beforeState(beforeStateBuilder.areaOfLaw(AreaOfLaw.CRIME_LOWER).build())
            .postAmendmentState(
                postStateBuilder.areaOfLaw(AreaOfLaw.CRIME_LOWER).feeCode("FEE02").build())
            .build();

    when(fspClient.calculateFee(any())).thenThrow(new RuntimeException("SocketTimeoutException"));

    // Act
    List<ClaimAmendmentValidationError> errors = validationStep.validate(state);

    // Assert
    assertThat(errors).hasSize(1);
    ClaimAmendmentValidationError error = errors.get(0);
    assertThat(error.getCode())
        .isEqualTo(ClaimAmendmentValidationCode.TECHNICAL_ERROR_FSP_REPRICING_FAILURE.toString());
  }
}
