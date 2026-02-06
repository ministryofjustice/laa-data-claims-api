package uk.gov.justice.laa.dstew.payments.claimsdata.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.springdoc.core.annotations.ParameterObject;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;

/**
 * This class provides a basic DTO for wrapping all the different filters to be applied in a Claim
 * search.
 */
@Data
@Builder
@ParameterObject
public class ClaimSearchRequest {

  private String officeCode;
  private String submissionId;
  private List<SubmissionStatus> submissionStatuses;
  private String feeCode;
  private String uniqueFileNumber;
  private String uniqueClientNumber;
  private String uniqueCaseId;
  private List<ClaimStatus> claimStatuses;
  private String submissionPeriod;
  private String caseReferenceNumber;
}
