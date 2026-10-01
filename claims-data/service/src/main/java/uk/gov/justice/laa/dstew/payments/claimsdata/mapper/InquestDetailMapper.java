package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import org.mapstruct.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.InquestDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimInquestDetail;

/** MapStruct mapper for converting between Inquest detail models and entities. */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    uses = GlobalStringMapper.class)
public interface InquestDetailMapper {

  /**
   * Map the {@code inquest_detail} object from a claim request to an {@link InquestDetail} entity.
   *
   * @param claimInquestDetail request object containing Inquest information
   * @return mapped Inquest detail entity
   */
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @InheritConfiguration(name = "ignoreAuditFieldsAndId")
  @Mapping(target = "claim", ignore = true)
  InquestDetail toInquestDetail(ClaimInquestDetail claimInquestDetail);

  @Mapping(
      target = "deceasedDateOfDeath",
      source = "deceasedDateOfDeath",
      qualifiedByName = "formatDate")
  ClaimInquestDetail toClaimInquestDetail(InquestDetail entity);
}
