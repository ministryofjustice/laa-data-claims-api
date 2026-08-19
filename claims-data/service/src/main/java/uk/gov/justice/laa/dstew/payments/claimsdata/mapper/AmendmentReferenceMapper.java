package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AmendmentReasonReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.RequestedByReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AmendmentReasonReference;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AmendmentRequestedByReference;

/** MapStruct mapper for amendment reference data entities to API models. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AmendmentReferenceMapper {

  /**
   * Map a {@link RequestedByReferenceEntity} entity to its API model (reasons are set separately).
   */
  AmendmentRequestedByReference toRequestedByModel(RequestedByReferenceEntity entity);

  /** Map an {@link AmendmentReasonReferenceEntity} entity to its API model. */
  AmendmentReasonReference toReasonModel(AmendmentReasonReferenceEntity entity);
}
