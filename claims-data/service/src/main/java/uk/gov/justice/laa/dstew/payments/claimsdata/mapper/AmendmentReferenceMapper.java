package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AmendmentReasonReference;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.RequestedByReference;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AmendmentRequestedByReference;

/**
 * MapStruct mapper for amendment reference data entities to API models.
 *
 * <p>The reason model shares a simple name with its entity counterpart, so it is referenced by its
 * fully-qualified name to avoid an import clash.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AmendmentReferenceMapper {

  /** Map a {@link RequestedByReference} entity to its API model (reasons are set separately). */
  AmendmentRequestedByReference toRequestedByModel(RequestedByReference entity);

  /** Map an {@link AmendmentReasonReference} entity to its API model. */
  uk.gov.justice.laa.dstew.payments.claimsdata.model.AmendmentReasonReference toReasonModel(
      AmendmentReasonReference entity);
}
