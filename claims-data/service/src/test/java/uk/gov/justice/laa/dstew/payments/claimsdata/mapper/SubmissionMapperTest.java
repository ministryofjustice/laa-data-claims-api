package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMITTED_DATE;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionBase;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;

@ExtendWith(MockitoExtension.class)
class SubmissionMapperTest {

  @InjectMocks private SubmissionMapper submissionMapper = new SubmissionMapperImpl();

  @Spy private GlobalStringMapper globalStringMapper = new GlobalStringMapperImpl();

  @Spy private GlobalDateTimeMapper globalDateTimeMapper = new GlobalDateTimeMapperImpl();

  @Test
  void shouldMapToSubmissionEntity() {
    UUID id = UUID.randomUUID();
    UUID bulkId = UUID.randomUUID();
    SubmissionPost post =
        new SubmissionPost()
            .submissionId(id)
            .bulkSubmissionId(bulkId)
            .officeAccountNumber("12345")
            .submissionPeriod("2025-07")
            .areaOfLaw("crime")
            .isNilSubmission(false)
            .numberOfClaims(1);

    Submission result = submissionMapper.toSubmission(post);

    assertThat(result.getId()).isEqualTo(id);
    assertThat(result.getBulkSubmissionId()).isEqualTo(bulkId);
    assertThat(result.getOfficeAccountNumber()).isEqualTo("12345");
    assertThat(result.getSubmissionPeriod()).isEqualTo("2025-07");
    assertThat(result.getAreaOfLaw()).isEqualTo("crime");
    assertThat(result.getIsNilSubmission()).isFalse();
    assertThat(result.getNumberOfClaims()).isEqualTo(1);
  }

  @Test
  void shouldMapToSubmissionBase() {
    UUID id = UUID.randomUUID();
    Submission submission =
        Submission.builder()
            .id(id)
            .bulkSubmissionId(UUID.randomUUID())
            .officeAccountNumber("12345")
            .submissionPeriod("2025-07")
            .areaOfLaw("crime")
            .isNilSubmission(false)
            .numberOfClaims(2)
            .createdOn(LocalDate.of(2025, 5, 20).atStartOfDay(ZoneOffset.UTC).toInstant())
            .build();

    SubmissionBase result = submissionMapper.toSubmissionBase(submission);

    assertThat(result.getSubmissionId()).isEqualTo(id);
    assertThat(result.getOfficeAccountNumber()).isEqualTo("12345");
    assertThat(result.getSubmissionPeriod()).isEqualTo("2025-07");
    assertThat(result.getSubmitted()).isEqualTo(SUBMITTED_DATE);
  }

  @Test
  void shouldUpdateSubmissionFromPatch() {
    Submission submission =
        Submission.builder().scheduleNumber("123").isNilSubmission(false).build();
    SubmissionPatch patch = new SubmissionPatch().scheduleNumber("456").isNilSubmission(true);

    submissionMapper.updateSubmissionFromPatch(patch, submission);

    assertThat(submission.getScheduleNumber()).isEqualTo("456");
    assertThat(submission.getIsNilSubmission()).isTrue();
  }

  @Test
  void toValidationErrorLog_mapsFields() {
    Submission submission = Submission.builder().id(UUID.randomUUID()).build();

    final ValidationMessagePatch patch =
        new ValidationMessagePatch()
            .type(ValidationMessageType.ERROR)
            .source("SYSTEM")
            .displayMessage("A display message")
            .technicalMessage("A technical message");

    ValidationMessageLog log = submissionMapper.toValidationMessageLog(patch, submission);

    assertThat(log.getId()).isNotNull();
    assertEquals(submission.getId(), log.getSubmissionId());
    assertThat(log.getClaimId()).isNull();
    Assertions.assertEquals("ERROR", log.getType());
    Assertions.assertEquals("SYSTEM", log.getSource());
    Assertions.assertEquals("A display message", log.getDisplayMessage());
    Assertions.assertEquals("A technical message", log.getTechnicalMessage());
  }
}
