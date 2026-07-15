package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import uk.gov.justice.laa.dstew.payments.claimsdata.client.FeeSchemePlatformRestClient;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.CalculatedFeeDetailSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee.FeeSchemeRequestBuilder;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee.FeeSchemeSnapshotFactory;
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
  private final FeeSchemeSnapshotFactory snapshotFactory;

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
    // 1595-B: Guard check using pre-derived trigger logic
    boolean requiresRepricing =
        state.getPostAmendmentState().isAmended()
            && state.getBeforeState().getCalculatedFeeDetail() != null;

    if (!requiresRepricing) {
      log.debug("No pricing-impacting changes discovered. Skipping FSP call.");
      return List.of();
    }

    try {
      // 1595-C: Generate payload
      // 1595-D: Dispatch synchronous timeout-protected request
      FeeCalculationResponse fspResponse =
          fspClient.calculateFee(requestBuilder.buildRequest(state)).getBody();
      state.setFspResponseContext(fspResponse);

      // 1595-F: Populate snap containers into state slots for historical audit tracking
      CalculatedFeeDetailSnapshot beforeFeeSnapshot =
          state.getBeforeState().getCalculatedFeeDetail();
      CalculatedFeeDetailSnapshot afterFeeSnapshot = snapshotFactory.toSnapshot(fspResponse);

      state.setBeforeFee(beforeFeeSnapshot);
      state.setAfterFee(afterFeeSnapshot);

    } catch (WebClientResponseException.BadRequest ex) {
      // 1595-E: Catch semantic rejections
      log.warn("FSP validation rejected payload: {}", ex.getResponseBodyAsString());
      return List.of(
          ClaimAmendmentValidationError.of(
              ClaimAmendmentValidationCode.INVALID_FSP_VALIDATION_FAILURE,
              ex.getResponseBodyAsString()));

    } catch (Exception ex) {
      // 1595-E: Catch technical timeouts or connection exceptions
      log.error("FSP call experienced a technical error or execution timeout", ex);
      return List.of(
          ClaimAmendmentValidationError.of(
              ClaimAmendmentValidationCode.TECHNICAL_ERROR_FSP_REPRICING_FAILURE));
    }

    return List.of();
  }
}
