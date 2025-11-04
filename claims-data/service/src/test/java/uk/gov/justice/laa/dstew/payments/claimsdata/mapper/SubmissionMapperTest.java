package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AREA_OF_LAW;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMITTED_DATE;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionBase;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@ExtendWith(MockitoExtension.class)
class SubmissionMapperTest {

  @InjectMocks private SubmissionMapper submissionMapper = new SubmissionMapperImpl();

  @Spy private GlobalStringMapper globalStringMapper = new GlobalStringMapperImpl();

  @Spy private GlobalDateTimeMapper globalDateTimeMapper = new GlobalDateTimeMapperImpl();

  @ParameterizedTest
  @EnumSource(AreaOfLaw.class)
  void shouldMapToSubmissionEntity(AreaOfLaw areaOfLaw) {
    UUID id = Uuid7.timeBasedUuid();
    UUID bulkId = Uuid7.timeBasedUuid();
    SubmissionPost post =
        new SubmissionPost()
            .submissionId(id)
            .bulkSubmissionId(bulkId)
            .officeAccountNumber("12345")
            .submissionPeriod("2025-07")
            .areaOfLaw(areaOfLaw)
            .isNilSubmission(false)
            .numberOfClaims(1);

    Submission result = submissionMapper.toSubmission(post);

    assertThat(result.getId()).isEqualTo(id);
    assertThat(result.getBulkSubmissionId()).isEqualTo(bulkId);
    assertThat(result.getOfficeAccountNumber()).isEqualTo("12345");
    assertThat(result.getSubmissionPeriod()).isEqualTo("2025-07");
    assertThat(result.getAreaOfLaw()).isEqualTo(areaOfLaw);
    assertThat(result.getIsNilSubmission()).isFalse();
    assertThat(result.getNumberOfClaims()).isEqualTo(1);
  }

  @Test
  void shouldMapToSubmissionBase() {
    UUID id = Uuid7.timeBasedUuid();
    Submission submission =
        Submission.builder()
            .id(id)
            .bulkSubmissionId(Uuid7.timeBasedUuid())
            .officeAccountNumber("12345")
            .submissionPeriod("2025-07")
            .areaOfLaw(AREA_OF_LAW)
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
        Submission.builder().crimeLowerScheduleNumber("123").isNilSubmission(false).build();
    SubmissionPatch patch =
        new SubmissionPatch().crimeLowerScheduleNumber("456").isNilSubmission(true);

    submissionMapper.updateSubmissionFromPatch(patch, submission);

    assertThat(submission.getCrimeLowerScheduleNumber()).isEqualTo("456");
    assertThat(submission.getIsNilSubmission()).isTrue();
  }

  @Test
  void toValidationErrorLog_mapsFields() {
    Submission submission = Submission.builder().id(Uuid7.timeBasedUuid()).build();

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
    Assertions.assertEquals(ValidationMessageType.ERROR, log.getType());
    Assertions.assertEquals("SYSTEM", log.getSource());
    Assertions.assertEquals("A display message", log.getDisplayMessage());
    Assertions.assertEquals("A technical message", log.getTechnicalMessage());
  }
}
