package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.ClaimValidationService.ASSESSMENT_REASON_MUST_BE_PROVIDED_ERROR;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.ClaimValidationService.CLAIM_IS_ALREADY_VOID_STATUS_ERROR;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.ClaimValidationService.CLAIM_WITH_ID_DOES_NOT_HAVE_VALID_STATUS_ERROR;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.ClaimValidationService.NO_CLAIM_FOUND_WITH_ID_ERROR;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.ClaimValidationService.NO_SUMMARY_FEE_FOR_CLAIM_ID_ERROR;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_USER_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CASE_REFERENCE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.FEE_CODE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.OFFICE_ACCOUNT_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_PERIOD;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.UNIQUE_CASE_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.UNIQUE_CLIENT_NUMBER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.UNIQUE_FILE_NUMBER;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.ClaimSearchRequest;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Assessment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimCase;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimBadRequestException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimSummaryFeeNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.DuplicateClaimException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimResultSetMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClientMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AssessmentType;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimAmendmentPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponseV2;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResultSetV2;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionClaim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.AssessmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimCaseRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClientRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationMessageLogRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.ClaimAmendmentService;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.ClaimAmendmentStateService;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;
import uk.gov.justice.laa.dstew.payments.claimsdata.validator.ClaimSearchRequestValidator;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {
  @Mock private SubmissionRepository submissionRepository;
  @Mock private ClaimRepository claimRepository;
  @Mock private ClientRepository clientRepository;
  @Mock private ClaimMapper claimMapper;
  @Mock private ClientMapper clientMapper;
  @Mock private ValidationMessageLogRepository validationMessageLogRepository;
  @Mock private ClaimResultSetMapper claimResultSetMapper;
  @Mock private ClaimSummaryFeeRepository claimSummaryFeeRepository;
  @Mock private CalculatedFeeDetailRepository calculatedFeeDetailRepository;
  @Mock private ClaimCaseRepository claimCaseRepository;
  @Mock private AssessmentRepository assessmentRepository;
  @Mock private ClaimValidationService claimValidationService;
  @Mock private AssessmentService assessmentService;
  @Mock private ClaimAmendmentService claimAmendmentService;
  @Mock private ClaimAmendmentStateService claimAmendmentStateService;

  @Spy
  private final ClaimSearchRequestValidator claimSearchRequestValidator =
      new ClaimSearchRequestValidator();

  @Captor ArgumentCaptor<Assessment> assessmentCaptor;

  @InjectMocks private ClaimService claimService;

  @DisplayName("create claim and client when client data provided (parameterized)")
  @ParameterizedTest
  @MethodSource("getClientTestingArguments")
  void shouldCreateClaimAndClient(Client client) {
    final UUID submissionId = Uuid7.timeBasedUuid();
    final Submission submission = Submission.builder().id(submissionId).build();
    final ClaimPost post = new ClaimPost();
    post.setCreatedByUserId(API_USER_ID);
    final Claim claim = Claim.builder().build();
    final ClaimSummaryFee claimSummaryFee = ClaimSummaryFee.builder().build();
    final ClaimCase claimCase = ClaimCase.builder().build();

    when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
    when(claimMapper.toClaim(post)).thenReturn(claim);
    when(clientMapper.toClient(post)).thenReturn(client);
    when(claimMapper.toClaimSummaryFee(post)).thenReturn(claimSummaryFee);
    when(claimMapper.toClaimCase(post)).thenReturn(claimCase);

    final UUID id = claimService.createClaim(submissionId, post);

    assertThat(id).isNotNull();
    assertThat(claim.getId()).isEqualTo(id);
    assertThat(claim.getCreatedByUserId()).isEqualTo(API_USER_ID);
    assertThat(client.getClaim()).isSameAs(claim);
    assertThat(client.getCreatedByUserId()).isEqualTo(API_USER_ID);
    assertThat(claimSummaryFee.getCreatedByUserId()).isEqualTo(API_USER_ID);
    assertThat(claimSummaryFee.getClaim()).isSameAs(claim);
    assertThat(claimSummaryFee.getCreatedByUserId()).isEqualTo(API_USER_ID);
    assertThat(claimCase.getClaim()).isSameAs(claim);
    assertThat(claimCase.getCreatedByUserId()).isEqualTo(API_USER_ID);
    verify(claimRepository).save(claim);
    verify(clientRepository).save(client);
    verify(claimSummaryFeeRepository).save(claimSummaryFee);
    verify(claimCaseRepository).save(claimCase);
  }

  public static Stream<Arguments> getClientTestingArguments() {
    return Stream.of(
        Arguments.of(Client.builder().clientForename("John").build()),
        Arguments.of(Client.builder().clientSurname("Smith").build()),
        Arguments.of(Client.builder().clientDateOfBirth(LocalDate.of(1980, 1, 1)).build()),
        Arguments.of(Client.builder().client2Forename("TestName").build()),
        Arguments.of(Client.builder().client2Surname("TestSurname").build()),
        Arguments.of(Client.builder().client2DateOfBirth(LocalDate.of(1983, 12, 12)).build()));
  }

  @DisplayName("create claim without client when no client data")
  @Test
  void shouldCreateClaimWithoutClientWhenNoClientData() {
    final UUID submissionId = Uuid7.timeBasedUuid();
    final Submission submission = Submission.builder().id(submissionId).build();
    final ClaimPost post = new ClaimPost();
    final Claim claim = Claim.builder().build();
    final Client emptyClient = Client.builder().build();
    final ClaimSummaryFee claimSummaryFee = ClaimSummaryFee.builder().build();
    final ClaimCase claimCase = ClaimCase.builder().build();

    when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
    when(claimMapper.toClaim(post)).thenReturn(claim);
    when(clientMapper.toClient(post)).thenReturn(emptyClient);
    when(claimMapper.toClaimSummaryFee(post)).thenReturn(claimSummaryFee);
    when(claimMapper.toClaimCase(post)).thenReturn(claimCase);

    final UUID id = claimService.createClaim(submissionId, post);

    assertThat(id).isNotNull();
    verify(claimRepository).save(claim);
    verify(clientRepository, never()).save(emptyClient);
  }

  @DisplayName("throw SubmissionNotFoundException when submission not found on create")
  @Test
  void shouldThrowWhenSubmissionNotFoundOnCreate() {
    final UUID submissionId = Uuid7.timeBasedUuid();
    final ClaimPost post = new ClaimPost();

    when(submissionRepository.findById(submissionId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> claimService.createClaim(submissionId, post))
        .isInstanceOf(SubmissionNotFoundException.class)
        .hasMessageContaining(submissionId.toString());
  }

  @DisplayName("throw DuplicateClaimException when claim line number already exists in submission")
  @Test
  void shouldThrowConflictWhenClaimLineNumberAlreadyExistsInSubmission() {
    final UUID submissionId = Uuid7.timeBasedUuid();
    final Submission submission = Submission.builder().id(submissionId).build();
    final ClaimPost post = new ClaimPost();
    post.setLineNumber(7);

    when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
    when(claimRepository.existsBySubmissionIdAndLineNumber(submissionId, 7)).thenReturn(true);

    assertThatThrownBy(() -> claimService.createClaim(submissionId, post))
        .isInstanceOf(DuplicateClaimException.class)
        .hasMessageContaining("line number 7");

    // Fails fast: no claim (or child records) is written.
    verify(claimRepository, never()).save(any());
  }

  @DisplayName("get a claim with associated data")
  @Test
  void shouldGetClaim() {
    final UUID submissionId = Uuid7.timeBasedUuid();
    final UUID claimId = Uuid7.timeBasedUuid();
    final Claim claim = Claim.builder().id(claimId).build();
    final ClaimResponse fields = new ClaimResponse();
    final Client client = Client.builder().clientForename("John").build();
    final ClaimSummaryFee claimSummaryFee = new ClaimSummaryFee();
    final CalculatedFeeDetail calculatedFeeDetail = new CalculatedFeeDetail();
    final ClaimCase claimCase = ClaimCase.builder().id(claimId).build();

    when(claimRepository.findByIdAndSubmissionId(claimId, submissionId))
        .thenReturn(Optional.of(claim));
    when(claimMapper.toClaimResponse(claim)).thenReturn(fields);
    when(clientRepository.findByClaimId(claimId)).thenReturn(Optional.of(client));
    when(claimSummaryFeeRepository.findByClaimId(claimId)).thenReturn(Optional.of(claimSummaryFee));
    when(calculatedFeeDetailRepository.findFirstByClaimIdOrderByCreatedOnDescIdDesc(claimId))
        .thenReturn(Optional.of(calculatedFeeDetail));
    when(claimCaseRepository.findByClaimId(claimId)).thenReturn(Optional.of(claimCase));

    final ClaimResponse result = claimService.getClaim(submissionId, claimId);

    assertThat(result).isSameAs(fields);
    verify(clientMapper).updateClaimResponseFromClient(client, fields);
    verify(claimMapper).updateClaimResponseFromClaimSummaryFee(claimSummaryFee, fields);
    verify(claimMapper).updateClaimResponseFromCalculatedFeeDetail(calculatedFeeDetail, fields);
    verify(claimMapper).updateClaimResponseFromClaimCase(claimCase, fields);
  }

  @DisplayName("get a claim without client data")
  @Test
  void shouldGetClaimWithoutClient() {
    final UUID submissionId = Uuid7.timeBasedUuid();
    final UUID claimId = Uuid7.timeBasedUuid();
    final Claim claim = Claim.builder().id(claimId).build();
    final ClaimResponse fields = new ClaimResponse();

    when(claimRepository.findByIdAndSubmissionId(claimId, submissionId))
        .thenReturn(Optional.of(claim));
    when(claimMapper.toClaimResponse(claim)).thenReturn(fields);
    when(clientRepository.findByClaimId(claimId)).thenReturn(Optional.empty());
    when(claimSummaryFeeRepository.findByClaimId(claimId)).thenReturn(Optional.empty());
    when(calculatedFeeDetailRepository.findFirstByClaimIdOrderByCreatedOnDescIdDesc(claimId))
        .thenReturn(Optional.empty());
    when(claimCaseRepository.findByClaimId(claimId)).thenReturn(Optional.empty());

    final ClaimResponse result = claimService.getClaim(submissionId, claimId);

    assertThat(result).isSameAs(fields);
    verify(clientMapper, never()).updateClaimResponseFromClient(any(), eq(fields));
  }

  @DisplayName("get a claim without claim summary fee")
  @Test
  void shouldGetClaimWithoutClaimSummaryFee() {
    final UUID submissionId = Uuid7.timeBasedUuid();
    final UUID claimId = Uuid7.timeBasedUuid();
    final Claim claim = Claim.builder().id(claimId).build();
    final ClaimResponse fields = new ClaimResponse();
    final CalculatedFeeDetail calculatedFeeDetail = new CalculatedFeeDetail();

    when(claimRepository.findByIdAndSubmissionId(claimId, submissionId))
        .thenReturn(Optional.of(claim));
    when(claimMapper.toClaimResponse(claim)).thenReturn(fields);
    when(clientRepository.findByClaimId(claimId)).thenReturn(Optional.empty());
    when(claimSummaryFeeRepository.findByClaimId(claimId)).thenReturn(Optional.empty());
    when(calculatedFeeDetailRepository.findFirstByClaimIdOrderByCreatedOnDescIdDesc(claimId))
        .thenReturn(Optional.of(calculatedFeeDetail));

    final ClaimResponse result = claimService.getClaim(submissionId, claimId);

    assertThat(result).isSameAs(fields);
    verify(claimMapper, never()).updateClaimResponseFromClaimSummaryFee(any(), eq(fields));
    verify(claimMapper).updateClaimResponseFromCalculatedFeeDetail(calculatedFeeDetail, fields);
  }

  @DisplayName("get a claim without calculated fee detail")
  @Test
  void shouldGetClaimWithoutCalculatedFeeDetail() {
    final UUID submissionId = Uuid7.timeBasedUuid();
    final UUID claimId = Uuid7.timeBasedUuid();
    final Claim claim = Claim.builder().id(claimId).build();
    final ClaimResponse fields = new ClaimResponse();
    final ClaimSummaryFee claimSummaryFee = new ClaimSummaryFee();

    when(claimRepository.findByIdAndSubmissionId(claimId, submissionId))
        .thenReturn(Optional.of(claim));
    when(claimMapper.toClaimResponse(claim)).thenReturn(fields);
    when(clientRepository.findByClaimId(claimId)).thenReturn(Optional.empty());
    when(claimSummaryFeeRepository.findByClaimId(claimId)).thenReturn(Optional.of(claimSummaryFee));
    when(calculatedFeeDetailRepository.findFirstByClaimIdOrderByCreatedOnDescIdDesc(claimId))
        .thenReturn(Optional.empty());

    final ClaimResponse result = claimService.getClaim(submissionId, claimId);

    assertThat(result).isSameAs(fields);
    verify(claimMapper).updateClaimResponseFromClaimSummaryFee(claimSummaryFee, fields);
    verify(claimMapper, never()).updateClaimResponseFromCalculatedFeeDetail(any(), eq(fields));
  }

  @DisplayName("get a claim without claim case")
  @Test
  void shouldGetClaimWithoutClaimCase() {
    final UUID submissionId = Uuid7.timeBasedUuid();
    final UUID claimId = Uuid7.timeBasedUuid();
    final Claim claim = Claim.builder().id(claimId).build();
    final ClaimResponse fields = new ClaimResponse();
    final CalculatedFeeDetail calculatedFeeDetail = new CalculatedFeeDetail();

    when(claimRepository.findByIdAndSubmissionId(claimId, submissionId))
        .thenReturn(Optional.of(claim));
    when(claimMapper.toClaimResponse(claim)).thenReturn(fields);
    when(clientRepository.findByClaimId(claimId)).thenReturn(Optional.empty());
    when(claimSummaryFeeRepository.findByClaimId(claimId)).thenReturn(Optional.empty());
    when(calculatedFeeDetailRepository.findFirstByClaimIdOrderByCreatedOnDescIdDesc(claimId))
        .thenReturn(Optional.of(calculatedFeeDetail));
    when(claimCaseRepository.findByClaimId(claimId)).thenReturn(Optional.empty());

    final ClaimResponse result = claimService.getClaim(submissionId, claimId);

    assertThat(result).isSameAs(fields);
    verify(claimMapper, never()).updateClaimResponseFromClaimCase(any(), eq(fields));
    verify(claimMapper).updateClaimResponseFromCalculatedFeeDetail(calculatedFeeDetail, fields);
  }

  @DisplayName("throw ClaimNotFoundException when claim not found")
  @Test
  void shouldThrowWhenClaimNotFound() {
    final UUID submissionId = Uuid7.timeBasedUuid();
    final UUID claimId = Uuid7.timeBasedUuid();

    when(claimRepository.findByIdAndSubmissionId(claimId, submissionId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> claimService.getClaim(submissionId, claimId))
        .isInstanceOf(ClaimNotFoundException.class)
        .hasMessageContaining(claimId.toString())
        .hasMessageContaining(submissionId.toString());
  }

  @DisplayName("get claim v2 response")
  @Test
  void shouldGetClaimV2() {
    final UUID submissionId = Uuid7.timeBasedUuid();
    final UUID claimId = Uuid7.timeBasedUuid();
    final Claim claim = Claim.builder().id(claimId).build();
    final ClaimResponseV2 fields = new ClaimResponseV2();

    when(claimRepository.findByIdAndSubmissionId(claimId, submissionId))
        .thenReturn(Optional.of(claim));
    when(claimMapper.toClaimResponseV2(claim)).thenReturn(fields);

    final ClaimResponseV2 result = claimService.getClaimV2(submissionId, claimId);

    assertThat(result).isSameAs(fields);
  }

  @DisplayName("throw ClaimNotFoundException when claim v2 not found")
  @Test
  void shouldThrowWhenClaimV2NotFound() {
    final UUID submissionId = Uuid7.timeBasedUuid();
    final UUID claimId = Uuid7.timeBasedUuid();

    when(claimRepository.findByIdAndSubmissionId(claimId, submissionId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> claimService.getClaimV2(submissionId, claimId))
        .isInstanceOf(ClaimNotFoundException.class)
        .hasMessageContaining(claimId.toString())
        .hasMessageContaining(submissionId.toString());
  }

  @DisplayName("update a claim")
  @Test
  void shouldUpdateClaim() {
    final UUID submissionId = Uuid7.timeBasedUuid();
    final UUID claimId = Uuid7.timeBasedUuid();
    final Claim claim = Claim.builder().id(claimId).version(1L).build();
    final ClaimAmendmentPatch patch = new ClaimAmendmentPatch();
    patch.setStatus(ClaimStatus.READY_TO_PROCESS);

    when(claimRepository.findByIdAndSubmissionId(claimId, submissionId))
        .thenReturn(Optional.of(claim));

    claimService.updateClaim(submissionId, claimId, patch);

    verify(claimMapper).updateSubmissionClaimFromPatch(patch, claim);
    verify(claimRepository).save(claim);
  }

  @DisplayName("throw ClaimNotFoundException when updating a non-existent claim")
  @Test
  void shouldThrowWhenClaimNotFoundOnUpdate() {
    final UUID submissionId = Uuid7.timeBasedUuid();
    final UUID claimId = Uuid7.timeBasedUuid();
    final ClaimAmendmentPatch patch = new ClaimAmendmentPatch();
    patch.setVersion(JsonNullable.of(1L));

    when(claimRepository.findByIdAndSubmissionId(claimId, submissionId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> claimService.updateClaim(submissionId, claimId, patch))
        .isInstanceOf(ClaimNotFoundException.class)
        .hasMessageContaining(claimId.toString())
        .hasMessageContaining(submissionId.toString());
  }

  @DisplayName("create calculated fee details on update")
  @Test
  void shouldCreateCalculatedFeeDetails() {
    final Submission submission = ClaimsDataTestUtil.getSubmission();
    // Added version to mock Claim
    final Claim claim =
        ClaimsDataTestUtil.getClaimBuilder().submission(submission).version(1L).build();
    final ClaimAmendmentPatch patch = new ClaimAmendmentPatch();
    patch.setStatus(ClaimStatus.READY_TO_PROCESS);
    final FeeCalculationPatch feeCalculationPatch = new FeeCalculationPatch();
    patch.setFeeCalculationResponse(feeCalculationPatch);
    patch.setValidationMessages(Collections.emptyList());

    final ClaimSummaryFee claimSummaryFee = new ClaimSummaryFee();
    claimSummaryFee.setId(Uuid7.timeBasedUuid());
    claimSummaryFee.setClaim(claim);
    when(claimRepository.findByIdAndSubmissionId(CLAIM_1_ID, SUBMISSION_ID))
        .thenReturn(Optional.of(claim));
    when(claimSummaryFeeRepository.findByClaim(claim)).thenReturn(Optional.of(claimSummaryFee));
    final CalculatedFeeDetail calculatedFeeDetail = new CalculatedFeeDetail();
    when(claimMapper.toCalculatedFeeDetail(feeCalculationPatch)).thenReturn(calculatedFeeDetail);

    claimService.updateClaim(SUBMISSION_ID, CLAIM_1_ID, patch);

    verify(claimMapper).updateSubmissionClaimFromPatch(any(), eq(claim));
    verify(claimRepository).save(claim);
    verify(calculatedFeeDetailRepository).save(calculatedFeeDetail);
  }

  @DisplayName("update calculated fee details on update")
  @Test
  void shouldUpdateCalculatedFeeDetails() {
    final Submission submission = ClaimsDataTestUtil.getSubmission();
    // Added version to mock Claim
    final Claim claim =
        ClaimsDataTestUtil.getClaimBuilder().submission(submission).version(1L).build();
    final ClaimAmendmentPatch patch = new ClaimAmendmentPatch();
    patch.setStatus(ClaimStatus.READY_TO_PROCESS);
    final FeeCalculationPatch feeCalculationPatch = new FeeCalculationPatch();
    patch.setFeeCalculationResponse(feeCalculationPatch);
    patch.setValidationMessages(Collections.emptyList());

    final ClaimSummaryFee claimSummaryFee = new ClaimSummaryFee();
    claimSummaryFee.setId(Uuid7.timeBasedUuid());
    claimSummaryFee.setClaim(claim);
    when(claimRepository.findByIdAndSubmissionId(CLAIM_1_ID, SUBMISSION_ID))
        .thenReturn(Optional.of(claim));
    when(claimSummaryFeeRepository.findByClaim(claim)).thenReturn(Optional.of(claimSummaryFee));
    final CalculatedFeeDetail calculatedFeeDetail = new CalculatedFeeDetail();
    UUID calculatedFeeDetailId = new UUID(0, 1);
    calculatedFeeDetail.setId(calculatedFeeDetailId);
    when(calculatedFeeDetailRepository.findFirstByClaimIdOrderByCreatedOnDescIdDesc(CLAIM_1_ID))
        .thenReturn(Optional.of(calculatedFeeDetail));
    final CalculatedFeeDetail resultingFeeDetail = new CalculatedFeeDetail();
    when(claimMapper.toCalculatedFeeDetail(feeCalculationPatch)).thenReturn(resultingFeeDetail);

    claimService.updateClaim(SUBMISSION_ID, CLAIM_1_ID, patch);

    verify(claimMapper).updateSubmissionClaimFromPatch(any(), eq(claim));
    verify(claimRepository).save(claim);
    verify(calculatedFeeDetailRepository).save(resultingFeeDetail);
  }

  @DisplayName("throw ClaimSummaryFeeNotFoundException when summary fee missing on update")
  @Test
  void shouldThrowWhenClaimSummaryFeeNotFoundOnUpdate() {
    final ClaimAmendmentPatch patch = new ClaimAmendmentPatch();
    patch.setStatus(ClaimStatus.READY_TO_PROCESS);
    final Submission submission = ClaimsDataTestUtil.getSubmission();
    // Added version to mock Claim
    final Claim claim =
        ClaimsDataTestUtil.getClaimBuilder().submission(submission).version(1L).build();
    final FeeCalculationPatch feeCalculationPatch = new FeeCalculationPatch();
    patch.setFeeCalculationResponse(feeCalculationPatch);

    when(claimRepository.findByIdAndSubmissionId(CLAIM_1_ID, SUBMISSION_ID))
        .thenReturn(Optional.of(claim));
    when(claimSummaryFeeRepository.findByClaim(claim)).thenReturn(Optional.empty());

    final CalculatedFeeDetail calculatedFeeDetail = new CalculatedFeeDetail();
    when(claimMapper.toCalculatedFeeDetail(feeCalculationPatch)).thenReturn(calculatedFeeDetail);

    assertThatThrownBy(() -> claimService.updateClaim(SUBMISSION_ID, CLAIM_1_ID, patch))
        .isInstanceOf(ClaimSummaryFeeNotFoundException.class)
        .hasMessageContaining(CLAIM_1_ID.toString());
  }

  @DisplayName("get claims for a submission")
  @Test
  void shouldGetClaimsForSubmission() {
    final UUID submissionId = Uuid7.timeBasedUuid();
    final Claim claim = Claim.builder().build();
    final SubmissionClaim inner = new SubmissionClaim();

    when(claimRepository.findBySubmissionId(submissionId)).thenReturn(List.of(claim));
    when(claimMapper.toSubmissionClaim(claim)).thenReturn(inner);

    final List<SubmissionClaim> result = claimService.getClaimsForSubmission(submissionId);

    assertThat(result).containsExactly(inner);
  }

  @DisplayName("update claim and log validation errors")
  @Test
  void shouldUpdateClaimAndLogValidationErrors() {
    final UUID submissionId = Uuid7.timeBasedUuid();
    final UUID claimId = Uuid7.timeBasedUuid();
    final Claim claim =
        Claim.builder()
            .id(claimId)
            .version(1L) // Added version to mock Claim
            .submission(Submission.builder().id(submissionId).build())
            .build();
    final ClaimAmendmentPatch patch = new ClaimAmendmentPatch();
    patch.setStatus(ClaimStatus.READY_TO_PROCESS);
    final ValidationMessagePatch message1 = new ValidationMessagePatch();
    patch.setValidationMessages(List.of(message1));

    when(claimRepository.findByIdAndSubmissionId(claimId, submissionId))
        .thenReturn(Optional.of(claim));
    when(claimMapper.toValidationMessageLog(message1, claim))
        .thenReturn(new ValidationMessageLog());

    claimService.updateClaim(submissionId, claimId, patch);

    verify(claimMapper).updateSubmissionClaimFromPatch(any(), eq(claim));
    verify(claimRepository).save(claim);
    verify(claimMapper).toValidationMessageLog(message1, claim);
  }

  @Nested
  @DisplayName("updateClaim - status/amendment routing and null-clear handling")
  class UpdateClaimRoutingAndNullHandling {

    /**
     * Regression for the Pact 503: a status-only patch that carries explicit JSON nulls for other
     * fields (as the consumer serialises them) must take the legacy path and must NOT be treated as
     * an amendment. The explicit nulls must be neutralised so the mapper cannot clear persisted
     * values.
     */
    @DisplayName("status-only with explicit nulls uses legacy path and does not clear fields")
    @Test
    void statusOnlyWithExplicitNullClears_usesLegacyPathAndDoesNotClearFields() {
      final Claim claim =
          Claim.builder()
              .id(CLAIM_1_ID)
              .version(1L)
              .scheduleReference("OLD_SCH")
              .feeCode("OLD_FEE")
              .build();

      final ClaimAmendmentPatch patch = new ClaimAmendmentPatch().status(ClaimStatus.VALID);
      patch.setScheduleReference(JsonNullable.of(null));
      patch.setFeeCode(JsonNullable.of(null));

      when(claimRepository.findByIdAndSubmissionId(CLAIM_1_ID, SUBMISSION_ID))
          .thenReturn(Optional.of(claim));

      claimService.updateClaim(SUBMISSION_ID, CLAIM_1_ID, patch);

      // Legacy path taken; the amendment flow (and its feature gate) is never invoked.
      verifyNoInteractions(claimAmendmentService);

      final ArgumentCaptor<ClaimAmendmentPatch> captor =
          ArgumentCaptor.forClass(ClaimAmendmentPatch.class);
      verify(claimMapper).updateSubmissionClaimFromPatch(captor.capture(), eq(claim));
      verify(claimRepository).save(claim);

      final ClaimAmendmentPatch mapped = captor.getValue();
      assertThat(mapped.getStatus()).isEqualTo(ClaimStatus.VALID);
      assertThat(mapped.getScheduleReference()).isEqualTo(JsonNullable.undefined());
      assertThat(mapped.getFeeCode()).isEqualTo(JsonNullable.undefined());
    }

    /** An explicit null on a field that is already null is still a no-op on the legacy path. */
    @DisplayName("explicit null on already-null field stays legacy and is neutralised")
    @Test
    void explicitNullOnAlreadyNullField_staysLegacyAndIsNeutralised() {
      final Claim claim = Claim.builder().id(CLAIM_1_ID).version(1L).build();

      final ClaimAmendmentPatch patch = new ClaimAmendmentPatch().status(ClaimStatus.VALID);
      patch.setScheduleReference(JsonNullable.of(null));

      when(claimRepository.findByIdAndSubmissionId(CLAIM_1_ID, SUBMISSION_ID))
          .thenReturn(Optional.of(claim));

      claimService.updateClaim(SUBMISSION_ID, CLAIM_1_ID, patch);

      verifyNoInteractions(claimAmendmentService);
      final ArgumentCaptor<ClaimAmendmentPatch> captor =
          ArgumentCaptor.forClass(ClaimAmendmentPatch.class);
      verify(claimMapper).updateSubmissionClaimFromPatch(captor.capture(), eq(claim));
      assertThat(captor.getValue().getScheduleReference()).isEqualTo(JsonNullable.undefined());
    }

    /** A set (present, non-null) value that actually differs is a real change - an amendment. */
    @DisplayName("status with non-null field change uses amendment path")
    @Test
    void statusWithNonNullFieldChange_usesAmendmentPath() {
      final Claim claim =
          Claim.builder().id(CLAIM_1_ID).version(1L).scheduleReference("OLD_SCH").build();

      final ClaimAmendmentPatch patch =
          new ClaimAmendmentPatch().status(ClaimStatus.VALID).scheduleReference("NEW_SCH");

      when(claimRepository.findByIdAndSubmissionId(CLAIM_1_ID, SUBMISSION_ID))
          .thenReturn(Optional.of(claim));
      when(claimAmendmentService.submitAmendment(eq(claim), any()))
          .thenReturn(ClaimAmendmentResult.success(null));

      claimService.updateClaim(SUBMISSION_ID, CLAIM_1_ID, patch);

      verify(claimAmendmentService).submitAmendment(eq(claim), any());
      verify(claimMapper, never()).updateSubmissionClaimFromPatch(any(), any());
      verify(claimRepository, never()).save(claim);
    }

    /** A set value equal to the persisted value is not a change - it stays on the legacy path. */
    @DisplayName("non-null field equal to persisted value stays legacy")
    @Test
    void nonNullFieldEqualToPersistedValue_staysLegacy() {
      final Claim claim = Claim.builder().id(CLAIM_1_ID).version(1L).feeCode("ABC").build();

      final ClaimAmendmentPatch patch =
          new ClaimAmendmentPatch().status(ClaimStatus.VALID).feeCode("ABC");

      when(claimRepository.findByIdAndSubmissionId(CLAIM_1_ID, SUBMISSION_ID))
          .thenReturn(Optional.of(claim));

      claimService.updateClaim(SUBMISSION_ID, CLAIM_1_ID, patch);

      verifyNoInteractions(claimAmendmentService);
      verify(claimMapper).updateSubmissionClaimFromPatch(any(), eq(claim));
      verify(claimRepository).save(claim);
    }

    /** A missing status still signals an amendment regardless of the other fields. */
    @DisplayName("null status uses amendment path")
    @Test
    void nullStatus_usesAmendmentPath() {
      final Claim claim = Claim.builder().id(CLAIM_1_ID).version(1L).build();
      final ClaimAmendmentPatch patch = new ClaimAmendmentPatch();

      when(claimRepository.findByIdAndSubmissionId(CLAIM_1_ID, SUBMISSION_ID))
          .thenReturn(Optional.of(claim));
      when(claimAmendmentService.submitAmendment(eq(claim), any()))
          .thenReturn(ClaimAmendmentResult.success(null));

      claimService.updateClaim(SUBMISSION_ID, CLAIM_1_ID, patch);

      verify(claimAmendmentService).submitAmendment(eq(claim), any());
      verify(claimMapper, never()).updateSubmissionClaimFromPatch(any(), any());
    }
  }

  @DisplayName("getClaimResultSet throws when office code is missing")
  @Test
  void getClaimResultSet_whenOfficeCodeIsMissing_shouldThrowClaimBadRequestException() {
    assertThrows(
        ClaimBadRequestException.class,
        () ->
            claimService.getClaimResultSet(
                null,
                SUBMISSION_ID.toString(),
                List.of(SubmissionStatus.CREATED),
                FEE_CODE,
                UNIQUE_FILE_NUMBER,
                UNIQUE_CLIENT_NUMBER,
                UNIQUE_CASE_ID,
                List.of(ClaimStatus.READY_TO_PROCESS),
                SUBMISSION_PERIOD,
                CASE_REFERENCE,
                Pageable.unpaged()));
  }

  @DisplayName("getClaimResultSet throws when office code is empty string")
  @Test
  void getClaimResultSet_whenOfficeCodeIsEmptyString_shouldThrowClaimBadRequestException() {
    assertThrows(
        ClaimBadRequestException.class,
        () ->
            claimService.getClaimResultSet(
                "",
                SUBMISSION_ID.toString(),
                List.of(SubmissionStatus.CREATED),
                FEE_CODE,
                UNIQUE_FILE_NUMBER,
                UNIQUE_CLIENT_NUMBER,
                UNIQUE_CASE_ID,
                List.of(ClaimStatus.READY_TO_PROCESS),
                SUBMISSION_PERIOD,
                CASE_REFERENCE,
                Pageable.unpaged()));
  }

  @DisplayName("getClaimResultSet returns non-empty result set when filters match data")
  @ParameterizedTest
  @EnumSource(ClaimStatus.class)
  void getClaimResultSet_whenFiltersMatchData_shouldReturnNonEmptyResultSet(
      ClaimStatus claimStatus) {
    Page<Claim> resultPage = new PageImpl<>(Collections.singletonList(new Claim()));
    when(claimRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(resultPage);

    var expectedNonEmptyResultSet =
        new ClaimResultSet().content(Collections.singletonList(new ClaimResponse()));
    when(claimResultSetMapper.toClaimResultSet(resultPage)).thenReturn(expectedNonEmptyResultSet);

    var actualResultSet =
        claimService.getClaimResultSet(
            OFFICE_ACCOUNT_NUMBER,
            SUBMISSION_ID.toString(),
            List.of(SubmissionStatus.CREATED),
            FEE_CODE,
            UNIQUE_FILE_NUMBER,
            UNIQUE_CLIENT_NUMBER,
            UNIQUE_CASE_ID,
            List.of(claimStatus),
            SUBMISSION_PERIOD,
            CASE_REFERENCE,
            Pageable.ofSize(10).withPage(0));

    assertThat(actualResultSet).isEqualTo(expectedNonEmptyResultSet);
    assertThat(actualResultSet.getContent()).hasSize(1);
  }

  @DisplayName("getClaimResultSet returns empty result set when filters match no data")
  @ParameterizedTest
  @EnumSource(ClaimStatus.class)
  void getClaimResultSet_whenFiltersMatchNoData_shouldReturnEmptyResultSet(
      ClaimStatus claimStatus) {
    Page<Claim> resultPage = new PageImpl<>(Collections.emptyList());
    when(claimRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(resultPage);

    var expectedEmptyResultSet = new ClaimResultSet();
    when(claimResultSetMapper.toClaimResultSet(resultPage)).thenReturn(expectedEmptyResultSet);

    var actualResultSet =
        claimService.getClaimResultSet(
            OFFICE_ACCOUNT_NUMBER,
            SUBMISSION_ID.toString(),
            List.of(SubmissionStatus.CREATED),
            FEE_CODE,
            UNIQUE_FILE_NUMBER,
            UNIQUE_CLIENT_NUMBER,
            UNIQUE_CASE_ID,
            List.of(claimStatus),
            SUBMISSION_PERIOD,
            CASE_REFERENCE,
            Pageable.ofSize(10).withPage(0));

    assertThat(actualResultSet).isEqualTo(expectedEmptyResultSet);
    assertThat(actualResultSet.getContent()).isEmpty();
  }

  @DisplayName("getClaimResultSetV2 throws when office code is missing")
  @Test
  void getClaimResultSet_v2_whenOfficeCodeIsMissing_shouldThrowClaimBadRequestException() {
    assertThrows(
        ClaimBadRequestException.class,
        () ->
            claimService.getClaimResultSetV2(
                ClaimSearchRequest.builder()
                    .officeCode(null)
                    .submissionId(SUBMISSION_ID.toString())
                    .submissionStatuses(List.of(SubmissionStatus.CREATED))
                    .feeCode(FEE_CODE)
                    .uniqueFileNumber(UNIQUE_FILE_NUMBER)
                    .uniqueClientNumber(UNIQUE_CLIENT_NUMBER)
                    .uniqueCaseId(UNIQUE_CASE_ID)
                    .claimStatuses(List.of(ClaimStatus.READY_TO_PROCESS))
                    .submissionPeriod(SUBMISSION_PERIOD)
                    .caseReferenceNumber(CASE_REFERENCE)
                    .build(),
                Pageable.unpaged()));
  }

  @DisplayName("getClaimResultSetV2 throws when office code is empty string")
  @Test
  void getClaimResultSet_v2_whenOfficeCodeIsEmptyString_shouldThrowClaimBadRequestException() {
    assertThrows(
        ClaimBadRequestException.class,
        () ->
            claimService.getClaimResultSetV2(
                ClaimSearchRequest.builder()
                    .officeCode("")
                    .submissionId(SUBMISSION_ID.toString())
                    .submissionStatuses(List.of(SubmissionStatus.CREATED))
                    .feeCode(FEE_CODE)
                    .uniqueFileNumber(UNIQUE_FILE_NUMBER)
                    .uniqueClientNumber(UNIQUE_CLIENT_NUMBER)
                    .uniqueCaseId(UNIQUE_CASE_ID)
                    .claimStatuses(List.of(ClaimStatus.READY_TO_PROCESS))
                    .submissionPeriod(SUBMISSION_PERIOD)
                    .caseReferenceNumber(CASE_REFERENCE)
                    .build(),
                Pageable.unpaged()));
  }

  @DisplayName("getClaimResultSetV2 returns non-empty result set when filters match data")
  @Test
  void getClaimResultSet_v2_whenFiltersMatchData_shouldReturnNonEmptyResultSet() {
    Claim claim = Claim.builder().id(Uuid7.timeBasedUuid()).build();

    Page<Claim> resultPage = new PageImpl<>(Collections.singletonList(claim));
    when(claimRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(resultPage);

    var expectedNonEmptyResultSet =
        new ClaimResultSetV2()
            .content(
                Collections.singletonList(
                    ClaimResponseV2.builder().id(claim.getId().toString()).build()));
    when(claimResultSetMapper.toClaimResultSetV2(resultPage)).thenReturn(expectedNonEmptyResultSet);

    var actualResultSet =
        claimService.getClaimResultSetV2(
            ClaimSearchRequest.builder()
                .officeCode(OFFICE_ACCOUNT_NUMBER)
                .submissionId(SUBMISSION_ID.toString())
                .submissionStatuses(List.of(SubmissionStatus.CREATED))
                .feeCode(FEE_CODE)
                .uniqueFileNumber(UNIQUE_FILE_NUMBER)
                .uniqueClientNumber(UNIQUE_CLIENT_NUMBER)
                .uniqueCaseId(UNIQUE_CASE_ID)
                .claimStatuses(List.of(ClaimStatus.READY_TO_PROCESS))
                .submissionPeriod(SUBMISSION_PERIOD)
                .caseReferenceNumber(CASE_REFERENCE)
                .build(),
            PageRequest.of(0, 10, Sort.by("total_warnings")));

    assertThat(actualResultSet).isEqualTo(expectedNonEmptyResultSet);
    assertThat(actualResultSet.getContent()).hasSize(1);
  }

  private static ClaimSearchRequest validV2SearchRequest() {
    return ClaimSearchRequest.builder().officeCode(OFFICE_ACCOUNT_NUMBER).build();
  }

  @DisplayName("getClaimResultSetV2 throws for unsupported sort field")
  @Test
  void getClaimResultSetV2_unsupportedSortField_throwsClaimBadRequestException() {
    assertThatThrownBy(
            () ->
                claimService.getClaimResultSetV2(
                    validV2SearchRequest(), PageRequest.of(0, 10, Sort.by("not_a_real_field"))))
        .isInstanceOf(ClaimBadRequestException.class)
        .hasMessageContaining("Unsupported sort field: not_a_real_field");
  }

  @DisplayName("plain sort appends deterministic id tie-break in getClaimResultSetV2")
  @Test
  void getClaimResultSetV2_plainSort_appendsDeterministicIdTieBreak() {
    when(claimRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(Collections.emptyList()));
    when(claimResultSetMapper.toClaimResultSetV2(any(Page.class)))
        .thenReturn(new ClaimResultSetV2());

    claimService.getClaimResultSetV2(
        validV2SearchRequest(), PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "status")));

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(claimRepository).findAll(any(Specification.class), pageableCaptor.capture());

    Sort appliedSort = pageableCaptor.getValue().getSort();
    // Primary sort remapped to the entity property, then id ASC appended as the tie-break.
    assertThat(appliedSort.stream().map(Sort.Order::getProperty)).containsExactly("status", "id");
    assertThat(appliedSort.getOrderFor("status").getDirection()).isEqualTo(Sort.Direction.DESC);
    assertThat(appliedSort.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
  }

  @DisplayName("plain primary with computed secondary still appends id tie-break in getClaimResultSetV2")
  @Test
  void getClaimResultSetV2_plainPrimaryWithComputedSecondary_stillAppendsIdTieBreak() {
    when(claimRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(Collections.emptyList()));
    when(claimResultSetMapper.toClaimResultSetV2(any(Page.class)))
        .thenReturn(new ClaimResultSetV2());

    // Repeated sort params: plain-column primary sort followed by a computed secondary sort.
    Sort mixedSort =
        Sort.by(Sort.Direction.ASC, "effective_total_value")
            .and(Sort.by(Sort.Direction.ASC, "total_warnings"));

    claimService.getClaimResultSetV2(validV2SearchRequest(), PageRequest.of(0, 10, mixedSort));

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(claimRepository).findAll(any(Specification.class), pageableCaptor.capture());

    Sort appliedSort = pageableCaptor.getValue().getSort();
    // The computed key is stripped, but the surviving plain sort must keep a deterministic id
    // tie-break so pagination stays stable for tied effective values.
    assertThat(appliedSort.stream().map(Sort.Order::getProperty))
        .containsExactly("effectiveTotalValue", "id");
    assertThat(appliedSort.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
  }

  @DisplayName("derived claim status sort is stripped and delegated to specification in getClaimResultSetV2")
  @Test
  void getClaimResultSetV2_derivedClaimStatusSort_isStrippedAndDelegatedToSpecification() {
    when(claimRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(Collections.emptyList()));
    when(claimResultSetMapper.toClaimResultSetV2(any(Page.class)))
        .thenReturn(new ClaimResultSetV2());

    claimService.getClaimResultSetV2(
        validV2SearchRequest(),
        PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "derived_claim_status")));

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(claimRepository).findAll(any(Specification.class), pageableCaptor.capture());

    // Computed sort: the key is stripped and no id tie-break is layered onto the Pageable so that
    // the ordering Specification's own orderBy (which includes the id tie-break) is not overridden.
    assertThat(pageableCaptor.getValue().getSort().isUnsorted()).isTrue();
  }

  @DisplayName("getClaimResultSetV2 is unpaged-safe when sort provided without paging")
  @Test
  void getClaimResultSetV2_sortWithoutPaging_isUnpagedSafe() {
    when(claimRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(Collections.emptyList()));
    when(claimResultSetMapper.toClaimResultSetV2(any(Page.class)))
        .thenReturn(new ClaimResultSetV2());

    // A sort-only request (no page/size) resolves to an Unpaged pageable; must not blow up.
    Pageable unpagedSorted = Pageable.unpaged(Sort.by(Sort.Direction.ASC, "status"));

    assertThat(claimService.getClaimResultSetV2(validV2SearchRequest(), unpagedSorted)).isNotNull();

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(claimRepository).findAll(any(Specification.class), pageableCaptor.capture());
    Pageable applied = pageableCaptor.getValue();
    assertThat(applied.isUnpaged()).isTrue();
    // Plain sort still gains the deterministic id tie-break even when unpaged.
    assertThat(applied.getSort().stream().map(Sort.Order::getProperty))
        .containsExactly("status", "id");
  }

  @ParameterizedTest
  @MethodSource("provideClaimSearchRequestsForValidation")
  @DisplayName("getClaimResultSetV2 validation cases")
  void getClaimResultSetV2_validation_cases(
      String officeCode, String caseRef, boolean shouldThrow, String expectedMessageContains) {

    // Build the ClaimSearchRequest the same way as the single-case tests do
    ClaimSearchRequest request =
        ClaimSearchRequest.builder()
            .officeCode(officeCode)
            .submissionId(SUBMISSION_ID.toString())
            .submissionStatuses(List.of(SubmissionStatus.CREATED))
            .feeCode(FEE_CODE)
            .uniqueFileNumber(UNIQUE_FILE_NUMBER)
            .uniqueClientNumber(UNIQUE_CLIENT_NUMBER)
            .uniqueCaseId(UNIQUE_CASE_ID)
            .claimStatuses(List.of(ClaimStatus.READY_TO_PROCESS))
            .submissionPeriod(SUBMISSION_PERIOD)
            .caseReferenceNumber(caseRef)
            .build();

    if (shouldThrow) {
      // Extract the invocation into a single Executable so the lambda contains only one
      // invocation that may throw a runtime exception (satisfies static analysis rules
      // that warn about lambdas with multiple potential throwing invocations).
      Executable invocation = () -> claimService.getClaimResultSetV2(request, Pageable.unpaged());
      ClaimBadRequestException ex = assertThrows(ClaimBadRequestException.class, invocation);
      if (expectedMessageContains != null) {
        assertThat(ex.getMessage()).contains(expectedMessageContains);
      }
    } else {
      // Ensure repository/mappers return harmless defaults when validation passes
      Page<Claim> emptyPage = new PageImpl<>(Collections.emptyList());
      when(claimRepository.findAll(any(Specification.class), any(Pageable.class)))
          .thenReturn(emptyPage);
      when(claimResultSetMapper.toClaimResultSetV2(any(Page.class)))
          .thenReturn(new ClaimResultSetV2());

      assertThat(claimService.getClaimResultSetV2(request, Pageable.unpaged())).isNotNull();
    }
  }

  private static Stream<Arguments> provideClaimSearchRequestsForValidation() {
    return Stream.of(
        // missing office -> should throw (exact message from validator constant)
        Arguments.of((String) null, null, true, ClaimSearchRequestValidator.MISSING_OFFICE_CODE),
        Arguments.of("", null, true, ClaimSearchRequestValidator.MISSING_OFFICE_CODE),
        Arguments.of("   ", null, true, ClaimSearchRequestValidator.MISSING_OFFICE_CODE),
        // short case reference -> should throw (exact formatted message from validator constant)
        Arguments.of(
            OFFICE_ACCOUNT_NUMBER,
            "ab",
            true,
            String.format(
                ClaimSearchRequestValidator.CASE_REFERENCE_TOO_SHORT,
                ClaimSearchRequestValidator.MIN_CASE_REFERENCE_LENGTH)),
        Arguments.of(
            OFFICE_ACCOUNT_NUMBER,
            "  ab  ",
            true,
            String.format(
                ClaimSearchRequestValidator.CASE_REFERENCE_TOO_SHORT,
                ClaimSearchRequestValidator.MIN_CASE_REFERENCE_LENGTH)),
        // valid partial/contains/case-insensitive/exact -> should not throw
        Arguments.of(OFFICE_ACCOUNT_NUMBER, "ABC", false, null),
        Arguments.of(OFFICE_ACCOUNT_NUMBER, "ATE2/1", false, null),
        Arguments.of(OFFICE_ACCOUNT_NUMBER, "ate2/1", false, null),
        Arguments.of(OFFICE_ACCOUNT_NUMBER, "RAC ATE2/1", false, null),
        // no case reference -> should not throw
        Arguments.of(OFFICE_ACCOUNT_NUMBER, null, false, null));
  }

  @DisplayName("getClaimResultSetV2 returns empty result set when filters match no data")
  @Test
  void getClaimResultSet_v2_whenFiltersMatchNoData_shouldReturnEmptyResultSet() {
    Page<Claim> resultPage = new PageImpl<>(Collections.emptyList());
    when(claimRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(resultPage);

    var expectedEmptyResultSet = new ClaimResultSetV2();
    when(claimResultSetMapper.toClaimResultSetV2(resultPage)).thenReturn(expectedEmptyResultSet);

    var actualResultSet =
        claimService.getClaimResultSetV2(
            ClaimSearchRequest.builder()
                .officeCode(OFFICE_ACCOUNT_NUMBER)
                .submissionId(SUBMISSION_ID.toString())
                .submissionStatuses(List.of(SubmissionStatus.CREATED))
                .feeCode(FEE_CODE)
                .uniqueFileNumber(UNIQUE_FILE_NUMBER)
                .uniqueClientNumber(UNIQUE_CLIENT_NUMBER)
                .uniqueCaseId(UNIQUE_CASE_ID)
                .claimStatuses(List.of(ClaimStatus.READY_TO_PROCESS))
                .submissionPeriod(SUBMISSION_PERIOD)
                .caseReferenceNumber(CASE_REFERENCE)
                .build(),
            Pageable.ofSize(10).withPage(0));

    assertThat(actualResultSet).isEqualTo(expectedEmptyResultSet);
    assertThat(actualResultSet.getContent()).isEmpty();
  }

  @Nested
  @DisplayName("Void Claim Service Tests")
  class VoidClaimTests {

    @DisplayName("void claim and create assessment")
    @Test
    void shouldVoidClaimAndCreateAssessment() {
      UUID claimId = Uuid7.timeBasedUuid();
      UUID userId = Uuid7.timeBasedUuid();
      String reason = "VOID CLAIM";

      ClaimSummaryFee claimSummaryFee = ClaimSummaryFee.builder().id(claimId).build();
      Claim claim =
          Claim.builder()
              .id(claimId)
              .status(ClaimStatus.VALID)
              .claimSummaryFee(List.of(claimSummaryFee))
              .build();
      Assessment expected = getAssessment(claim, claimSummaryFee, reason, userId);

      doNothing().when(claimValidationService).validateVoidClaimParameters(claimId, userId, reason);
      when(claimValidationService.getValidClaimOrThrow(claimId)).thenReturn(claim);
      when(claimValidationService.getClaimSummaryFeeByClaimIdOrThrow(claimId))
          .thenReturn(claimSummaryFee);
      when(assessmentService.createVoidAssessment(reason, claim, claimSummaryFee, userId))
          .thenReturn(expected);
      when(assessmentRepository.save(any())).thenReturn(expected);

      claimService.voidClaimByIdAndCreateAssessment(claimId, userId, reason);

      verify(assessmentRepository, times(1)).save(assessmentCaptor.capture());
      verify(assessmentService, times(1))
          .createVoidAssessment(reason, claim, claimSummaryFee, userId);
      verify(claimValidationService, times(1)).validateVoidClaimParameters(claimId, userId, reason);
      verify(claimValidationService, times(1)).getValidClaimOrThrow(claimId);
      verify(claimValidationService, times(1)).getClaimSummaryFeeByClaimIdOrThrow(claimId);
      verifyNoMoreInteractions(claimValidationService, assessmentService, assessmentRepository);
      var captured = assessmentCaptor.getValue();

      assertThat(claim.getStatus()).isEqualTo(ClaimStatus.VOID);
      assertThat(claim.isHasAssessment()).isEqualTo(true);
      assertThat(claim.getUpdatedByUserId()).isEqualTo(userId.toString());

      assertThat(captured)
          .usingRecursiveComparison()
          .ignoringFields("id", "createdOn", "updatedOn")
          .isEqualTo(expected);

      assertThat(captured)
          .extracting(Assessment::getId, Assessment::getCreatedOn, Assessment::getUpdatedOn)
          .doesNotContainNull();
    }

    @DisplayName("validate void claim parameters before processing")
    @Test
    void shouldValidateVoidClaimParametersBeforeProcessing() {
      UUID claimId = Uuid7.timeBasedUuid();
      UUID userId = Uuid7.timeBasedUuid();
      String reason = "VOID CLAIM";

      Claim claim = Claim.builder().id(claimId).status(ClaimStatus.VALID).build();
      ClaimSummaryFee fee = ClaimSummaryFee.builder().id(Uuid7.timeBasedUuid()).build();
      Assessment assessment = getAssessment(claim, fee, reason, userId);

      when(claimValidationService.getValidClaimOrThrow(claimId)).thenReturn(claim);
      when(claimValidationService.getClaimSummaryFeeByClaimIdOrThrow(claimId)).thenReturn(fee);
      when(assessmentService.createVoidAssessment(reason, claim, fee, userId))
          .thenReturn(assessment);
      when(assessmentRepository.save(any())).thenReturn(assessment);

      claimService.voidClaimByIdAndCreateAssessment(claimId, userId, reason);

      verify(claimValidationService).validateVoidClaimParameters(claimId, userId, reason);
    }

    @DisplayName("throw when void reason is blank")
    @Test
    void shouldThrowExceptionWhenReasonIsBlank() {

      UUID claimId = Uuid7.timeBasedUuid();
      UUID userId = Uuid7.timeBasedUuid();

      doThrow(new ClaimBadRequestException(ASSESSMENT_REASON_MUST_BE_PROVIDED_ERROR))
          .when(claimValidationService)
          .validateVoidClaimParameters(claimId, userId, "");

      assertThatThrownBy(() -> claimService.voidClaimByIdAndCreateAssessment(claimId, userId, ""))
          .isInstanceOf(ClaimBadRequestException.class)
          .hasMessageContaining(ASSESSMENT_REASON_MUST_BE_PROVIDED_ERROR);

      verifyNoInteractions(assessmentService);
      verifyNoInteractions(assessmentRepository);
    }

    @DisplayName("do not save assessment when factory fails")
    @Test
    void shouldNotSaveAssessmentWhenFactoryFails() {

      UUID claimId = Uuid7.timeBasedUuid();
      UUID userId = Uuid7.timeBasedUuid();

      Claim claim = Claim.builder().id(claimId).status(ClaimStatus.VALID).build();
      ClaimSummaryFee fee = ClaimSummaryFee.builder().id(Uuid7.timeBasedUuid()).build();

      when(claimValidationService.getValidClaimOrThrow(claimId)).thenReturn(claim);
      when(claimValidationService.getClaimSummaryFeeByClaimIdOrThrow(claimId)).thenReturn(fee);

      when(assessmentService.createVoidAssessment(any(), any(), any(), any()))
          .thenThrow(new RuntimeException("Factory error"));

      assertThatThrownBy(
              () -> claimService.voidClaimByIdAndCreateAssessment(claimId, userId, "reason"))
          .isInstanceOf(RuntimeException.class);

      verifyNoInteractions(assessmentRepository);
    }

    @DisplayName("do not void claim when already void")
    @Test
    void shouldNotVoidClaimWhenAlreadyVoid() {

      UUID claimId = Uuid7.timeBasedUuid();
      UUID userId = Uuid7.timeBasedUuid();

      when(claimValidationService.getValidClaimOrThrow(claimId))
          .thenThrow(
              new ClaimBadRequestException(CLAIM_IS_ALREADY_VOID_STATUS_ERROR.formatted(claimId)));

      assertThatThrownBy(
              () -> claimService.voidClaimByIdAndCreateAssessment(claimId, userId, "reason"))
          .isInstanceOf(ClaimBadRequestException.class)
          .hasMessageContaining(CLAIM_IS_ALREADY_VOID_STATUS_ERROR.formatted(claimId));

      verifyNoInteractions(assessmentService);
      verifyNoInteractions(assessmentRepository);
    }

    @DisplayName("void claim when claim already has assessment")
    @Test
    void shouldVoidClaimWhenClaimAlreadyHasAssessment() {

      UUID claimId = Uuid7.timeBasedUuid();
      UUID userId = Uuid7.timeBasedUuid();

      Claim claim =
          Claim.builder().id(claimId).status(ClaimStatus.VALID).hasAssessment(true).build();

      ClaimSummaryFee fee = ClaimSummaryFee.builder().id(Uuid7.timeBasedUuid()).build();
      Assessment assessment = getAssessment(claim, fee, "VOID", userId);

      when(claimValidationService.getValidClaimOrThrow(claimId)).thenReturn(claim);
      when(claimValidationService.getClaimSummaryFeeByClaimIdOrThrow(claimId)).thenReturn(fee);
      when(assessmentService.createVoidAssessment(any(), any(), any(), any()))
          .thenReturn(assessment);
      when(assessmentRepository.save(any())).thenReturn(assessment);

      claimService.voidClaimByIdAndCreateAssessment(claimId, userId, "VOID");

      assertThat(claim.isHasAssessment()).isTrue();
    }

    @DisplayName("throw ClaimNotFoundException when voiding a non-existent claim")
    @Test
    void shouldThrowExceptionWhenClaimNotFound() {
      UUID claimId = Uuid7.timeBasedUuid();
      UUID userId = Uuid7.timeBasedUuid();

      when(claimValidationService.getValidClaimOrThrow(claimId))
          .thenThrow(new ClaimNotFoundException(NO_CLAIM_FOUND_WITH_ID_ERROR.formatted(claimId)));

      assertThatThrownBy(
              () -> claimService.voidClaimByIdAndCreateAssessment(claimId, userId, "reason"))
          .isInstanceOf(ClaimNotFoundException.class)
          .hasMessageContaining(NO_CLAIM_FOUND_WITH_ID_ERROR.formatted(claimId));

      verifyNoInteractions(assessmentRepository);
      verifyNoInteractions(assessmentService);
    }

    @DisplayName("throw when claim status is not valid for voiding")
    @Test
    void shouldThrowExceptionWhenClaimStatusIsNotValid() {
      UUID claimId = Uuid7.timeBasedUuid();
      UUID userId = Uuid7.timeBasedUuid();

      when(claimValidationService.getValidClaimOrThrow(claimId))
          .thenThrow(
              new ClaimBadRequestException(
                  CLAIM_WITH_ID_DOES_NOT_HAVE_VALID_STATUS_ERROR.formatted(claimId)));

      assertThatThrownBy(
              () -> claimService.voidClaimByIdAndCreateAssessment(claimId, userId, "reason"))
          .isInstanceOf(ClaimBadRequestException.class)
          .hasMessageContaining(CLAIM_WITH_ID_DOES_NOT_HAVE_VALID_STATUS_ERROR.formatted(claimId));

      verifyNoInteractions(assessmentRepository);
      verifyNoInteractions(assessmentService);
    }

    @DisplayName("throw when claim summary fee not found during voiding")
    @Test
    void shouldThrowExceptionWhenClaimSummaryFeeNotFound() {
      UUID claimId = Uuid7.timeBasedUuid();
      UUID userId = Uuid7.timeBasedUuid();

      Claim claim = Claim.builder().id(claimId).status(ClaimStatus.VALID).build();
      when(claimValidationService.getValidClaimOrThrow(claimId)).thenReturn(claim);
      when(claimValidationService.getClaimSummaryFeeByClaimIdOrThrow(claimId))
          .thenThrow(
              new ClaimSummaryFeeNotFoundException(
                  NO_SUMMARY_FEE_FOR_CLAIM_ID_ERROR.formatted(claimId)));

      assertThatThrownBy(
              () -> claimService.voidClaimByIdAndCreateAssessment(claimId, userId, "reason"))
          .isInstanceOf(ClaimSummaryFeeNotFoundException.class)
          .hasMessageContaining(NO_SUMMARY_FEE_FOR_CLAIM_ID_ERROR.formatted(claimId));

      verifyNoInteractions(assessmentRepository);
      verifyNoInteractions(assessmentService);
    }

    @DisplayName("propagate exception when saving assessment fails")
    @Test
    void shouldPropagateExceptionWhenSavingAssessmentFails() {

      UUID claimId = Uuid7.timeBasedUuid();
      UUID userId = Uuid7.timeBasedUuid();

      Claim claim = Claim.builder().id(claimId).status(ClaimStatus.VALID).build();
      ClaimSummaryFee fee = ClaimSummaryFee.builder().id(Uuid7.timeBasedUuid()).build();
      Assessment assessment = getAssessment(claim, fee, "VOID", userId);

      when(claimValidationService.getValidClaimOrThrow(claimId)).thenReturn(claim);
      when(claimValidationService.getClaimSummaryFeeByClaimIdOrThrow(claimId)).thenReturn(fee);
      when(assessmentService.createVoidAssessment(any(), any(), any(), any()))
          .thenReturn(assessment);

      when(assessmentRepository.save(any())).thenThrow(new RuntimeException("DB failure"));

      assertThatThrownBy(
              () -> claimService.voidClaimByIdAndCreateAssessment(claimId, userId, "VOID"))
          .isInstanceOf(RuntimeException.class);
    }

    private static Assessment getAssessment(
        Claim claim, ClaimSummaryFee claimSummaryFee, String reason, UUID userId) {
      return Assessment.builder()
          .id(Uuid7.timeBasedUuid())
          .claim(claim)
          .claimSummaryFee(claimSummaryFee)
          .assessmentOutcome(null)
          .assessmentReason(reason)
          .assessmentType(AssessmentType.VOID)
          .fixedFeeAmount(BigDecimal.ZERO)
          .netTravelCostsAmount(BigDecimal.ZERO)
          .netWaitingCostsAmount(BigDecimal.ZERO)
          .netProfitCostsAmount(BigDecimal.ZERO)
          .disbursementAmount(BigDecimal.ZERO)
          .disbursementVatAmount(BigDecimal.ZERO)
          .netCostOfCounselAmount(BigDecimal.ZERO)
          .detentionTravelAndWaitingCostsAmount(BigDecimal.ZERO)
          .boltOnAdjournedHearingFee(BigDecimal.ZERO)
          .jrFormFillingAmount(BigDecimal.ZERO)
          .boltOnCmrhOralFee(BigDecimal.ZERO)
          .boltOnCmrhTelephoneFee(BigDecimal.ZERO)
          .boltOnSubstantiveHearingFee(BigDecimal.ZERO)
          .boltOnHomeOfficeInterviewFee(BigDecimal.ZERO)
          .assessedTotalVat(BigDecimal.ZERO)
          .assessedTotalInclVat(BigDecimal.ZERO)
          .allowedTotalVat(BigDecimal.ZERO)
          .allowedTotalInclVat(BigDecimal.ZERO)
          .createdByUserId(userId.toString())
          .createdOn(Instant.now())
          .updatedByUserId(userId.toString())
          .updatedOn(Instant.now())
          .build();
    }
  }

  @ParameterizedTest
  @CsvSource({
    "total_warnings",
    "submission_period",
    "derived_claim_status",
    "total_amount",
    "calculated_vat_amount",
    "escape_case_flag",
    "category_of_law"
  })
  @DisplayName("getClaimResultSetV2 - all computed sorts are stripped from pageable and delegated")
  void getClaimResultSetV2ComputedSortIsStrippedAndDelegatedToSpecification(String apiSortField) {
    // Arrange: Mock the repository and mapper to return empty results safely
    when(claimRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(Collections.emptyList()));
    when(claimResultSetMapper.toClaimResultSetV2(any(Page.class)))
        .thenReturn(new ClaimResultSetV2());

    // Act: Call the service with a pageable containing one of the computed fee API fields
    claimService.getClaimResultSetV2(
        validV2SearchRequest(), PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, apiSortField)));

    // Assert: Capture the sanitized Pageable that gets passed to the repository
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(claimRepository).findAll(any(Specification.class), pageableCaptor.capture());

    // Because this is a computed sort,
    // removeComputedSorts() should strip the key, and hasComputedSort() should
    // prevent the ID tie-break from being appended. The Pageable must be unsorted.
    assertThat(pageableCaptor.getValue().getSort().isUnsorted()).isTrue();
  }
}
