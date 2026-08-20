package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.FeeSchemeMapper;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationRequest;

/**
 * Component responsible for building a pristine {@link FeeCalculationRequest} payload to send to
 * the Fee Scheme Platform for repricing.
 *
 * <p>It maps values directly from the fully resolved post-amendment state snapshot.
 */
@Component
@RequiredArgsConstructor
public class FeeSchemeRequestBuilder {

  private final FeeSchemeMapper feeSchemeMapper;

  // No explicit no-arg constructor: use constructor injection. Tests should supply a mapper
  // instance when constructing directly (e.g. via Mappers.getMapper(FeeSchemeMapper.class)).

  /**
   * Builds the calculation request from the current post-amendment state context.
   *
   * @param state the in-memory aggregate describing the amendment in progress
   * @return the fully populated request contract matching the FSP OpenAPI specifications
   */
  public FeeCalculationRequest buildRequest(ClaimAmendmentState state) {
    Objects.requireNonNull(state, "ClaimAmendmentState must not be null");
    ClaimStateSnapshot post = state.getPostAmendmentState();
    Objects.requireNonNull(post, "postAmendmentState must not be null");

    // areaOfLaw must be present; do not default silently
    Objects.requireNonNull(post.getAreaOfLaw(), "areaOfLaw must be present on ClaimStateSnapshot");

    // Delegate full mapping to FeeSchemeMapper (mapper is the source-of-truth)
    FeeCalculationRequest req =
        feeSchemeMapper.mapToFeeCalculationRequest(post, post.getAreaOfLaw());

    // MapStruct should never return null for this mapping. If it does, that's a logical
    // construction failure and we should fail fast.
    if (req == null) {
      throw new IllegalStateException(
          "Unable to build FeeCalculationRequest from post-amendment state: mapper returned null");
    }

    return req;
  }
}
