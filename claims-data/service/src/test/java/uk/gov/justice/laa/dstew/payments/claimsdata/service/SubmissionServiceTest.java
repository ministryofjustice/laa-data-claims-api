package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AREA_OF_LAW;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_STATUSES;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationIssue;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationResult;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationSeverity;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.service.ValidationService;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionBadRequestException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionValidationException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.SubmissionMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.SubmissionsResultSetMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
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
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubmissionService Unit Tests")
class SubmissionServiceTest {
  @Mock private SubmissionRepository submissionRepository;
  @Mock private ValidationService validationService;
  @Mock private ClaimService claimService;
  @Mock private MatterStartService matterStartService;
  @Mock private SubmissionMapper submissionMapper;
  @Mock private ValidationMessageLogRepository validationMessageLogRepository;
  @Mock private SubmissionsResultSetMapper submissionsResultSetMapper;
  @Mock private SubmissionEventPublisherService submissionEventPublisherService;
  @Mock private AssessmentService assessmentService;

  @InjectMocks private SubmissionService submissionService;

  @Captor private ArgumentCaptor<Specification> submissionSpecificationArgumentCaptor;
  private static final LocalDate SUBMITTED_DATE_FROM = LocalDate.of(2025, 1, 1);
  private static final LocalDate SUBMITTED_DATE_TO = LocalDate.of(2025, 12, 31);
  private static final List<String> OFFICE_CODES = List.of("office1", "office2", "office3");
  private static final String SUBMISSION_PERIOD = "2025-07";

  @Test
  @DisplayName("Should successfully map and persist a new submission")
  void shouldCreateSubmission() {
    UUID id = Uuid7.timeBasedUuid();
    SubmissionPost post = new SubmissionPost().submissionId(id);
    Submission entity = Submission.builder().id(id).build();
    SubmissionResponse submissionResponse = new SubmissionResponse();

    when(submissionMapper.toSubmission(post)).thenReturn(entity);
    when(submissionMapper.toSubmissionResponse(entity)).thenReturn(submissionResponse);
    when(validationService.validateSubmission(submissionResponse))
        .thenReturn(ValidationResult.builder().isValid(true).build());
    when(submissionRepository.save(entity)).thenReturn(entity);

    UUID result = submissionService.createSubmission(post);
    assertThat(entity.getStatus()).isEqualTo(SubmissionStatus.VALIDATION_SUCCEEDED);
    assertThat(result).isEqualTo(id);
    verify(submissionRepository).save(entity);
  }

//    @Test
//    @DisplayName("Should successfully map and persist a nil new submission")
//    void shouldCreateNilSubmissionWithCorrectStatus() {
//        UUID id = Uuid7.timeBasedUuid();
//        Submission entity = ClaimsDataTestUtil.getNilSubmission();
//        entity.setId(id);
//        SubmissionPost post = new SubmissionPost().submissionId(id);
//        SubmissionResponse submissionResponse = new SubmissionResponse();
//
//        when(submissionMapper.toSubmission(post)).thenReturn(entity);
//        when(submissionMapper.toSubmissionResponse(entity)).thenReturn(submissionResponse);
//        when(validationService.validateSubmission(submissionResponse))
//                .thenReturn(ValidationResult.builder().isValid(true).build());
//        when(submissionRepository.save(entity)).thenReturn(entity);
//
//        UUID result = submissionService.createSubmission(post);
//        assertThat(entity.getStatus()).isEqualTo(SubmissionStatus.VALIDATION_SUCCEEDED);
//        assertThat(result).isEqualTo(id);
//        verify(submissionRepository).save(entity);
//    }

  @Test
  @DisplayName(
      "createSubmission: invalid submission throws SubmissionValidationException with issues and 400 status")
  void shouldThrowSubmissionValidationExceptionWhenValidationFails() {
    UUID id = Uuid7.timeBasedUuid();
    SubmissionPost post = new SubmissionPost().submissionId(id);
    Submission entity = Submission.builder().id(id).build();
    SubmissionResponse submissionResponse = new SubmissionResponse();

    ValidationIssue issue =
        ValidationIssue.builder()
            .code("SUB-001")
            .message("Office code is required")
            .severity(ValidationSeverity.ERROR)
            .build();
    ValidationResult invalidResult =
        ValidationResult.builder().isValid(false).issues(List.of(issue)).build();

    when(submissionMapper.toSubmission(post)).thenReturn(entity);
    when(submissionMapper.toSubmissionResponse(entity)).thenReturn(submissionResponse);
    when(validationService.validateSubmission(submissionResponse)).thenReturn(invalidResult);

    SubmissionValidationException ex =
        assertThrows(
            SubmissionValidationException.class, () -> submissionService.createSubmission(post));

    assertThat(ex.getIssues()).hasSize(1);
    assertThat(ex.getIssues().getFirst().getCode()).isEqualTo("SUB-001");
    assertThat(ex.getHttpStatus().value()).isEqualTo(400);
    verify(submissionRepository, never()).save(any());
  }

