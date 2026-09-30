package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimRetentionLookupMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimRetentionLookupResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.PageableUtils;

/** Service containing business logic for retention lookup endpoint. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClaimRetentionLookupService {

  private final ClaimRepository claimRepository;
  private final ClaimRetentionLookupMapper mapper;

  /**
   * Retrieves VALID claims for a given office code and unique file number (UFN) with pagination.
   * Only claims with VALID status are returned; other statuses are excluded.
   *
   * <p>Results are sorted by updated_on in descending order (most recent first) with a
   * deterministic secondary sort by claim ID to ensure stable pagination across requests.
   *
   * <p>This endpoint is used for data governance and retention compliance.
   *
   * @param officeCode the office account number from the submission
   * @param ufn the unique file number to search for
   * @param pageable pagination details (page index and size); unpaged requests fall back to page
   *     {@value PageableUtils#DEFAULT_PAGE_NUMBER} with size {@value
   *     PageableUtils#DEFAULT_PAGE_SIZE}
   * @return a paginated response containing VALID claims matching the composite key
   */
  @Transactional(readOnly = true)
  public ClaimRetentionLookupResultSet getClaimsRetentionLookup(
      String officeCode, String ufn, Pageable pageable) {
    Pageable effectivePageable = applyDefaultPagination(pageable);

    Page<Claim> claimsPage =
        claimRepository.findValidByOfficeAccountNumberAndUniqueFileNumber(
            officeCode, ufn, effectivePageable);

    return mapper.toClaimRetentionLookupResultSet(claimsPage);
  }

  /**
   * Guarantees a paged request. Spring only resolves a {@link PageRequest} when both {@code page}
   * and {@code size} query parameters are supplied; otherwise the configured fallback is {@link
   * Pageable#unpaged()}, whose {@code getPageNumber()} and {@code getPageSize()} throw.
   *
   * @param pageable the resolved pageable, possibly unpaged or null
   * @return a paged {@link Pageable}, preserving any requested sort
   */
  private Pageable applyDefaultPagination(Pageable pageable) {
    if (pageable == null || pageable.isUnpaged()) {
      return PageRequest.of(PageableUtils.DEFAULT_PAGE_NUMBER, PageableUtils.DEFAULT_PAGE_SIZE);
    }
    return pageable;
  }
}
