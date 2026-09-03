package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ResolvedClaimData;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.CalculatedFeeDetailSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationType;
import uk.gov.justice.laa.fee.scheme.model.FeeDetailsResponseV2;

/** Resolves fee metadata for amendment repricing from cached validation/enrichment state. */
@Component
@Slf4j
public class FeeCalculationMetadataResolver {

  /**
   * Resolve all fee metadata fields needed by amendment repricing in a single call so callers can
   * pass a single {@link ResolvedFeeMetadata} to the mapper.
   *
   * @param state amendment state carrying cached enrichment context
   * @param feeCode the repriced fee code
   * @return the resolved metadata (fields may be {@code null} when no source supplies a value)
   */
  public ResolvedFeeMetadata resolve(ClaimAmendmentState state, String feeCode) {
    return new ResolvedFeeMetadata(
        resolveFeeType(state, feeCode),
        resolveFeeCodeDescription(state, feeCode),
        resolveCategoryOfLaw(state, feeCode));
  }

  /**
   * Resolve the calculated fee type for the amendment repricing result.
   *
   * @param state amendment state carrying cached enrichment context
   * @param feeCode the repriced fee code
   * @return the resolved fee type, or {@code null} when no supported value is available
   */
  private FeeCalculationType resolveFeeType(ClaimAmendmentState state, String feeCode) {
    return parseFeeType(
        firstNonBlank(
            resolvedClaimData(state) == null ? null : resolvedClaimData(state).feeCalculationType(),
            feeSchemeDetails(state) == null ? null : feeSchemeDetails(state).getFeeType(),
            canReusePreviousMetadata(state, feeCode) && previousFee(state).getFeeType() != null
                ? previousFee(state).getFeeType().getValue()
                : null));
  }

  /**
   * Resolve the fee code description for the amendment repricing result.
   *
   * @param state amendment state carrying cached enrichment context
   * @param feeCode the repriced fee code
   * @return the resolved description, or {@code null} when none is available
   */
  private String resolveFeeCodeDescription(ClaimAmendmentState state, String feeCode) {
    return firstNonBlank(
        feeSchemeDetails(state) == null ? null : feeSchemeDetails(state).getFeeCodeDescription(),
        canReusePreviousMetadata(state, feeCode)
            ? previousFee(state).getFeeCodeDescription()
            : null);
  }

  /**
   * Resolve the category of law code for the amendment repricing result.
   *
   * @param state amendment state carrying cached enrichment context
   * @param feeCode the repriced fee code
   * @return the resolved category of law code, preferring the validated claim value, then any
   *     unchanged persisted value, and finally the fee-scheme category list when no prior value is
   *     available
   */
  private String resolveCategoryOfLaw(ClaimAmendmentState state, String feeCode) {
    String previousCategoryOfLaw =
        canReusePreviousMetadata(state, feeCode) ? previousFee(state).getCategoryOfLaw() : null;
    return firstNonBlank(
        resolvedClaimData(state) == null
            ? null
            : resolvedClaimData(state).authorisedCategoryOfLawCode(),
        previousCategoryOfLaw,
        firstCategoryOfLawCode(feeSchemeDetails(state)));
  }

  private ResolvedClaimData resolvedClaimData(ClaimAmendmentState state) {
    return state == null ? null : state.getResolvedClaimDataContext();
  }

  private FeeDetailsResponseV2 feeSchemeDetails(ClaimAmendmentState state) {
    return state == null ? null : state.getFeeSchemeDetailsContext();
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

  private String firstCategoryOfLawCode(FeeDetailsResponseV2 feeDetails) {
    if (feeDetails == null) {
      return null;
    }
    List<String> categoryOfLawCodes = feeDetails.getCategoryOfLawCodes();
    if (categoryOfLawCodes == null || categoryOfLawCodes.isEmpty()) {
      return null;
    }
    String firstCode = categoryOfLawCodes.getFirst();
    return firstCode == null || firstCode.isBlank() ? null : firstCode;
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
