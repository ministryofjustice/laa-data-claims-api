package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPost;

@ExtendWith(MockitoExtension.class)
class SubmissionMapperTest {
  @InjectMocks private SubmissionMapper submissionMapper = new SubmissionMapperImpl();

  @Test
  void shouldMapToSubmissionEntity() {
    UUID id = UUID.randomUUID();
    UUID bulkId = UUID.randomUUID();
    SubmissionPost post = new SubmissionPost()
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
  void shouldMapToSubmissionFields() {
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
            .build();

    SubmissionFields result = submissionMapper.toSubmissionFields(submission);

    assertThat(result.getSubmissionId()).isEqualTo(id);
    assertThat(result.getOfficeAccountNumber()).isEqualTo("12345");
  }

  @Test
  void shouldUpdateSubmissionFromPatch() {
    Submission submission = Submission.builder().scheduleNumber("123").isNilSubmission(false).build();
    SubmissionPatch patch = new SubmissionPatch().scheduleNumber("456").isNilSubmission(true);

    submissionMapper.updateSubmissionFromPatch(patch, submission);

    assertThat(submission.getScheduleNumber()).isEqualTo("456");
    assertThat(submission.getIsNilSubmission()).isTrue();
  }
}
