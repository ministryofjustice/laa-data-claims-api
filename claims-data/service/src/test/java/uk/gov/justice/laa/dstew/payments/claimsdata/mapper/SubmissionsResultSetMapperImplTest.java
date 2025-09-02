package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionsResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;

import java.time.ZoneOffset;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionsResultSetMapperImplTest {

  @Spy private SubmissionMapper submissionMapper;

  @InjectMocks private SubmissionsResultSetMapperImpl mapper = new SubmissionsResultSetMapperImpl();

  @Test
  void toSubmissionsResultSet_shouldMapPageAttributes() {
    Page<Submission> input = new PageImpl<>(Collections.emptyList(), PageRequest.of(1, 20), 30);

    SubmissionsResultSet resultSet = mapper.toSubmissionsResultSet(input);

    assertEquals(1, resultSet.getNumber());
    assertEquals(20, resultSet.getSize());
    assertEquals(2, resultSet.getTotalPages());
    assertEquals(30, resultSet.getTotalElements());
  }

  @Test
  void submissionListToSubmissionFieldsList_shouldReturnSubmissionData() {
    var expectedSubmission = ClaimsDataTestUtil.getSubmission();
    Page<Submission> page = new PageImpl<>(Collections.singletonList(expectedSubmission));

    var submissionFields = ClaimsDataTestUtil.getSubmissionFields();
    when(submissionMapper.toSubmissionFields(expectedSubmission)).thenReturn(submissionFields);

    var submissionsResultSet = mapper.toSubmissionsResultSet(page);
    var actualSubmission = submissionsResultSet.getContent().getFirst();

    assertThat(actualSubmission.getSubmissionId()).isEqualTo(expectedSubmission.getId());
    assertThat(actualSubmission.getBulkSubmissionId())
        .isEqualTo(expectedSubmission.getBulkSubmissionId());
    assertThat(actualSubmission.getOfficeAccountNumber())
        .isEqualTo(expectedSubmission.getOfficeAccountNumber());
    assertThat(actualSubmission.getSubmissionPeriod())
        .isEqualTo(expectedSubmission.getSubmissionPeriod());
    assertThat(actualSubmission.getAreaOfLaw()).isEqualTo(expectedSubmission.getAreaOfLaw());
    assertThat(actualSubmission.getStatus()).isEqualTo(expectedSubmission.getStatus());
    assertThat(actualSubmission.getScheduleNumber())
        .isEqualTo(expectedSubmission.getScheduleNumber());
    assertThat(actualSubmission.getPreviousSubmissionId())
        .isEqualTo(expectedSubmission.getPreviousSubmissionId());
    assertThat(actualSubmission.getIsNilSubmission())
        .isEqualTo(expectedSubmission.getIsNilSubmission());
    assertThat(actualSubmission.getNumberOfClaims())
        .isEqualTo(expectedSubmission.getNumberOfClaims());
    assertThat(actualSubmission.getSubmitted().atStartOfDay().toInstant(ZoneOffset.UTC))
        .isEqualTo(expectedSubmission.getCreatedOn());
  }
}
