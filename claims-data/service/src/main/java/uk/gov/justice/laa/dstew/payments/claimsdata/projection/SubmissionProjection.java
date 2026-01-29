package uk.gov.justice.laa.dstew.payments.claimsdata.projection;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionClaim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;

/** Submission Projection. */
public interface SubmissionProjection {

  UUID getSubmissionId();

  UUID getBulkSubmissionId();

  UUID getPreviousSubmissionId();

  String getOfficeAccountNumber();

  String getSubmissionPeriod();

  AreaOfLaw getAreaOfLaw();

  String getProviderUserId();

  SubmissionStatus getStatus();

  String getCrimeLowerScheduleNumber();

  String getLegalHelpSubmissionReference();

  String getMediationSubmissionReference();

  Boolean getIsNilSubmission();

  Integer getNumberOfClaims();

  BigDecimal getCalculatedTotalAmount();

  OffsetDateTime getSubmitted();

  String getCreatedByUserId();

  List<SubmissionClaim> getClaims();

  List<UUID> getMatterStarts();
}
