package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidatedClaimResponse;

/** MapStruct mapper for converting between client models and entities. */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    uses = GlobalStringMapper.class)
public interface ClientMapper {

  /**
   * Map the client details from a {@link ClaimPost} to a {@link Client} entity.
   *
   * @param claimPost request object containing client information
   * @return mapped client entity
   */
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @InheritConfiguration(name = "ignoreAuditFieldsAndId")
  @Mapping(target = "claim", ignore = true)
  Client toClient(ClaimPost claimPost);

  /**
   * Map client entity values onto claim response.
   *
   * @param entity client entity
   * @param claim claim response to populate
   */
  @Mapping(target = "id", ignore = true)
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  void updateClaimResponseFromClient(Client entity, @MappingTarget ClaimResponse claim);

  /**
   * Map client entity values onto claim response.
   *
   * @param entity client entity
   * @param claim claim response to populate
   */
  @Mapping(target = "id", ignore = true)
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  void updateValidatedClaimResponseFromClient(
      Client entity, @MappingTarget ValidatedClaimResponse claim);
}
