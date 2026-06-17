package uk.gov.justice.laa.dstew.payments.claimsdata.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimResultSetMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.SubmissionMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionBase;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;

@ExtendWith(MockitoExtension.class)
class RepositoryClaimsDataProviderTest {

  @Mock private ClaimRepository claimRepository;
  @Mock private SubmissionRepository submissionRepository;
  @Mock private ClaimResultSetMapper claimResultSetMapper;
  @Mock private SubmissionMapper submissionMapper;

  @InjectMocks private RepositoryClaimsDataProvider provider;

  @Nested
  @DisplayName("getClaims")
  class GetClaims {

    private static final String OFFICE = "OFF_001";

    @Test
    @DisplayName("returns empty result set when officeCode is null")
    void nullOfficeCode_returnsEmpty() {
      ClaimResultSet result =
          provider.getClaims(null, null, null, null, null, null, null, null, null, null, null);

      assertThat(result).isNotNull();
      assertThat(result.getContent()).isEmpty();
      verify(claimRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("returns empty result set when officeCode is blank")
    void blankOfficeCode_returnsEmpty() {
      ClaimResultSet result =
          provider.getClaims("   ", null, null, null, null, null, null, null, null, null, null);

      assertThat(result).isNotNull();
      assertThat(result.getContent()).isEmpty();
      verify(claimRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("returns empty content list when repository returns empty page")
    void emptyRepositoryPage_returnsEmptyContent() {
      Page<Claim> emptyPage = Page.empty();
      ClaimResultSet emptyResultSet = new ClaimResultSet().content(Collections.emptyList());

      when(claimRepository.findAll(any(Specification.class), any(Pageable.class)))
          .thenReturn(emptyPage);
      when(claimResultSetMapper.toClaimResultSet(emptyPage)).thenReturn(emptyResultSet);

      ClaimResultSet result =
          provider.getClaims(OFFICE, null, null, null, null, null, null, null, null, null, null);

      assertThat(result).isNotNull();
      assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("passes all non-null filter parameters to the repository query")
    void allFilters_applied() {
      String submissionId = UUID.randomUUID().toString();
      List<SubmissionStatus> submissionStatuses = List.of(SubmissionStatus.VALIDATION_SUCCEEDED);
      List<ClaimStatus> claimStatuses = List.of(ClaimStatus.VALID);
      String feeCode = "FC001";
      String ufn = "UFN123";
      String ucn = "UCN456";
      String uniqueCaseId = "CASE789";

      Page<Claim> page = new PageImpl<>(List.of(buildClaim()));
      ClaimResultSet expected = new ClaimResultSet().content(List.of(new ClaimResponse()));

      when(claimRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
      when(claimResultSetMapper.toClaimResultSet(page)).thenReturn(expected);

      ClaimResultSet result =
          provider.getClaims(
              OFFICE,
              submissionId,
              submissionStatuses,
              feeCode,
              ufn,
              ucn,
              uniqueCaseId,
              claimStatuses,
              0,
              10,
              "status,asc");

      assertThat(result.getContent()).hasSize(1);
      verify(claimRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("defaults page=0, size=20 when pagination params are null")
    void nullPagination_usesDefaults() {
      Page<Claim> page = Page.empty();
      ClaimResultSet resultSet = new ClaimResultSet().content(Collections.emptyList());

      when(claimRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
      when(claimResultSetMapper.toClaimResultSet(page)).thenReturn(resultSet);

      provider.getClaims(OFFICE, null, null, null, null, null, null, null, null, null, null);

      ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
      verify(claimRepository).findAll(any(Specification.class), pageableCaptor.capture());

      Pageable captured = pageableCaptor.getValue();
      assertThat(captured.getPageNumber()).isZero();
      assertThat(captured.getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("applies sort expression to pageable")
    void sortExpression_applied() {
      Page<Claim> page = Page.empty();
      ClaimResultSet resultSet = new ClaimResultSet().content(Collections.emptyList());

      when(claimRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
      when(claimResultSetMapper.toClaimResultSet(page)).thenReturn(resultSet);

      provider.getClaims(OFFICE, null, null, null, null, null, null, null, 0, 5, "feeCode,desc");

      ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
      verify(claimRepository).findAll(any(Specification.class), pageableCaptor.capture());

      Pageable captured = pageableCaptor.getValue();
      assertThat(captured.getPageSize()).isEqualTo(5);
      assertThat(captured.getSort().getOrderFor("feeCode")).isNotNull();
      assertThat(captured.getSort().getOrderFor("feeCode").getDirection())
          .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("returns empty content list instead of null when mapper returns null content")
    void mapperReturnsNullContent_contentReplacedWithEmptyList() {
      Page<Claim> page = Page.empty();
      ClaimResultSet resultSetWithNullContent = new ClaimResultSet();
      resultSetWithNullContent.setContent(null);

      when(claimRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
      when(claimResultSetMapper.toClaimResultSet(page)).thenReturn(resultSetWithNullContent);

      ClaimResultSet result =
          provider.getClaims(OFFICE, null, null, null, null, null, null, null, null, null, null);

      assertThat(result.getContent()).isNotNull().isEmpty();
    }
  }

  @Nested
  @DisplayName("getSubmissions")
  class GetSubmissions {

    @Test
    @DisplayName("returns empty list when offices is null")
    void nullOffices_returnsEmpty() {
      List<SubmissionBase> result = provider.getSubmissions(null, null, null);

      assertThat(result).isNotNull().isEmpty();
      verify(submissionRepository, never()).findAll(any(Specification.class));
    }

    @Test
    @DisplayName("returns empty list when offices is empty")
    void emptyOffices_returnsEmpty() {
      List<SubmissionBase> result = provider.getSubmissions(Collections.emptyList(), null, null);

      assertThat(result).isNotNull().isEmpty();
      verify(submissionRepository, never()).findAll(any(Specification.class));
    }

    @Test
    @DisplayName("returns empty list when repository returns no submissions")
    void noSubmissions_returnsEmpty() {
      when(submissionRepository.findAll(any(Specification.class)))
          .thenReturn(Collections.emptyList());

      List<SubmissionBase> result = provider.getSubmissions(List.of("OFF_001"), null, null);

      assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("maps each returned entity to SubmissionBase")
    void submissions_areMappedToSubmissionBase() {
      Submission submission = buildSubmission();
      SubmissionBase expected = new SubmissionBase().submissionId(submission.getId());

      when(submissionRepository.findAll(any(Specification.class))).thenReturn(List.of(submission));
      when(submissionMapper.toSubmissionBase(submission)).thenReturn(expected);

      List<SubmissionBase> result = provider.getSubmissions(List.of("OFF_001"), null, null);

      assertThat(result).hasSize(1).containsExactly(expected);
    }

    @Test
    @DisplayName("applies areaOfLaw and submissionPeriod filters when provided")
    void optionalFilters_applied() {
      when(submissionRepository.findAll(any(Specification.class)))
          .thenReturn(Collections.emptyList());

      provider.getSubmissions(List.of("OFF_001"), AreaOfLaw.CRIME_LOWER, "JUL-2025");

      verify(submissionRepository).findAll(any(Specification.class));
    }

    @Test
    @DisplayName("null areaOfLaw and submissionPeriod are treated as no-filter")
    void nullOptionalFilters_ignored() {
      when(submissionRepository.findAll(any(Specification.class)))
          .thenReturn(Collections.emptyList());

      provider.getSubmissions(List.of("OFF_001"), null, null);

      verify(submissionRepository).findAll(any(Specification.class));
    }
  }

  private Claim buildClaim() {
    return Claim.builder()
        .id(UUID.randomUUID())
        .status(ClaimStatus.VALID)
        .lineNumber(1)
        .matterTypeCode("MT")
        .createdByUserId("user-1")
        .build();
  }

  private Submission buildSubmission() {
    return Submission.builder()
        .id(UUID.randomUUID())
        .bulkSubmissionId(UUID.randomUUID())
        .officeAccountNumber("OFF_001")
        .submissionPeriod("JUL-2025")
        .areaOfLaw(AreaOfLaw.CRIME_LOWER)
        .status(SubmissionStatus.VALIDATION_SUCCEEDED)
        .createdByUserId("user-1")
        .providerUserId("provider-1")
        .createdOn(Instant.now())
        .updatedOn(Instant.now())
        .build();
  }
}
