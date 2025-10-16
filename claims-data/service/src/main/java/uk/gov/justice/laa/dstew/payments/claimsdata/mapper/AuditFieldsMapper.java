package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import org.mapstruct.InheritConfiguration;
import org.mapstruct.MapperConfig;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/** MapStruct config for common mappings of audit fields. * */
@MapperConfig
public interface AuditFieldsMapper {

  // Base configuration for ignoring audit fields
  @Mapping(target = "createdByUserId", ignore = true)
  @Mapping(target = "createdOn", ignore = true)
  @Mapping(target = "updatedByUserId", ignore = true)
  @Mapping(target = "updatedOn", ignore = true)
  default void ignoreAuditFields(@MappingTarget Object target) {
    // No implementation needed — it's only used for config inheritance
  }

  // Base configuration for ignoring audit fields
  @Mapping(target = "id", ignore = true)
  @InheritConfiguration(name = "ignoreAuditFields")
  default void ignoreAuditFieldsAndId(@MappingTarget Object target) {
    // No implementation needed — it's only used for config inheritance
  }
}
