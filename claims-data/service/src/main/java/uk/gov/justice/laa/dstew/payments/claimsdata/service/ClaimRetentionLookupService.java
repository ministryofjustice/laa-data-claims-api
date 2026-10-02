package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimRetentionLookupMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimRetentionLookupResultSet;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.PageableUtils;
import uk.gov.justice.laa.dstew.payments.claimsdata.validator.ClaimRetentionLookupValidator;

/** Service containing business logic for retention lookup endpoint. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClaimRetentionLookupService {

  private final ClaimRepository claimRepository;
  private final ClaimRetentionLookupMapper mapper;
  private final ClaimRetentionLookupValidator validator;

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
    validator.validate(officeCode, ufn);
    Pageable effectivePageable =
        PageableUtils.withDefaultPaginationAndSort(pageable, Sort.unsorted());

    Page<Claim> claimsPage =
        claimRepository.findValidByOfficeAccountNumberAndUniqueFileNumber(
            officeCode, ufn, effectivePageable);

    return mapper.toClaimRetentionLookupResultSet(claimsPage);
  }
}
