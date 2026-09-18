package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationIssue;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Builds {@link ValidationMessageLog} entities for the different validation-message write paths.
 */
@Component
public class ValidationMessageLogFactory {

  /**
   * Builds a claim-scoped validation message row from an API patch payload.
   *
   * @param message incoming validation message payload
   * @param claim claim the message belongs to
   * @return populated validation message log entity
   */
  public ValidationMessageLog createForClaim(ValidationMessagePatch message, Claim claim) {
    return create(
        claim.getSubmission().getId(),
        claim.getId(),
        message.getType(),
        message.getSource(),
        message.getDisplayMessage(),
        message.getTechnicalMessage(),
        message.getMessageCode(),
        null,
        null,
        null);
  }

  /**
   * Builds a submission-scoped validation message row from an API patch payload.
   *
   * @param message incoming validation message payload
   * @param submission submission the message belongs to
   * @return populated validation message log entity
   */
  public ValidationMessageLog createForSubmission(
      ValidationMessagePatch message, Submission submission) {
    return create(
        submission.getId(),
        null,
        message.getType(),
        message.getSource(),
        message.getDisplayMessage(),
        message.getTechnicalMessage(),
        message.getMessageCode(),
        null,
        null,
        null);
  }

  /**
   * Builds a claim-scoped validation message row from a validation-core issue.
   *
   * @param issue validation-core issue to persist
   * @param claim claim the issue belongs to
   * @param type persisted message type
   * @param source persisted source label
   * @param version claim version the issue belongs to
   * @param supersededByVersion supersession marker/version for current-vs-historical semantics
   * @return populated validation message log entity
   */
  public ValidationMessageLog createForClaimIssue(
      ValidationIssue issue,
      Claim claim,
      ValidationMessageType type,
      String source,
      Long version,
      Long supersededByVersion) {
    return create(
        claim.getSubmission().getId(),
        claim.getId(),
        type,
        source,
        issue.getMessage(),
        issue.getMessage(),
        issue.getCode(),
        Instant.now(),
        version,
        supersededByVersion);
  }

  private ValidationMessageLog create(
      UUID submissionId,
      UUID claimId,
      ValidationMessageType type,
      String source,
      String displayMessage,
      String technicalMessage,
      String messageCode,
      Instant createdOn,
      Long version,
      Long supersededByVersion) {
    ValidationMessageLog log = new ValidationMessageLog();
    log.setId(Uuid7.timeBasedUuid());
    log.setSubmissionId(submissionId);
    log.setClaimId(claimId);
    log.setType(type);
    log.setSource(source);
    log.setDisplayMessage(displayMessage);
    log.setTechnicalMessage(technicalMessage);
    log.setMessageCode(messageCode);
    if (createdOn != null) {
      log.setCreatedOn(createdOn);
    }
    if (version != null) {
      log.setVersion(version);
    }
    if (supersededByVersion != null) {
      log.setSupersededByVersion(supersededByVersion);
    }
    return log;
  }
}
