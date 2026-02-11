package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.ClaimSearchRequest;
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
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.SubmissionNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimResultSetMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClientMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.FeeCalculationPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionClaim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimCaseRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimSummaryFeeRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClientRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationMessageLogRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

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

  @InjectMocks private ClaimService claimService;

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

  @Test
  void shouldThrowWhenSubmissionNotFoundOnCreate() {
    final UUID submissionId = Uuid7.timeBasedUuid();
    final ClaimPost post = new ClaimPost();

    when(submissionRepository.findById(submissionId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> claimService.createClaim(submissionId, post))
        .isInstanceOf(SubmissionNotFoundException.class)
        .hasMessageContaining(submissionId.toString());
  }

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
    when(calculatedFeeDetailRepository.findByClaimId(claimId))
        .thenReturn(Optional.of(calculatedFeeDetail));
    when(claimCaseRepository.findByClaimId(claimId)).thenReturn(Optional.of(claimCase));

    final ClaimResponse result = claimService.getClaim(submissionId, claimId);

    assertThat(result).isSameAs(fields);
    verify(clientMapper).updateClaimResponseFromClient(client, fields);
    verify(claimMapper).updateClaimResponseFromClaimSummaryFee(claimSummaryFee, fields);
    verify(claimMapper).updateClaimResponseFromCalculatedFeeDetail(calculatedFeeDetail, fields);
    verify(claimMapper).updateClaimResponseFromClaimCase(claimCase, fields);
  }

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
    when(calculatedFeeDetailRepository.findByClaimId(claimId)).thenReturn(Optional.empty());
    when(claimCaseRepository.findByClaimId(claimId)).thenReturn(Optional.empty());

    final ClaimResponse result = claimService.getClaim(submissionId, claimId);

    assertThat(result).isSameAs(fields);
    verify(clientMapper, never()).updateClaimResponseFromClient(any(), eq(fields));
  }

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
    when(calculatedFeeDetailRepository.findByClaimId(claimId))
        .thenReturn(Optional.of(calculatedFeeDetail));

    final ClaimResponse result = claimService.getClaim(submissionId, claimId);

    assertThat(result).isSameAs(fields);
    verify(claimMapper, never()).updateClaimResponseFromClaimSummaryFee(any(), eq(fields));
    verify(claimMapper).updateClaimResponseFromCalculatedFeeDetail(calculatedFeeDetail, fields);
  }

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
    when(calculatedFeeDetailRepository.findByClaimId(claimId)).thenReturn(Optional.empty());

    final ClaimResponse result = claimService.getClaim(submissionId, claimId);

    assertThat(result).isSameAs(fields);
    verify(claimMapper).updateClaimResponseFromClaimSummaryFee(claimSummaryFee, fields);
    verify(claimMapper, never()).updateClaimResponseFromCalculatedFeeDetail(any(), eq(fields));
  }

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
    when(calculatedFeeDetailRepository.findByClaimId(claimId))
        .thenReturn(Optional.of(calculatedFeeDetail));
    when(claimCaseRepository.findByClaimId(claimId)).thenReturn(Optional.empty());

    final ClaimResponse result = claimService.getClaim(submissionId, claimId);

    assertThat(result).isSameAs(fields);
    verify(claimMapper, never()).updateClaimResponseFromClaimCase(any(), eq(fields));
    verify(claimMapper).updateClaimResponseFromCalculatedFeeDetail(calculatedFeeDetail, fields);
  }

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

  @Test
  void shouldUpdateClaim() {
    final UUID submissionId = Uuid7.timeBasedUuid();
    final UUID claimId = Uuid7.timeBasedUuid();
    final Claim claim = Claim.builder().id(claimId).build();
    final ClaimPatch patch = new ClaimPatch();

    when(claimRepository.findByIdAndSubmissionId(claimId, submissionId))
        .thenReturn(Optional.of(claim));

    claimService.updateClaim(submissionId, claimId, patch);

    verify(claimMapper).updateSubmissionClaimFromPatch(patch, claim);
    verify(claimRepository).save(claim);
  }

  @Test
  void shouldThrowWhenClaimNotFoundOnUpdate() {
    final UUID submissionId = Uuid7.timeBasedUuid();
    final UUID claimId = Uuid7.timeBasedUuid();
    final ClaimPatch patch = new ClaimPatch();

    when(claimRepository.findByIdAndSubmissionId(claimId, submissionId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> claimService.updateClaim(submissionId, claimId, patch))
        .isInstanceOf(ClaimNotFoundException.class)
        .hasMessageContaining(claimId.toString())
        .hasMessageContaining(submissionId.toString());
  }

  @Test
  void shouldCreateCalculatedFeeDetails() {
    final Submission submission = ClaimsDataTestUtil.getSubmission();
    final Claim claim = ClaimsDataTestUtil.getClaimBuilder().submission(submission).build();
    final ClaimPatch patch = new ClaimPatch();
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

  @Test
  void shouldUpdateCalculatedFeeDetails() {
    final Submission submission = ClaimsDataTestUtil.getSubmission();
    final Claim claim = ClaimsDataTestUtil.getClaimBuilder().submission(submission).build();
    final ClaimPatch patch = new ClaimPatch();
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
    when(calculatedFeeDetailRepository.findByClaimId(CLAIM_1_ID))
        .thenReturn(Optional.of(calculatedFeeDetail));
    final CalculatedFeeDetail resultingFeeDetail = new CalculatedFeeDetail();
    when(claimMapper.toCalculatedFeeDetail(feeCalculationPatch)).thenReturn(resultingFeeDetail);

    claimService.updateClaim(SUBMISSION_ID, CLAIM_1_ID, patch);

    verify(claimMapper).updateSubmissionClaimFromPatch(any(), eq(claim));
    verify(claimRepository).save(claim);
    verify(calculatedFeeDetailRepository).save(resultingFeeDetail);
  }

  @Test
  void shouldThrowWhenClaimSummaryFeeNotFoundOnUpdate() {
    final ClaimPatch patch = new ClaimPatch();
    final Submission submission = ClaimsDataTestUtil.getSubmission();
    final Claim claim = ClaimsDataTestUtil.getClaimBuilder().submission(submission).build();
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

  @Test
  void shouldUpdateClaimAndLogValidationErrors() {
    final UUID submissionId = Uuid7.timeBasedUuid();
    final UUID claimId = Uuid7.timeBasedUuid();
    final Claim claim =
        Claim.builder()
            .id(claimId)
            .submission(Submission.builder().id(submissionId).build())
            .build();
    final ClaimPatch patch = new ClaimPatch();
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

  @Test
  void getClaimResultSet_whenFiltersMatchData_shouldReturnNonEmptyResultSet() {
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
            List.of(ClaimStatus.READY_TO_PROCESS),
            SUBMISSION_PERIOD,
            CASE_REFERENCE,
            Pageable.ofSize(10).withPage(0));

    assertThat(actualResultSet).isEqualTo(expectedNonEmptyResultSet);
    assertThat(actualResultSet.getContent()).hasSize(1);
  }

  @Test
  void getClaimResultSet_whenFiltersMatchNoData_shouldReturnEmptyResultSet() {
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
            List.of(ClaimStatus.READY_TO_PROCESS),
            SUBMISSION_PERIOD,
            CASE_REFERENCE,
            Pageable.ofSize(10).withPage(0));

    assertThat(actualResultSet).isEqualTo(expectedEmptyResultSet);
    assertThat(actualResultSet.getContent()).isEmpty();
  }

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

  @Test
  void getClaimResultSet_v2_whenFiltersMatchData_shouldReturnNonEmptyResultSet() {
    Page<Claim> resultPage = new PageImpl<>(Collections.singletonList(new Claim()));
    when(claimRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(resultPage);

    var expectedNonEmptyResultSet =
        new ClaimResultSet().content(Collections.singletonList(new ClaimResponse()));
    when(claimResultSetMapper.toClaimResultSet(resultPage)).thenReturn(expectedNonEmptyResultSet);

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

    assertThat(actualResultSet).isEqualTo(expectedNonEmptyResultSet);
    assertThat(actualResultSet.getContent()).hasSize(1);
  }

  @Test
  void getClaimResultSet_v2_whenFiltersMatchNoData_shouldReturnEmptyResultSet() {
    Page<Claim> resultPage = new PageImpl<>(Collections.emptyList());
    when(claimRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(resultPage);

    var expectedEmptyResultSet = new ClaimResultSet();
    when(claimResultSetMapper.toClaimResultSet(resultPage)).thenReturn(expectedEmptyResultSet);

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
}
