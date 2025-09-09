package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationErrorLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionClaim;

/** MapStruct mapper for converting between claim models and entities. */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    uses = GlobalStringMapper.class,
    imports = {java.util.UUID.class})
public interface ClaimMapper {

  // TODO: DSTEW-323 isolate common @Mapping annotations in one place (6 methods are currently using
  // these)
  /** Map a {@link ClaimPost} to a {@link Claim} entity. */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "submission", ignore = true)
  @Mapping(target = "createdByUserId", ignore = true)
  @Mapping(target = "createdOn", ignore = true)
  @Mapping(target = "updatedByUserId", ignore = true)
  @Mapping(target = "updatedOn", ignore = true)
  @Mapping(target = "dutySolicitor", source = "isDutySolicitor")
  @Mapping(target = "youthCourt", source = "isYouthCourt")
  Claim toClaim(ClaimPost claimPost);

  /** Map a {@link Claim} entity to {@link ClaimResponse}. */
  @Mapping(target = "isDutySolicitor", source = "dutySolicitor")
  @Mapping(target = "isYouthCourt", source = "youthCourt")
  ClaimResponse toClaimResponse(Claim entity);

  /** Map a {@link SubmissionClaim} to summary response model. */
  @Mapping(target = "claimId", source = "id")
  SubmissionClaim toSubmissionClaim(Claim entity);

  /** Update an existing {@link Claim} from a {@link ClaimPatch}. */
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "submission", ignore = true)
  @Mapping(target = "createdByUserId", ignore = true)
  @Mapping(target = "createdOn", ignore = true)
  @Mapping(target = "updatedByUserId", ignore = true)
  @Mapping(target = "updatedOn", ignore = true)
  @Mapping(target = "dutySolicitor", source = "isDutySolicitor")
  @Mapping(target = "youthCourt", source = "isYouthCourt")
  void updateSubmissionClaimFromPatch(ClaimPatch patch, @MappingTarget Claim entity);

  /** Map a validation error string to a ValidationErrorLog. */
  @Mapping(target = "id", expression = "java(UUID.randomUUID())")
  @Mapping(target = "submission", source = "claim.submission")
  @Mapping(target = "claim", source = "claim")
  @Mapping(target = "errorCode", source = "error")
  @Mapping(target = "errorDescription", source = "error")
  @Mapping(target = "createdByUserId", constant = "todo")
  ValidationErrorLog toValidationErrorLog(String error, Claim claim);
}
