package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.MatterStart;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MatterStartsGet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MatterStartsPost;

/**
 * MapStruct mapper for converting between matter start models and entities.
 */
@Mapper(componentModel = "spring", uses = GlobalStringMapper.class)
public interface MatterStartMapper {

  /** Map a {@link MatterStartsPost} to a {@link MatterStart} entity. */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "submission", ignore = true)
  @Mapping(target = "createdByUserId", ignore = true)
  @Mapping(target = "createdOn", ignore = true)
  @Mapping(target = "updatedByUserId", ignore = true)
  @Mapping(target = "updatedOn", ignore = true)
  MatterStart toMatterStart(MatterStartsPost request);

  MatterStartsGet toMatterStartsGet(MatterStart entity);
}

