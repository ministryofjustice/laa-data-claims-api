package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentOutcome;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentType;

/**
 * Immutable read-only snapshot of the latest assessment for a claim. Captures only the state needed
 * to enforce assessed-claim amendment rules. Not provider-amendable.
 */
@Value
@Builder
public class AssessmentSnapshot {

  AssessmentOutcome assessmentOutcome;
  AssessmentType assessmentType;
  String assessmentReason;
  BigDecimal assessedTotalVat;
  BigDecimal assessedTotalInclVat;
  BigDecimal allowedTotalVat;
  BigDecimal allowedTotalInclVat;
}
