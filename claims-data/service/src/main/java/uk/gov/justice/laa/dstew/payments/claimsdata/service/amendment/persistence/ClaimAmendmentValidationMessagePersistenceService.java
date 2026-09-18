package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.model.ValidationSeverity;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationMessageLogRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.ValidationMessageLogFactory;

/**
 * Persists current FSP warning messages for a successful amendment.
 *
 * <p>The persistence rule is supersession-based: when an amendment successfully invokes FSP, all
 * existing current FSP warnings for the claim are marked superseded and the new warning set is
 * stored against the current claim version. If the FSP result contains no warnings, the claim ends
 * up with no current FSP warnings.
 */
@Service
@RequiredArgsConstructor
public class ClaimAmendmentValidationMessagePersistenceService {

  static final String FSP_SOURCE = "FSP";

  private final ValidationMessageLogRepository validationMessageLogRepository;
  private final ValidationMessageLogFactory validationMessageLogFactory;

  /**
   * Supersedes current FSP warnings and writes the warning set returned by the latest successful
   * repricing for this claim.
   *
   * @param claim managed claim already merged and versioned in the commit transaction
   * @param state amendment state carrying the latest external-validation warning issues
   */
  @Transactional
  public void persistCurrentWarnings(Claim claim, ClaimAmendmentState state) {
    if (claim == null || state == null || state.getFspResponseContext() == null) {
      return;
    }

    Long claimVersion = claim.getVersion();
    if (claimVersion == null || claim.getSubmission() == null) {
      return;
    }

    validationMessageLogRepository.supersedeCurrentByClaimIdAndSource(
        claim.getId(),
        FSP_SOURCE,
        ValidationMessageType.WARNING,
        claimVersion,
        ValidationMessageLogRepository.CURRENT_SUPERSEDED_BY_VERSION);

    List<ValidationMessageLog> warnings =
        state.getFspWarnings().stream()
            .filter(issue -> issue.getSeverity() == ValidationSeverity.WARNING)
            .map(
                issue ->
                    validationMessageLogFactory.createForClaimIssue(
                        issue,
                        claim,
                        ValidationMessageType.WARNING,
                        FSP_SOURCE,
                        claimVersion,
                        ValidationMessageLogRepository.CURRENT_SUPERSEDED_BY_VERSION))
            .toList();

    if (!warnings.isEmpty()) {
      validationMessageLogRepository.saveAll(warnings);
    }
  }
}
