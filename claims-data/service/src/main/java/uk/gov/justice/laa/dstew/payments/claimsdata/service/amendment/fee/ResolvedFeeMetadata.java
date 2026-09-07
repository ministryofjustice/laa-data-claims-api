package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee;

import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationType;

/**
 * Immutable carrier for fee metadata that is not present on the FSP {@code FeeCalculationResponse}
 * and must be resolved from cached validation/enrichment context. Separates concerns and intent:
 * metadata resolution happens in FeeCalculationMetadataResolver, while ClaimMapper only assembles
 * the target entity.
 *
 * <p>Produced by {@link FeeCalculationMetadataResolver#resolve(
 * uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState, String)} and
 * consumed by {@code ClaimMapper.toCalculatedFeeDetail(FeeCalculationResponse,
 * ResolvedFeeMetadata)} so the mapper can populate the target entity in a single pass.
 *
 * @param feeType resolved fee calculation type (nullable when no source supplies it)
 * @param feeCodeDescription resolved fee code description (nullable)
 * @param categoryOfLaw resolved category-of-law code (nullable)
 */
public record ResolvedFeeMetadata(
    FeeCalculationType feeType, String feeCodeDescription, String categoryOfLaw) {}
