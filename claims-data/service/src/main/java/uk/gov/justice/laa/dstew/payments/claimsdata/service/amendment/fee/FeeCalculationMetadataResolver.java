package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ResolvedClaimData;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.CalculatedFeeDetailSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationType;

/**
 * Resolves fee metadata for amendment repricing from the validation-core {@link ResolvedClaimData}
 * cached on the amendment state, falling back to the previous fee snapshot when the fee code is
 * unchanged.
 *
 * <p>Since validation-core 1.4.9, {@code feeCalculationType}, {@code authorisedCategoryOfLawCode}
 * and {@code feeCodeDescription} are all surfaced on {@link ResolvedClaimData}, so no additional
 * FSP {@code /fee-details} lookup is required in the amendment path.
 */
@Component
@Slf4j
public class FeeCalculationMetadataResolver {

  /**
   * Resolve all fee metadata fields needed by amendment repricing in a single call so callers can
   * pass a single {@link ResolvedFeeMetadata} to the mapper.
   *
   * @param state amendment state carrying cached {@link ResolvedClaimData}
   * @param feeCode the repriced fee code
   * @return the resolved metadata (fields may be {@code null} when no source supplies a value)
   */
  public ResolvedFeeMetadata resolve(ClaimAmendmentState state, String feeCode) {
    return new ResolvedFeeMetadata(
        resolveFeeType(state, feeCode),
        resolveFeeCodeDescription(state, feeCode),
        resolveCategoryOfLaw(state, feeCode));
  }

  private FeeCalculationType resolveFeeType(ClaimAmendmentState state, String feeCode) {
    ResolvedClaimData resolved = resolvedClaimData(state);
    String fromResolvedData = resolved == null ? null : resolved.feeCalculationType();
    String fromPreviousFee =
        canReusePreviousMetadata(state, feeCode) && previousFee(state).getFeeType() != null
            ? previousFee(state).getFeeType().getValue()
            : null;
    return parseFeeType(firstNonBlank(fromResolvedData, fromPreviousFee));
  }

  private String resolveFeeCodeDescription(ClaimAmendmentState state, String feeCode) {
    ResolvedClaimData resolved = resolvedClaimData(state);
    return firstNonBlank(
        resolved == null ? null : resolved.feeCodeDescription(),
        canReusePreviousMetadata(state, feeCode)
            ? previousFee(state).getFeeCodeDescription()
            : null);
  }

  private String resolveCategoryOfLaw(ClaimAmendmentState state, String feeCode) {
    ResolvedClaimData resolved = resolvedClaimData(state);
    String previousCategoryOfLaw =
        canReusePreviousMetadata(state, feeCode) ? previousFee(state).getCategoryOfLaw() : null;
    return firstNonBlank(
        resolved == null ? null : resolved.authorisedCategoryOfLawCode(), previousCategoryOfLaw);
  }

  private ResolvedClaimData resolvedClaimData(ClaimAmendmentState state) {
    return state == null ? null : state.getResolvedClaimDataContext();
  }

  private CalculatedFeeDetailSnapshot previousFee(ClaimAmendmentState state) {
    return state == null || state.getBeforeState() == null
        ? null
        : state.getBeforeState().getCalculatedFeeDetail();
  }

  private boolean canReusePreviousMetadata(ClaimAmendmentState state, String feeCode) {
    CalculatedFeeDetailSnapshot previousFee = previousFee(state);
    return previousFee != null && Objects.equals(previousFee.getFeeCode(), feeCode);
  }

  private FeeCalculationType parseFeeType(String rawFeeType) {
    if (rawFeeType == null) {
      return null;
    }
    try {
      return FeeCalculationType.fromValue(rawFeeType);
    } catch (IllegalArgumentException ex) {
      log.warn("Unable to map fee calculation type '{}' onto FeeCalculationType", rawFeeType);
      return null;
    }
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }
}
