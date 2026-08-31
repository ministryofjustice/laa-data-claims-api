package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import uk.gov.justice.laa.dstew.payments.claimsdata.client.FeeSchemePlatformRestClient;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.AmendmentDiff;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.CalculatedFeeDetailSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimStateSnapshotMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee.FeeCalculationMetadataResolver;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee.FeeSchemeRequestBuilder;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee.FeeSchemeRequestField;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence.AmendmentDiffAssembler;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationResponse;

/**
 * Fee Scheme Platform (FSP) validation step responsible for orchestrating claim repricing during
 * the amendment workflow pipeline.
 *
 * <p>Modelled directly as an inline validation step inside {@code
 * ClaimAmendmentValidationService.STEP_ORDER}, this component encapsulates the trigger
 * determination, request payload construction, synchronous remote call, error translation, and
 * success state handoff to downstream layers.
 *
 * <p><b>Transaction Boundary Management:</b> Following the non-transactional requirement for Phase
 * 2 (Validate), this step executes with <b>no held transaction</b>. Isolating the remote HTTP
 * network call outside of a persistence context prevents database connections or row-level locks
 * from being held open during external network I/O, completely avoiding thread exhaustion inside
 * the connection pool.
 *
 * <p><b>DSTEW-1595 Core Subtask Compliances:</b>
 *
 * <ul>
 *   <li><b>1595-B (Trigger Consumption):</b> Assesses if the amendment has pricing-impacting
 *       updates and short-circuits safely if the baseline state parameters indicate no repricing is
 *       required.
 *   <li><b>1595-C (Request Builder):</b> Leverages {@link FeeSchemeRequestBuilder} to compile a
 *       sparse-merged input payload uniting post-amendment updates with baseline values.
 *   <li><b>1595-D (Synchronous Mechanics):</b> Invokes the declarative REST interface via a single,
 *       synchronous blocking call configured with an independent, user-facing path timeout control.
 *   <li><b>1595-E (Response & Failure Mapping):</b> Translates semantic FSP contract errors into
 *       structured validation rejections, and treats connectivity failures or execution timeouts as
 *       controlled technical exceptions.
 *   <li><b>1595-F (Outcome Persistence Handoff):</b> Caches the resulting successful {@link
 *       FeeCalculationResponse} onto the transient state context, and pushes unwrapped historical
 *       diff snapshots into the state slots for audit generation.
 * </ul>
 *
 * @see
 *     uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.ClaimAmendmentValidationService
 * @see uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState
 * @see uk.gov.justice.laa.dstew.payments.claimsdata.client.FeeSchemePlatformRestClient
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AmendmentFspValidationStep implements ClaimAmendmentValidationStep {

  private final FeeSchemeRequestBuilder requestBuilder;
  private final FeeSchemePlatformRestClient fspClient;
  private final AmendmentDiffAssembler diffAssembler;
  private final ClaimStateSnapshotMapper claimStateSnapshotMapper;
  private final FeeCalculationMetadataResolver feeCalculationMetadataResolver;

  /**
   * Executes the trigger verification and processes the remote FSP recalculation sequence.
   *
   * <p>If pricing-impacting triggers are absent, it passes through silently without adding error
   * context. If an update is triggered, it fires a blocking HTTP request, maps the
   * successes/failures, and stores the response context in the in-memory aggregate state.
   *
   * @param state the in-memory {@link ClaimAmendmentState} aggregate containing the proposed
   *     modifications and baseline state
   * @return a {@link List} containing any structural validation errors or technical failure codes
   *     captured during execution; an empty list represents an entirely successful passthrough
   */
  @Override
  public List<ClaimAmendmentValidationError> validate(ClaimAmendmentState state) {
    // Local amendability precondition (no external call): the before-state must carry Calculated
    // Fee Details for the claim to be repriceable. Because this is the ONLY place this check is
    // produced, it must run before the outcome-check gate below - otherwise, when an earlier step
    // has already collected another (non-fatal) message, this validation signal would be silently
    // dropped from the aggregated response. Running it first lets it contribute to aggregation
    // while still making no outbound FSP call.
    if (state.getBeforeState().getCalculatedFeeDetail() == null) {
      String claimId =
          state.getBeforeState().getClaimId() != null
              ? state.getBeforeState().getClaimId().toString()
              : "unknown";

      log.warn("Claim status {} is not amendable; Calculated Fee Details missing.", claimId);

      return List.of(
          ClaimAmendmentValidationError.of(
              ClaimAmendmentValidationCode.INVALID_CLAIM_BEFORE_STATE_CFD_MISSING, claimId));
    }

    // Outcome-check gate (DSTEW-1770): if any earlier step has already collected a validation
    // error, the amendment is going to be rejected regardless, so the (external) Fee Scheme
    // Platform call must not be made. The orchestrator only short-circuits on *fatal* errors, so
    // this guard is what enforces "no FSP call when any collected validation message exists" for
    // the non-fatal, aggregated case too. Returning an empty list adds nothing and keeps the
    // already-collected errors intact for the aggregated response.
    if (!state.getErrors().isEmpty()) {
      log.debug(
          "Skipping FSP call: {} validation error(s) already collected.", state.getErrors().size());
      return List.of();
    }

    AmendmentDiff differences = diffAssembler.assemble(state);
    if (differences == null
        || differences.changes() == null
        || !hasPricingImpactingChanges(
            differences, state.getBeforeState(), state.getPostAmendmentState())) {
      log.debug("No pricing-impacting changes discovered. Skipping FSP call.");
      return List.of();
    }

    try {
      // 1595-C: Generate payload
      // 1595-D: Dispatch synchronous timeout-protected request
      FeeCalculationResponse fspResponse =
          Objects.requireNonNull(
              fspClient.calculateFee(requestBuilder.buildRequest(state)).getBody(),
              "FSP calculateFee returned a null response body");
      state.setFspResponseContext(fspResponse);

      // 1595-F: Populate snap containers into state slots for historical audit tracking
      CalculatedFeeDetailSnapshot beforeFeeSnapshot =
          state.getBeforeState().getCalculatedFeeDetail();
      CalculatedFeeDetailSnapshot afterFeeSnapshot =
          enrichAfterFeeSnapshot(
              claimStateSnapshotMapper.toSnapshot(fspResponse), state, fspResponse);

      state.setBeforeFee(beforeFeeSnapshot);
      state.setAfterFee(afterFeeSnapshot);

    } catch (WebClientResponseException.BadRequest ex) {
      // 1595-E: Catch semantic rejections
      log.warn("FSP validation rejected payload: {}", ex.getResponseBodyAsString());
      return List.of(
          ClaimAmendmentValidationError.of(
              ClaimAmendmentValidationCode.INVALID_FSP_VALIDATION_FAILURE,
              ex.getResponseBodyAsString()));
    } catch (IllegalStateException ex) {
      // Failures originating from request construction indicate invalid or incomplete input
      // and should be reported as semantic validation rejections rather than technical faults.
      log.warn("Failed to build FSP request: {}", ex.getMessage());
      return List.of(
          ClaimAmendmentValidationError.of(
              ClaimAmendmentValidationCode.INVALID_FSP_VALIDATION_FAILURE, ex.getMessage()));

    } catch (Exception ex) {
      // 1595-E: Catch technical timeouts or connection exceptions
      log.error("FSP call experienced a technical error or execution timeout", ex);
      return List.of(
          ClaimAmendmentValidationError.of(
              ClaimAmendmentValidationCode.TECHNICAL_ERROR_FSP_REPRICING_FAILURE));
    }

    return List.of();
  }

  private CalculatedFeeDetailSnapshot enrichAfterFeeSnapshot(
      CalculatedFeeDetailSnapshot baseSnapshot,
      ClaimAmendmentState state,
      FeeCalculationResponse fspResponse) {
    if (baseSnapshot == null) {
      return null;
    }

    String feeCode = fspResponse == null ? null : fspResponse.getFeeCode();

    return CalculatedFeeDetailSnapshot.builder()
        .feeCode(baseSnapshot.getFeeCode())
        .feeType(feeCalculationMetadataResolver.resolveFeeType(state, feeCode))
        .feeCodeDescription(
            feeCalculationMetadataResolver.resolveFeeCodeDescription(state, feeCode))
        .categoryOfLaw(feeCalculationMetadataResolver.resolveCategoryOfLaw(state, feeCode))
        .totalAmount(baseSnapshot.getTotalAmount())
        .vatIndicator(baseSnapshot.getVatIndicator())
        .vatRateApplied(baseSnapshot.getVatRateApplied())
        .calculatedVatAmount(baseSnapshot.getCalculatedVatAmount())
        .disbursementAmount(baseSnapshot.getDisbursementAmount())
        .requestedNetDisbursementAmount(baseSnapshot.getRequestedNetDisbursementAmount())
        .disbursementVatAmount(baseSnapshot.getDisbursementVatAmount())
        .hourlyTotalAmount(baseSnapshot.getHourlyTotalAmount())
        .fixedFeeAmount(baseSnapshot.getFixedFeeAmount())
        .netProfitCostsAmount(baseSnapshot.getNetProfitCostsAmount())
        .requestedNetProfitCostsAmount(baseSnapshot.getRequestedNetProfitCostsAmount())
        .netCostOfCounselAmount(baseSnapshot.getNetCostOfCounselAmount())
        .netTravelCostsAmount(baseSnapshot.getNetTravelCostsAmount())
        .netWaitingCostsAmount(baseSnapshot.getNetWaitingCostsAmount())
        .detentionTravelAndWaitingCostsAmount(
            baseSnapshot.getDetentionTravelAndWaitingCostsAmount())
        .jrFormFillingAmount(baseSnapshot.getJrFormFillingAmount())
        .travelAndWaitingCostsAmount(baseSnapshot.getTravelAndWaitingCostsAmount())
        .boltOnTotalFeeAmount(baseSnapshot.getBoltOnTotalFeeAmount())
        .boltOnAdjournedHearingCount(baseSnapshot.getBoltOnAdjournedHearingCount())
        .boltOnAdjournedHearingFee(baseSnapshot.getBoltOnAdjournedHearingFee())
        .boltOnCmrhTelephoneCount(baseSnapshot.getBoltOnCmrhTelephoneCount())
        .boltOnCmrhTelephoneFee(baseSnapshot.getBoltOnCmrhTelephoneFee())
        .boltOnCmrhOralCount(baseSnapshot.getBoltOnCmrhOralCount())
        .boltOnCmrhOralFee(baseSnapshot.getBoltOnCmrhOralFee())
        .boltOnHomeOfficeInterviewCount(baseSnapshot.getBoltOnHomeOfficeInterviewCount())
        .boltOnHomeOfficeInterviewFee(baseSnapshot.getBoltOnHomeOfficeInterviewFee())
        .boltOnSubstantiveHearingFee(baseSnapshot.getBoltOnSubstantiveHearingFee())
        .escapeCaseFlag(baseSnapshot.getEscapeCaseFlag())
        .schemeId(baseSnapshot.getSchemeId())
        .build();
  }

  private boolean hasPricingImpactingChanges(
      AmendmentDiff diff, ClaimStateSnapshot before, ClaimStateSnapshot post) {

    if (before == null || post == null || before.getAreaOfLaw() == null) {
      return false;
    }

    return diff.changes().stream()
        .anyMatch(
            entry ->
                isChangedAndImpactsPricing(
                    entry.before(), entry.after(), entry.fieldIdentifier(), before.getAreaOfLaw()));
  }

  private boolean isChangedAndImpactsPricing(
      Object beforeVal, Object postVal, String fieldIdentifier, AreaOfLaw areaOfLaw) {
    if (Objects.equals(beforeVal, postVal)) {
      return false;
    }
    return FeeSchemeRequestField.impactsPricing(fieldIdentifier, areaOfLaw);
  }
}
