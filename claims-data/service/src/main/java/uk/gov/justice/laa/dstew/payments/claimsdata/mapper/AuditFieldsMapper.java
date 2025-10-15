package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import org.mapstruct.MapperConfig;
import org.mapstruct.Mapping;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.*;

@MapperConfig
public interface AuditFieldsMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdByUserId", ignore = true)
  @Mapping(target = "createdOn", ignore = true)
  @Mapping(target = "updatedByUserId", ignore = true)
  @Mapping(target = "updatedOn", ignore = true)
  Submission ignoreAuditFieldsAndIdSubmission(SubmissionPatch source);

  @Mapping(target = "createdByUserId", ignore = true)
  @Mapping(target = "createdOn", ignore = true)
  @Mapping(target = "updatedByUserId", ignore = true)
  @Mapping(target = "updatedOn", ignore = true)
  Submission ignoreAuditFieldsSubmission(SubmissionPost source);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdByUserId", ignore = true)
  @Mapping(target = "createdOn", ignore = true)
  @Mapping(target = "updatedByUserId", ignore = true)
  @Mapping(target = "updatedOn", ignore = true)
  MatterStart ignoreAuditFieldsAndIdMatterStart(MatterStartPost source);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdByUserId", ignore = true)
  @Mapping(target = "createdOn", ignore = true)
  @Mapping(target = "updatedByUserId", ignore = true)
  @Mapping(target = "updatedOn", ignore = true)
  Claim ignoreAuditFieldsAndIdClaim(ClaimPost source);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdByUserId", ignore = true)
  @Mapping(target = "createdOn", ignore = true)
  @Mapping(target = "updatedByUserId", ignore = true)
  @Mapping(target = "updatedOn", ignore = true)
  Claim ignoreAuditFieldsClaimPatch(ClaimPatch source);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdByUserId", ignore = true)
  @Mapping(target = "createdOn", ignore = true)
  @Mapping(target = "updatedByUserId", ignore = true)
  @Mapping(target = "updatedOn", ignore = true)
  ClaimSummaryFee ignoreAuditFieldsClaimSummaryFee(ClaimPatch source);

  @Mapping(target = "createdByUserId", ignore = true)
  @Mapping(target = "createdOn", ignore = true)
  @Mapping(target = "updatedByUserId", ignore = true)
  @Mapping(target = "updatedOn", ignore = true)
  CalculatedFeeDetail ignoreAuditFieldsCalculatedFeeDetail(FeeCalculationPatch source);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdByUserId", ignore = true)
  @Mapping(target = "createdOn", ignore = true)
  @Mapping(target = "updatedByUserId", ignore = true)
  @Mapping(target = "updatedOn", ignore = true)
  ClaimCase ignoreAuditFieldsClaimCase(ClaimPost source);
}
