package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;

/**
 * MapStruct mapper for converting between client models and entities.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
    uses = GlobalStringMapper.class)
public interface ClientMapper {

  /**
   * Map the client details from a {@link ClaimPost} to a {@link Client} entity.
   *
   * @param claimPost request object containing client information
   * @return mapped client entity
   */
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "claim", ignore = true)
  @Mapping(target = "createdByUserId", ignore = true)
  @Mapping(target = "createdOn", ignore = true)
  @Mapping(target = "updatedByUserId", ignore = true)
  @Mapping(target = "updatedOn", ignore = true)
  Client toClient(ClaimPost claimPost);

  /**
   * Map client entity values onto claim fields.
   *
   * @param entity client entity
   * @param fields claim fields to populate
   */
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  void updateClaimFieldsFromClient(Client entity, @MappingTarget ClaimFields fields);
}