  @Test
  @DisplayName("createSubmission: invalid submission is never persisted to the repository")
  void shouldNotPersistSubmissionWhenValidationFails() {
    UUID id = Uuid7.timeBasedUuid();
    SubmissionPost post = new SubmissionPost().submissionId(id);
    Submission entity = Submission.builder().id(id).build();
    SubmissionResponse submissionResponse = new SubmissionResponse();

    when(submissionMapper.toSubmission(post)).thenReturn(entity);
    when(submissionMapper.toSubmissionResponse(entity)).thenReturn(submissionResponse);
    when(validationService.validateSubmission(submissionResponse))
        .thenReturn(ValidationResult.builder().isValid(false).build());

    assertThrows(
        SubmissionValidationException.class, () -> submissionService.createSubmission(post));

    verify(submissionRepository, never()).save(any());
  }

    @Test
    @DisplayName(
            "Should retrieve a direct nil submission with empty matter starts, bulk submission id and no claims")
    void shouldGetNilSubmission() {
        Submission entity = ClaimsDataTestUtil.getNilSubmission();
        when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(entity));

        SubmissionResponse result = submissionService.getSubmission(SUBMISSION_ID);

        assertThat(result.getSubmissionId()).isEqualTo(SUBMISSION_ID);
        assertThat(result.getClaims().size()).isEqualTo(0);
        assertThat(result.getMatterStarts()).isEqualTo(List.of());
        assertNull(result.getBulkSubmissionId());
    }

  @ParameterizedTest
  @MethodSource("getCalculatedTotalAmountArguments")
  @DisplayName(
      "Should retrieve a submission and correctly scale rounding variations for calculated total amount")
  void shouldGetSubmission(String inputTotalAmount, String expectedTotalAmount) {
    Submission entity = ClaimsDataTestUtil.getSubmission();
    when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(entity));
    when(claimService.getClaimsForSubmission(SUBMISSION_ID)).thenReturn(List.of());
    when(matterStartService.getMatterStartIdsForSubmission(SUBMISSION_ID)).thenReturn(List.of());
    when(submissionRepository.getCalculatedTotalAmount(SUBMISSION_ID))
        .thenReturn(new BigDecimal(inputTotalAmount));

    SubmissionResponse result = submissionService.getSubmission(SUBMISSION_ID);

    assertThat(result.getSubmissionId()).isEqualTo(SUBMISSION_ID);
    assertThat(result.getCalculatedTotalAmount()).isEqualTo(new BigDecimal(expectedTotalAmount));
  }

  private static Stream<Arguments> getCalculatedTotalAmountArguments() {
    return Stream.of(
        Arguments.of("1.234", "1.23"),
        Arguments.of("1.235", "1.24"),
        Arguments.of("-1.234", "-1.23"),
        Arguments.of("-1.235", "-1.24"));
  }

  @Test
  @DisplayName(
      "Should retrieve submission with scaled 0.00 value when calculated total amount is exactly zero")
  void shouldGetSubmissionWithZeroCalculatedTotalAmount() {
    Submission entity = ClaimsDataTestUtil.getSubmission();
    when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(entity));
    when(claimService.getClaimsForSubmission(SUBMISSION_ID)).thenReturn(List.of());
    when(matterStartService.getMatterStartIdsForSubmission(SUBMISSION_ID)).thenReturn(List.of());
    when(submissionRepository.getCalculatedTotalAmount(SUBMISSION_ID)).thenReturn(BigDecimal.ZERO);

    SubmissionResponse result = submissionService.getSubmission(SUBMISSION_ID);

    assertThat(result.getSubmissionId()).isEqualTo(SUBMISSION_ID);
    assertThat(result.getCalculatedTotalAmount()).isEqualTo(new BigDecimal("0.00"));
  }

  @Test
  @DisplayName(
      "Should retrieve submission with a null calculated total amount if no values are recorded")
  void shouldGetSubmissionWithNullCalculatedTotalAmount() {
    Submission entity = ClaimsDataTestUtil.getSubmission();
    when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(entity));
    when(claimService.getClaimsForSubmission(SUBMISSION_ID)).thenReturn(List.of());
    when(matterStartService.getMatterStartIdsForSubmission(SUBMISSION_ID)).thenReturn(List.of());
    when(submissionRepository.getCalculatedTotalAmount(SUBMISSION_ID)).thenReturn(null);

    SubmissionResponse result = submissionService.getSubmission(SUBMISSION_ID);

    assertThat(result.getSubmissionId()).isEqualTo(SUBMISSION_ID);
    assertThat(result.getCalculatedTotalAmount()).isNull();
  }

  @Test
  @DisplayName(
      "Should retrieve submission with a null assessed total amount when no assessment records exist")
  void shouldGetSubmissionWithNullAssessedTotalAmountWhenNoAssessmentsExist() {
    Submission entity = ClaimsDataTestUtil.getSubmission();

    when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(entity));
    when(claimService.getClaimsForSubmission(SUBMISSION_ID)).thenReturn(List.of());
    when(matterStartService.getMatterStartIdsForSubmission(SUBMISSION_ID)).thenReturn(List.of());
    when(submissionRepository.getCalculatedTotalAmount(SUBMISSION_ID)).thenReturn(BigDecimal.ZERO);
    when(assessmentService.getAssessedTotalAmount(SUBMISSION_ID)).thenReturn(null);

    SubmissionResponse result = submissionService.getSubmission(SUBMISSION_ID);

    assertThat(result.getSubmissionId()).isEqualTo(SUBMISSION_ID);
    assertThat(result.getAssessedTotalAmount()).isNull();
  }

  @Test
  @DisplayName(
      "Should retrieve submission with scaled 0.00 assessed total amount when assessment sums to zero")
  void shouldGetSubmissionWithZeroAssessedTotalAmountWhenAssessmentsTotalZero() {
    Submission entity = ClaimsDataTestUtil.getSubmission();

    when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(entity));
    when(claimService.getClaimsForSubmission(SUBMISSION_ID)).thenReturn(List.of());
    when(matterStartService.getMatterStartIdsForSubmission(SUBMISSION_ID)).thenReturn(List.of());
    when(submissionRepository.getCalculatedTotalAmount(SUBMISSION_ID)).thenReturn(BigDecimal.ZERO);
    when(assessmentService.getAssessedTotalAmount(SUBMISSION_ID))
        .thenReturn(new BigDecimal("0.00"));

    SubmissionResponse result = submissionService.getSubmission(SUBMISSION_ID);

    assertThat(result.getSubmissionId()).isEqualTo(SUBMISSION_ID);
    assertThat(result.getAssessedTotalAmount()).isEqualTo(new BigDecimal("0.00"));
  }

  @Test
  void shouldGetSubmissionWithAssessedTotalAmountToTwoDecimalPlaces() {
    Submission entity = ClaimsDataTestUtil.getSubmission();

    when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(entity));
    when(claimService.getClaimsForSubmission(SUBMISSION_ID)).thenReturn(List.of());
    when(matterStartService.getMatterStartIdsForSubmission(SUBMISSION_ID)).thenReturn(List.of());
    when(submissionRepository.getCalculatedTotalAmount(SUBMISSION_ID)).thenReturn(BigDecimal.ZERO);
    when(assessmentService.getAssessedTotalAmount(SUBMISSION_ID))
        .thenReturn(new BigDecimal("12.345"));

    SubmissionResponse result = submissionService.getSubmission(SUBMISSION_ID);

    assertThat(result.getSubmissionId()).isEqualTo(SUBMISSION_ID);
    assertThat(result.getAssessedTotalAmount()).isEqualTo(new BigDecimal("12.35"));
  }

  @Test
  @DisplayName("Should retrieve submission along with its top-level error messages field")
  void shouldGetSubmissionWithErrorMessages() {
    Submission entity = ClaimsDataTestUtil.getSubmission();
    when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(entity));
    when(claimService.getClaimsForSubmission(SUBMISSION_ID)).thenReturn(List.of());
    when(matterStartService.getMatterStartIdsForSubmission(SUBMISSION_ID)).thenReturn(List.of());
    when(submissionRepository.getCalculatedTotalAmount(SUBMISSION_ID)).thenReturn(BigDecimal.ZERO);

    SubmissionResponse result = submissionService.getSubmission(SUBMISSION_ID);

    assertThat(result.getSubmissionId()).isEqualTo(SUBMISSION_ID);
    assertThat(result.getErrorMessages()).isEqualTo("Test error message");
  }

  @Test
  @DisplayName(
      "Should throw SubmissionNotFoundException when retrieving a non-existent submission ID")
  void shouldThrowWhenSubmissionNotFoundOnGet() {
    UUID id = Uuid7.timeBasedUuid();
    when(submissionRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(SubmissionNotFoundException.class, () -> submissionService.getSubmission(id));
  }

  @Test
  @DisplayName("Should successfully apply patch fields and update submission entity details")
  void shouldUpdateSubmission() {
    UUID id = Uuid7.timeBasedUuid();
    Submission entity = Submission.builder().id(id).build();
    SubmissionPatch patch = new SubmissionPatch().crimeLowerScheduleNumber("456");
    when(submissionRepository.findById(id)).thenReturn(Optional.of(entity));

    submissionService.updateSubmission(id, patch);

    verify(submissionMapper).updateSubmissionFromPatch(patch, entity);
    verify(submissionRepository).save(entity);
  }

  @Test
  @DisplayName(
      "Should cascadingly update all associated claims to INVALID when submission status changes to VALIDATION_FAILED")
  void shouldUpdateAllClaimsAsInvalidWhenSubmissionStatusIsValidationFailed() {
    UUID id = Uuid7.timeBasedUuid();
    Submission entity = Submission.builder().id(id).build();
    SubmissionPatch patch = new SubmissionPatch().status(SubmissionStatus.VALIDATION_FAILED);
    when(submissionRepository.findById(id)).thenReturn(Optional.of(entity));

    submissionService.updateSubmission(id, patch);

    verify(submissionMapper).updateSubmissionFromPatch(patch, entity);
    verify(submissionRepository).save(entity);
    verify(claimService).updateAllClaimsStatusForSubmission(id, ClaimStatus.INVALID);
  }

  @Test
  @DisplayName(
      "Should trigger submission validation succeeded domain event when status changes to VALIDATION_SUCCEEDED")
  void shouldPublishValidationSucceededEventWhenSubmissionStatusIsValidationSucceeded() {
    UUID id = Uuid7.timeBasedUuid();
    Submission entity = Submission.builder().id(id).build();
    SubmissionPatch patch = new SubmissionPatch().status(SubmissionStatus.VALIDATION_SUCCEEDED);
    when(submissionRepository.findById(id)).thenReturn(Optional.of(entity));

    submissionService.updateSubmission(id, patch);

    verify(submissionMapper).updateSubmissionFromPatch(patch, entity);
    verify(submissionRepository).save(entity);
    verify(submissionEventPublisherService).publishSubmissionValidationSucceededEvent(id);
  }

  @Test
  @DisplayName("Should update submission with Legal Help and Mediation references via patch")
  void shouldUpdateSubmissionWithCivilAndMediationSubmissionReferences() {
    UUID id = Uuid7.timeBasedUuid();
    Submission entity = Submission.builder().id(id).build();
    SubmissionPatch patch =
        new SubmissionPatch()
            .legalHelpSubmissionReference("LEGAL-123")
            .mediationSubmissionReference("MED-123");
    when(submissionRepository.findById(id)).thenReturn(Optional.of(entity));

    submissionService.updateSubmission(id, patch);

    verify(submissionMapper).updateSubmissionFromPatch(patch, entity);
    verify(submissionRepository).save(entity);
  }

  @Test
  @DisplayName(
      "Should throw SubmissionNotFoundException when trying to patch a non-existent submission")
  void shouldThrowWhenSubmissionNotFoundOnUpdate() {
    UUID id = Uuid7.timeBasedUuid();
    SubmissionPatch patch = new SubmissionPatch();
    when(submissionRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(
        SubmissionNotFoundException.class, () -> submissionService.updateSubmission(id, patch));
  }

  @Test
  @DisplayName("Should process patch and map/persist incoming validation error logging collections")
  void shouldUpdateSubmissionAndLogValidationErrors() {
    UUID id = Uuid7.timeBasedUuid();
    Submission entity = Submission.builder().id(id).build();

    final ValidationMessagePatch messagePatch =
        new ValidationMessagePatch()
            .type(ValidationMessageType.ERROR)
            .source("SYSTEM")
            .displayMessage("A display message")
            .technicalMessage("A technical message");

    SubmissionPatch patch = new SubmissionPatch().validationMessages(List.of(messagePatch));
    when(submissionRepository.findById(id)).thenReturn(Optional.of(entity));
    when(submissionMapper.toValidationMessageLog(any(), eq(entity)))
        .thenReturn(new ValidationMessageLog());

    submissionService.updateSubmission(id, patch);

    verify(submissionMapper).toValidationMessageLog(any(), eq(entity));
    verify(validationMessageLogRepository).save(any(ValidationMessageLog.class));
  }

  @Test
  @DisplayName(
      "Should throw SubmissionBadRequestException when office accounts list parameter is completely omitted")
  void getSubmissionsResultSet_whenOfficesIsMissing_shouldThrowSubmissionBadRequestException() {
    assertThrows(
        SubmissionBadRequestException.class,
        () ->
            submissionService.getSubmissionsResultSet(
                null,
                SUBMISSION_ID.toString(),
                SUBMITTED_DATE_FROM,
                SUBMITTED_DATE_TO,
                AREA_OF_LAW,
                SUBMISSION_PERIOD,
                SUBMISSION_STATUSES,
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
                AREA_OF_LAW,
                SUBMISSION_PERIOD,
                SUBMISSION_STATUSES,
                Pageable.unpaged()));
  }

  @Test
  void getSubmissionsResultSet_whenFiltersMatchData_shouldReturnNonEmptyResultSet() {
    var submissionBase = SubmissionBase.builder().submissionId(UUID.randomUUID()).build();
    var submission = new Submission();
    submission.setId(submissionBase.getSubmissionId());
    Page<Submission> resultPage = new PageImpl<>(Collections.singletonList(submission));
    when(submissionRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(resultPage);

    var expectedNonEmptyResultSet =
        new SubmissionsResultSet().content(Collections.singletonList(submissionBase));
    when(submissionsResultSetMapper.toSubmissionsResultSet(resultPage))
        .thenReturn(expectedNonEmptyResultSet);

    assertThat(submissionBase.getSubmissionId()).isNotNull();
    when(assessmentService.getAssessedTotalAmounts(
            Collections.singletonList(submissionBase.getSubmissionId())))
        .thenReturn(Map.of(submissionBase.getSubmissionId(), new BigDecimal("123.40")));

    when(submissionRepository.getCalculatedTotalAmounts(
            Collections.singletonList(submissionBase.getSubmissionId())))
        .thenReturn(
            getCalcTotalsProjection(submissionBase.getSubmissionId(), new BigDecimal("789.1")));

    var actualResultSet =
        submissionService.getSubmissionsResultSet(
            OFFICE_CODES,
            SUBMISSION_ID.toString(),
            SUBMITTED_DATE_FROM,
            SUBMITTED_DATE_TO,
            AREA_OF_LAW,
            SUBMISSION_PERIOD,
            SUBMISSION_STATUSES,
            Pageable.ofSize(10).withPage(0));

    assertThat(actualResultSet.getContent()).hasSize(1);
    assertThat(actualResultSet.getContent().getFirst().getSubmissionId())
        .isEqualTo(submissionBase.getSubmissionId());
    assertThat(actualResultSet.getContent().getFirst().getAssessedTotalAmount())
        .isEqualTo(new BigDecimal("123.40"));
    assertThat(actualResultSet.getContent().getFirst().getCalculatedTotalAmount())
        .isEqualTo(new BigDecimal("789.10"));
    verify(assessmentService)
        .getAssessedTotalAmounts(Collections.singletonList(submissionBase.getSubmissionId()));
    verify(submissionRepository)
        .getCalculatedTotalAmounts(Collections.singletonList(submissionBase.getSubmissionId()));
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
            AREA_OF_LAW,
            SUBMISSION_PERIOD,
            SUBMISSION_STATUSES,
            Pageable.ofSize(10).withPage(0));

    assertThat(actualResultSet).isEqualTo(expectedEmptyResultSet);
    assertThat(actualResultSet.getContent()).isEmpty();
    verifyNoInteractions(assessmentService);
  }

  @Test
  @DisplayName(
      "Should execute findAll passing all filter parameters correctly mapped onto a JPA Specification object")
  void getSubmissionsResultSet_shouldCallFindAllWithSpecification() {
    Page<Submission> resultPage = new PageImpl<>(Collections.emptyList());

    when(submissionRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(resultPage);

    when(submissionsResultSetMapper.toSubmissionsResultSet(eq(resultPage)))
        .thenReturn(new SubmissionsResultSet());

    submissionService.getSubmissionsResultSet(
        OFFICE_CODES,
        SUBMISSION_ID.toString(),
        SUBMITTED_DATE_FROM,
        SUBMITTED_DATE_TO,
        AREA_OF_LAW,
        SUBMISSION_PERIOD,
        SUBMISSION_STATUSES,
        Pageable.ofSize(10).withPage(0));

    // Service always appends id as tie-breaker sort
    Pageable expectedPageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "id"));
    verify(submissionRepository)
        .findAll(submissionSpecificationArgumentCaptor.capture(), eq(expectedPageable));
    verifyNoInteractions(assessmentService);

    assertThat(submissionSpecificationArgumentCaptor.getValue()).isNotNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"createdOn", "areaOfLaw", "status"})
  void getSubmissionsResultSet_whenSortFieldIsValid_shouldPassSortToRepository(String sortField) {
    Pageable pageableWithSort = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, sortField));
    // Service appends a secondary sort by id using the same direction as the primary sort,
    // and applies ignoreCase to the primary sort order.
    Pageable expectedPageable =
        PageRequest.of(
            0,
            10,
            Sort.by(Sort.Order.asc(sortField).ignoreCase()).and(Sort.by(Sort.Direction.ASC, "id")));
    Page<Submission> resultPage = new PageImpl<>(Collections.emptyList());

    when(submissionRepository.findAll(any(Specification.class), eq(expectedPageable)))
        .thenReturn(resultPage);
    when(submissionsResultSetMapper.toSubmissionsResultSet(resultPage))
        .thenReturn(new SubmissionsResultSet());

    submissionService.getSubmissionsResultSet(
        OFFICE_CODES,
        SUBMISSION_ID.toString(),
        SUBMITTED_DATE_FROM,
        SUBMITTED_DATE_TO,
        AREA_OF_LAW,
        SUBMISSION_PERIOD,
        SUBMISSION_STATUSES,
        pageableWithSort);

    verify(submissionRepository).findAll(any(Specification.class), eq(expectedPageable));
  }

  @Test
  void getSubmissionsResultSet_whenSortByOfficeAccountNumber_shouldApplyIgnoreCase() {
    Pageable pageableWithSort =
        PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "officeAccountNumber"));
    // officeAccountNumber maps to itself with ignoreCase for case-insensitive ordering
    Pageable expectedPageable =
        PageRequest.of(
            0,
            10,
            Sort.by(Sort.Order.asc("officeAccountNumber").ignoreCase())
                .and(Sort.by(Sort.Direction.ASC, "id")));
    Page<Submission> resultPage = new PageImpl<>(Collections.emptyList());

    when(submissionRepository.findAll(any(Specification.class), eq(expectedPageable)))
        .thenReturn(resultPage);
    when(submissionsResultSetMapper.toSubmissionsResultSet(resultPage))
        .thenReturn(new SubmissionsResultSet());

    submissionService.getSubmissionsResultSet(
        OFFICE_CODES,
        SUBMISSION_ID.toString(),
        SUBMITTED_DATE_FROM,
        SUBMITTED_DATE_TO,
        AREA_OF_LAW,
        SUBMISSION_PERIOD,
        SUBMISSION_STATUSES,
        pageableWithSort);

    verify(submissionRepository).findAll(any(Specification.class), eq(expectedPageable));
  }

  @Test
  void getSubmissionsResultSet_whenSortBySubmissionPeriod_shouldRemapToSortKey() {
    Pageable pageableWithPeriodSort =
        PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "submissionPeriod"));
    // submissionPeriod is remapped to submissionPeriodSortKey for correct chronological ordering
    Pageable expectedPageable =
        PageRequest.of(
            0,
            10,
            Sort.by(Sort.Order.asc("submissionPeriodSortKey").ignoreCase())
                .and(Sort.by(Sort.Direction.ASC, "id")));
    Page<Submission> resultPage = new PageImpl<>(Collections.emptyList());

    when(submissionRepository.findAll(any(Specification.class), eq(expectedPageable)))
        .thenReturn(resultPage);
    when(submissionsResultSetMapper.toSubmissionsResultSet(resultPage))
        .thenReturn(new SubmissionsResultSet());

    submissionService.getSubmissionsResultSet(
        OFFICE_CODES,
        SUBMISSION_ID.toString(),
        SUBMITTED_DATE_FROM,
        SUBMITTED_DATE_TO,
        AREA_OF_LAW,
        SUBMISSION_PERIOD,
        SUBMISSION_STATUSES,
        pageableWithPeriodSort);

    verify(submissionRepository).findAll(any(Specification.class), eq(expectedPageable));
  }

  @Test
  void getSubmissionsResultSet_whenSortIsDescending_tieBreakerShouldAlsoBeDescending() {
    Pageable pageableWithDescSort =
        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdOn"));
    Pageable expectedPageable =
        PageRequest.of(
            0,
            10,
            Sort.by(Sort.Order.desc("createdOn").ignoreCase())
                .and(Sort.by(Sort.Direction.DESC, "id")));
    Page<Submission> resultPage = new PageImpl<>(Collections.emptyList());

    when(submissionRepository.findAll(any(Specification.class), eq(expectedPageable)))
        .thenReturn(resultPage);
    when(submissionsResultSetMapper.toSubmissionsResultSet(resultPage))
        .thenReturn(new SubmissionsResultSet());

    submissionService.getSubmissionsResultSet(
        OFFICE_CODES,
        SUBMISSION_ID.toString(),
        SUBMITTED_DATE_FROM,
        SUBMITTED_DATE_TO,
        AREA_OF_LAW,
        SUBMISSION_PERIOD,
        SUBMISSION_STATUSES,
        pageableWithDescSort);

    verify(submissionRepository).findAll(any(Specification.class), eq(expectedPageable));
  }

  @Test
  void getSubmissionsResultSet_alwaysAppendsTieBreakerSortById() {
    Pageable unorderedPageable = Pageable.ofSize(10).withPage(0);
    // No primary sort, so tie-breaker defaults to ASC
    Pageable expectedPageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "id"));
    Page<Submission> resultPage = new PageImpl<>(Collections.emptyList());

    when(submissionRepository.findAll(any(Specification.class), eq(expectedPageable)))
        .thenReturn(resultPage);
    when(submissionsResultSetMapper.toSubmissionsResultSet(resultPage))
        .thenReturn(new SubmissionsResultSet());

    submissionService.getSubmissionsResultSet(
        OFFICE_CODES,
        SUBMISSION_ID.toString(),
        SUBMITTED_DATE_FROM,
        SUBMITTED_DATE_TO,
        AREA_OF_LAW,
        SUBMISSION_PERIOD,
        SUBMISSION_STATUSES,
        unorderedPageable);

    verify(submissionRepository).findAll(any(Specification.class), eq(expectedPageable));
  }

  @Test
  void getSubmissionsResultSet_whenSortFieldIsInvalid_shouldThrowSubmissionBadRequestException() {
    Pageable pageableWithInvalidSort =
        PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "unknownField"));

    assertThrows(
        SubmissionBadRequestException.class,
        () ->
            submissionService.getSubmissionsResultSet(
                OFFICE_CODES,
                SUBMISSION_ID.toString(),
                SUBMITTED_DATE_FROM,
                SUBMITTED_DATE_TO,
                AREA_OF_LAW,
                SUBMISSION_PERIOD,
                SUBMISSION_STATUSES,
                pageableWithInvalidSort));

    verifyNoInteractions(submissionRepository);
  }

  @Test
  @DisplayName(
      "Should trigger submission validation initialization domain event when status becomes READY_FOR_VALIDATION")
  void updateSubmission_whenStatusIsReadyForValidation_shouldPublishValidationEvent() {
    UUID id = Uuid7.timeBasedUuid();
    Submission entity = Submission.builder().id(id).build();
    SubmissionPatch patch = new SubmissionPatch().status(SubmissionStatus.READY_FOR_VALIDATION);
    when(submissionRepository.findById(id)).thenReturn(Optional.of(entity));

    submissionService.updateSubmission(id, patch);

    verify(submissionMapper).updateSubmissionFromPatch(patch, entity);
    verify(submissionRepository).save(entity);
    verify(submissionEventPublisherService).publishSubmissionValidationEvent(id);
  }

  @Test
  @DisplayName(
      "Should bypass validation logging entirely if provided validation messages collection list payload is empty")
  void updateSubmission_whenValidationMessagesListIsEmpty_shouldNotSaveLogs() {
    UUID id = Uuid7.timeBasedUuid();
    Submission entity = Submission.builder().id(id).build();
    SubmissionPatch patch = new SubmissionPatch().validationMessages(Collections.emptyList());
    when(submissionRepository.findById(id)).thenReturn(Optional.of(entity));

    submissionService.updateSubmission(id, patch);

    verify(submissionRepository).save(entity);
    verifyNoInteractions(validationMessageLogRepository);
  }

  @Test
  @DisplayName(
      "Should return an empty tracking map immediately and bypass repository lookups if search context list is empty")
  void getCalculatedTotalAmounts_whenInputIsEmpty_shouldReturnEmptyMap() {
    Map<UUID, BigDecimal> result =
        submissionService.getCalculatedTotalAmounts(Collections.emptyList());

    assertThat(result).isEmpty();
    verifyNoInteractions(submissionRepository);
  }

  @Test
  @DisplayName(
      "Should return an empty tracking map immediately and bypass repository lookups if search context list is null")
  void getCalculatedTotalAmounts_whenInputIsNull_shouldReturnEmptyMap() {
    Map<UUID, BigDecimal> result = submissionService.getCalculatedTotalAmounts(null);

    assertThat(result).isEmpty();
    verifyNoInteractions(submissionRepository);
  }

  @Test
  @DisplayName(
      "Should cleanly discard structural projection entries containing invalid null fields during calculated map aggregation")
  void getCalculatedTotalAmounts_whenProjectionsContainNulls_shouldFilterThemOutSafely() {
    UUID validId = UUID.randomUUID();

    List<SubmissionRepository.CalculatedTotalAmountProjection> projections = new ArrayList<>();
    projections.add(new TestProjection(validId, new BigDecimal("500.00")));
    projections.add(new TestProjection(null, new BigDecimal("100.00")));
    projections.add(new TestProjection(UUID.randomUUID(), null));

    List<UUID> queryIds = List.of(validId);
    when(submissionRepository.getCalculatedTotalAmounts(queryIds)).thenReturn(projections);

    Map<UUID, BigDecimal> result = submissionService.getCalculatedTotalAmounts(queryIds);

    assertThat(result).hasSize(1);
    assertThat(result).containsKey(validId);
    assertThat(result.get(validId)).isEqualTo(new BigDecimal("500.00"));
  }

  @Test
  @DisplayName(
      "Should retrieve submission with null calculated and assessed total amounts when neither exists")
  void shouldGetSubmissionWithNullCalculatedAndAssessedTotalAmounts() {
    Submission entity = ClaimsDataTestUtil.getSubmission();
    when(submissionRepository.findById(SUBMISSION_ID)).thenReturn(Optional.of(entity));
    when(claimService.getClaimsForSubmission(SUBMISSION_ID)).thenReturn(List.of());
    when(matterStartService.getMatterStartIdsForSubmission(SUBMISSION_ID)).thenReturn(List.of());

    // Both backend systems return null
    when(submissionRepository.getCalculatedTotalAmount(SUBMISSION_ID)).thenReturn(null);
    when(assessmentService.getAssessedTotalAmount(SUBMISSION_ID)).thenReturn(null);

    SubmissionResponse result = submissionService.getSubmission(SUBMISSION_ID);

    assertThat(result.getSubmissionId()).isEqualTo(SUBMISSION_ID);
    assertThat(result.getCalculatedTotalAmount()).isNull();
    assertThat(result.getAssessedTotalAmount()).isNull();
  }

  private List<SubmissionRepository.CalculatedTotalAmountProjection> getCalcTotalsProjection(
      UUID submissionId, BigDecimal expectedCalcValue) {

    TestProjection projection = new TestProjection(submissionId, expectedCalcValue);
    return new ArrayList<>(List.of(projection));
  }
}

@AllArgsConstructor
@Data
class TestProjection implements SubmissionRepository.CalculatedTotalAmountProjection {

  private UUID submissionId;
  private BigDecimal total;
}
