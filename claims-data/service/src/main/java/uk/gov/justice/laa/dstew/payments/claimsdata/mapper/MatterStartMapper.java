package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.MatterStart;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MatterStartGet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MatterStartPost;

/** MapStruct mapper for converting between matter start models and entities. */
@Mapper(
    componentModel = "spring",
    uses = GlobalStringMapper.class,
    config = AuditFieldsMapper.class)
public interface MatterStartMapper {

  /** Map a {@link MatterStartPost} to a {@link MatterStart} entity. */
  @InheritConfiguration(name = "ignoreAuditFieldsAndId")
  @Mapping(target = "submission", ignore = true)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdOn", ignore = true)
  @Mapping(target = "updatedByUserId", ignore = true)
  @Mapping(target = "updatedOn", ignore = true)
  MatterStart toMatterStart(MatterStartPost request);

  MatterStartGet toMatterStartGet(MatterStart entity);
}
