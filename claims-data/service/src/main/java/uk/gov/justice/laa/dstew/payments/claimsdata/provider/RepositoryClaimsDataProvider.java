package uk.gov.justice.laa.dstew.payments.claimsdata.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.provider.ClaimsDataProvider;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.ClaimsDataProviderConfig;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimResultSetMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.SubmissionMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionBase;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.specification.ClaimSpecification;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.specification.SubmissionSpecification;

/**
 * JPA-backed implementation of {@link ClaimsDataProvider}.
 *
 * <p>Used when the claims-validation-core library is embedded directly inside the Claims API
 * service, which owns the database. Queries are executed via Spring Data JPA repositories,
 * bypassing any HTTP transport layer.
 *
 * <p>This bean is only registered when no other {@link ClaimsDataProvider} bean is present in the
 * application context (see {@link ClaimsDataProviderConfig}).
 */
@RequiredArgsConstructor
@Slf4j
public class RepositoryClaimsDataProvider implements ClaimsDataProvider {

  private static final int DEFAULT_PAGE = 0;
  private static final int DEFAULT_SIZE = 20;

  private final ClaimRepository claimRepository;
  private final SubmissionRepository submissionRepository;
  private final ClaimResultSetMapper claimResultSetMapper;
  private final SubmissionMapper submissionMapper;

  /**
   * Fetch a paginated, filtered result-set of claims.
   *
   * <p>Every parameter is optional except {@code officeCode}. A {@code null} or blank value for any
   * optional parameter means the filter is not applied.
   *
   * @param officeCode mandatory office code
   * @param submissionId optional submission UUID string
   * @param submissionStatuses optional list of {@link SubmissionStatus} to filter by
   * @param feeCode optional fee code
   * @param uniqueFileNumber optional unique file number
   * @param uniqueClientNumber optional unique client number
   * @param uniqueCaseId optional unique case id
   * @param claimStatuses optional list of {@link ClaimStatus} to filter by
   * @param page 0-based page number (defaults to 0 if null)
   * @param size page size (defaults to 20 if null)
   * @param sort sort expression in the form {@code field,direction} (e.g. {@code "status,asc"});
   *     ignored if null/blank
   * @return never-null {@link ClaimResultSet}
   */
  @Override
  @Transactional(readOnly = true)
  public ClaimResultSet getClaims(
      String officeCode,
      String submissionId,
      List<SubmissionStatus> submissionStatuses,
      String feeCode,
      String uniqueFileNumber,
      String uniqueClientNumber,
      String uniqueCaseId,
      List<ClaimStatus> claimStatuses,
      Integer page,
      Integer size,
      String sort) {

    if (!StringUtils.hasText(officeCode)) {
      log.warn("getClaims called with blank officeCode — returning empty result set");
      return emptyClaimResultSet();
    }

    Specification<Claim> spec =
        ClaimSpecification.filterBy(
            officeCode,
            submissionId,
            submissionStatuses,
            feeCode,
            uniqueFileNumber,
            uniqueClientNumber,
            uniqueCaseId,
            claimStatuses,
            /* submissionPeriod */ null,
            /* caseReferenceNumber */ null);

    Pageable pageable = buildPageable(page, size, sort);
    Page<Claim> resultPage = claimRepository.findAll(spec, pageable);

    ClaimResultSet resultSet = claimResultSetMapper.toClaimResultSet(resultPage);
    if (resultSet == null) {
      return emptyClaimResultSet();
    }
    if (resultSet.getContent() == null) {
      resultSet.setContent(Collections.emptyList());
    }
    return resultSet;
  }

  /**
   * Fetch submissions filtered by office codes, and optionally by area-of-law and
   * submission-period.
   *
   * @param offices mandatory non-empty list of office account numbers
   * @param areaOfLaw optional area-of-law filter
   * @param submissionPeriod optional submission-period filter (e.g. {@code "JUL-2025"})
   * @return never-null, possibly-empty list of {@link SubmissionBase}
   */
  @Override
  @Transactional(readOnly = true)
  public List<SubmissionBase> getSubmissions(
      List<String> offices, AreaOfLaw areaOfLaw, String submissionPeriod) {

    if (offices == null || offices.isEmpty()) {
      log.warn("getSubmissions called with null/empty offices — returning empty list");
      return Collections.emptyList();
    }

    Specification<Submission> spec =
        SubmissionSpecification.filterByOfficeAccountNumberIn(offices)
            .and(SubmissionSpecification.areaOfLawEqual(areaOfLaw))
            .and(SubmissionSpecification.submissionPeriodEqual(submissionPeriod));

    List<Submission> submissions = submissionRepository.findAll(spec);

    return submissions.stream().map(submissionMapper::toSubmissionBase).toList();
  }

  private Pageable buildPageable(Integer page, Integer size, String sort) {
    int pageNumber = page != null ? page : DEFAULT_PAGE;
    int pageSize = size != null && size > 0 ? size : DEFAULT_SIZE;

    if (StringUtils.hasText(sort)) {
      Sort parsedSort = parseSort(sort);
      return PageRequest.of(pageNumber, pageSize, parsedSort);
    }

    return PageRequest.of(pageNumber, pageSize);
  }

  /**
   * Parses a sort expression of the form {@code field,direction} or just {@code field}. Multiple
   * comma-separated pairs are supported (e.g. {@code "status,asc,feeCode,desc"}).
   */
  private Sort parseSort(String sort) {
    String[] parts = sort.split(",");
    List<Sort.Order> orders = new ArrayList<>();

    for (int i = 0; i < parts.length; i += 2) {
      String property = parts[i].trim();
      if (property.isEmpty()) {
        continue;
      }
      Sort.Direction direction = Sort.Direction.ASC;
      if (i + 1 < parts.length) {
        String dir = parts[i + 1].trim();
        if ("desc".equalsIgnoreCase(dir)) {
          direction = Sort.Direction.DESC;
        }
      }
      orders.add(new Sort.Order(direction, property));
    }

    return orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);
  }

  private ClaimResultSet emptyClaimResultSet() {
    return new ClaimResultSet()
        .content(Collections.emptyList())
        .totalPages(0)
        .totalElements(0)
        .number(0)
        .size(DEFAULT_SIZE);
  }
}
