package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_ID;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionBadRequestException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.SubmissionMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.SubmissionsResultSetMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionBase;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionsResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationMessageLogRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {
  @Mock private SubmissionRepository submissionRepository;
  @Mock private ClaimService claimService;
  @Mock private MatterStartService matterStartService;
  @Mock private SubmissionMapper submissionMapper;
  @Mock private ValidationMessageLogRepository validationMessageLogRepository;
  @Mock private SubmissionsResultSetMapper submissionsResultSetMapper;
  @Mock private SubmissionEventPublisherService submissionEventPublisherService;

  @InjectMocks private SubmissionService submissionService;

  private static final LocalDate SUBMITTED_DATE_FROM = LocalDate.of(2025, 1, 1);
  private static final LocalDate SUBMITTED_DATE_TO = LocalDate.of(2025, 12, 31);
  private static final List<String> OFFICE_CODES = List.of("office1", "office2", "office3");

  @Test
  void shouldCreateSubmission() {
    UUID id = Uuid7.timeBasedUuid();
    SubmissionPost post = new SubmissionPost().submissionId(id);
    Submission entity = Submission.builder().id(id).build();

    when(submissionMapper.toSubmission(post)).thenReturn(entity);
    when(submissionRepository.save(entity)).thenReturn(entity);

    UUID result = submissionService.createSubmission(post);

    assertThat(result).isEqualTo(id);
    verify(submissionRepository).save(entity);
  }

  @Test
  void shouldGetSubmission() {
    UUID submissionId = Uuid7.timeBasedUuid();
    Submission entity =
        Submission.builder()
            .id(submissionId)
            .bulkSubmissionId(Uuid7.timeBasedUuid())
            .officeAccountNumber("123-ABC")
            .submissionPeriod("APR-2024")
            .areaOfLaw("CIVIL")
            .status(SubmissionStatus.CREATED)
            .crimeScheduleNumber("SCH-123")
            .civilSubmissionReference("CIVIL-SUB-123")
            .mediationSubmissionReference("MEDIATION-SUB-123")
            .previousSubmissionId(submissionId)
            .isNilSubmission(false)
            .numberOfClaims(10)
            .createdOn(Instant.now())
            .build();
    when(submissionRepository.findById(submissionId)).thenReturn(java.util.Optional.of(entity));
    when(claimService.getClaimsForSubmission(submissionId)).thenReturn(java.util.List.of());
    when(matterStartService.getMatterStartIdsForSubmission(submissionId))
        .thenReturn(java.util.List.of());

    SubmissionResponse result = submissionService.getSubmission(submissionId);

    assertThat(result.getSubmissionId()).isEqualTo(submissionId);
  }

  @Test
  void shouldThrowWhenSubmissionNotFoundOnGet() {
    UUID id = Uuid7.timeBasedUuid();
    when(submissionRepository.findById(id)).thenReturn(java.util.Optional.empty());

    assertThrows(SubmissionNotFoundException.class, () -> submissionService.getSubmission(id));
  }

  @Test
  void shouldUpdateSubmission() {
    UUID id = Uuid7.timeBasedUuid();
    Submission entity = Submission.builder().id(id).build();
    SubmissionPatch patch = new SubmissionPatch().crimeScheduleNumber("456");
    when(submissionRepository.findById(id)).thenReturn(java.util.Optional.of(entity));

    submissionService.updateSubmission(id, patch);

    verify(submissionMapper).updateSubmissionFromPatch(patch, entity);
    verify(submissionRepository).save(entity);
  }

  @Test
  void shouldUpdateSubmissionWithCivilAndMediationSubmissionReferences() {
    UUID id = Uuid7.timeBasedUuid();
    Submission entity = Submission.builder().id(id).build();
    SubmissionPatch patch =
        new SubmissionPatch()
            .civilSubmissionReference("CIVIL-123")
            .mediationSubmissionReference("MED-123");
    when(submissionRepository.findById(id)).thenReturn(java.util.Optional.of(entity));

    submissionService.updateSubmission(id, patch);

    verify(submissionMapper).updateSubmissionFromPatch(patch, entity);
    verify(submissionRepository).save(entity);
  }

  @Test
  void shouldThrowWhenSubmissionNotFoundOnUpdate() {
    UUID id = Uuid7.timeBasedUuid();
    SubmissionPatch patch = new SubmissionPatch();
    when(submissionRepository.findById(id)).thenReturn(java.util.Optional.empty());

    assertThrows(
        SubmissionNotFoundException.class, () -> submissionService.updateSubmission(id, patch));
  }

  @Test
  void shouldUpdateSubmissionAndLogValidationErrors() {
    UUID id = Uuid7.timeBasedUuid();
    Submission entity = Submission.builder().id(id).build();

    final ValidationMessagePatch messagePatch =
        new ValidationMessagePatch()
            .type(ValidationMessageType.ERROR)
            .source("SYSTEM")
            .displayMessage("A display message")
            .technicalMessage("A technical message");

    SubmissionPatch patch =
        new SubmissionPatch().validationMessages(java.util.List.of(messagePatch));
    when(submissionRepository.findById(id)).thenReturn(java.util.Optional.of(entity));
    when(submissionMapper.toValidationMessageLog(any(), eq(entity)))
        .thenReturn(new ValidationMessageLog());

    submissionService.updateSubmission(id, patch);

    verify(submissionMapper).toValidationMessageLog(any(), eq(entity));
    verify(validationMessageLogRepository).save(any(ValidationMessageLog.class));
  }

  @Test
  void getSubmissionsResultSet_whenOfficesIsMissing_shouldThrowSubmissionBadRequestException() {
    assertThrows(
        SubmissionBadRequestException.class,
        () ->
            submissionService.getSubmissionsResultSet(
                null,
                SUBMISSION_ID.toString(),
                SUBMITTED_DATE_FROM,
                SUBMITTED_DATE_TO,
                Pageable.unpaged()));
  }

  @Test
  void getSubmissionsResultSet_whenOfficesIsEmpty_shouldThrowSubmissionBadRequestException() {
    assertThrows(
        SubmissionBadRequestException.class,
        () ->
            submissionService.getSubmissionsResultSet(
                Collections.emptyList(),
                SUBMISSION_ID.toString(),
                SUBMITTED_DATE_FROM,
                SUBMITTED_DATE_TO,
                Pageable.unpaged()));
  }

  @Test
  void getSubmissionsResultSet_whenFiltersMatchData_shouldReturnNonEmptyResultSet() {
    Page<Submission> resultPage = new PageImpl<>(Collections.singletonList(new Submission()));
    when(submissionRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(resultPage);

    var expectedNonEmptyResultSet =
        new SubmissionsResultSet().content(Collections.singletonList(new SubmissionBase()));
    when(submissionsResultSetMapper.toSubmissionsResultSet(resultPage))
        .thenReturn(expectedNonEmptyResultSet);

    var actualResultSet =
        submissionService.getSubmissionsResultSet(
            OFFICE_CODES,
            SUBMISSION_ID.toString(),
            SUBMITTED_DATE_FROM,
            SUBMITTED_DATE_TO,
            Pageable.ofSize(10).withPage(0));

    assertThat(actualResultSet).isEqualTo(expectedNonEmptyResultSet);
    assertThat(actualResultSet.getContent()).hasSize(1);
  }

  @Test
  void getSubmissionsResultSet_whenFiltersDoNotMatchData_shouldReturnEmptyResultSet() {
    Page<Submission> resultPage = new PageImpl<>(Collections.emptyList());

    when(submissionRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(resultPage);

    var expectedEmptyResultSet = new SubmissionsResultSet();
    when(submissionsResultSetMapper.toSubmissionsResultSet(resultPage))
        .thenReturn(expectedEmptyResultSet);

    var actualResultSet =
        submissionService.getSubmissionsResultSet(
            OFFICE_CODES,
            SUBMISSION_ID.toString(),
            SUBMITTED_DATE_FROM,
            SUBMITTED_DATE_TO,
            Pageable.ofSize(10).withPage(0));

    assertThat(actualResultSet).isEqualTo(expectedEmptyResultSet);
    assertThat(actualResultSet.getContent()).isEmpty();
  }
}
