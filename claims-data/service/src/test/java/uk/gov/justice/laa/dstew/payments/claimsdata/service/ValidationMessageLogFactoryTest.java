package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationIssue;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationSeverity;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

class ValidationMessageLogFactoryTest {

  private final ValidationMessageLogFactory factory = new ValidationMessageLogFactory();

  @Test
  void createForClaim_mapsPatchFields() {
    Submission submission = Submission.builder().id(Uuid7.timeBasedUuid()).build();
    Claim claim = Claim.builder().id(Uuid7.timeBasedUuid()).submission(submission).build();
    ValidationMessagePatch patch =
        new ValidationMessagePatch()
            .type(ValidationMessageType.ERROR)
            .source("SYSTEM")
            .displayMessage("Display")
            .technicalMessage("Technical")
            .messageCode("ERR001");

    ValidationMessageLog log = factory.createForClaim(patch, claim);

    assertThat(log.getId()).isNotNull();
    assertThat(log.getSubmissionId()).isEqualTo(submission.getId());
    assertThat(log.getClaimId()).isEqualTo(claim.getId());
    assertThat(log.getType()).isEqualTo(ValidationMessageType.ERROR);
    assertThat(log.getSource()).isEqualTo("SYSTEM");
    assertThat(log.getDisplayMessage()).isEqualTo("Display");
    assertThat(log.getTechnicalMessage()).isEqualTo("Technical");
    assertThat(log.getMessageCode()).isEqualTo("ERR001");
    assertThat(log.getVersion()).isZero();
    assertThat(log.getSupersededByVersion()).isZero();
    assertThat(log.getCreatedOn()).isNull();
  }

  @Test
  void createForSubmission_mapsPatchFields() {
    Submission submission = Submission.builder().id(Uuid7.timeBasedUuid()).build();
    ValidationMessagePatch patch =
        new ValidationMessagePatch()
            .type(ValidationMessageType.WARNING)
            .source("FSP")
            .displayMessage("Display")
            .technicalMessage("Technical")
            .messageCode("WARN01");

    ValidationMessageLog log = factory.createForSubmission(patch, submission);

    assertThat(log.getId()).isNotNull();
    assertThat(log.getSubmissionId()).isEqualTo(submission.getId());
    assertThat(log.getClaimId()).isNull();
    assertThat(log.getType()).isEqualTo(ValidationMessageType.WARNING);
    assertThat(log.getSource()).isEqualTo("FSP");
    assertThat(log.getDisplayMessage()).isEqualTo("Display");
    assertThat(log.getTechnicalMessage()).isEqualTo("Technical");
    assertThat(log.getMessageCode()).isEqualTo("WARN01");
    assertThat(log.getVersion()).isZero();
    assertThat(log.getSupersededByVersion()).isZero();
    assertThat(log.getCreatedOn()).isNull();
  }

  @Test
  void createForClaimIssue_mapsValidationIssueIntoVersionedWarningRow() {
    Submission submission = Submission.builder().id(Uuid7.timeBasedUuid()).build();
    Claim claim = Claim.builder().id(Uuid7.timeBasedUuid()).submission(submission).build();
    ValidationIssue issue =
        ValidationIssue.builder()
            .code("WARFAM1")
            .message("FSP warning")
            .severity(ValidationSeverity.WARNING)
            .build();

    ValidationMessageLog log =
        factory.createForClaimIssue(issue, claim, ValidationMessageType.WARNING, "FSP", 7L, 0L);

    assertThat(log.getId()).isNotNull();
    assertThat(log.getSubmissionId()).isEqualTo(submission.getId());
    assertThat(log.getClaimId()).isEqualTo(claim.getId());
    assertThat(log.getType()).isEqualTo(ValidationMessageType.WARNING);
    assertThat(log.getSource()).isEqualTo("FSP");
    assertThat(log.getDisplayMessage()).isEqualTo("FSP warning");
    assertThat(log.getTechnicalMessage()).isEqualTo("FSP warning");
    assertThat(log.getMessageCode()).isEqualTo("WARFAM1");
    assertThat(log.getVersion()).isEqualTo(7L);
    assertThat(log.getSupersededByVersion()).isZero();
    assertThat(log.getCreatedOn()).isNotNull();
  }
}
