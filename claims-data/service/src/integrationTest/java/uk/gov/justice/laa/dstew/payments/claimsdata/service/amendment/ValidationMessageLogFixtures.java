package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import java.util.Locale;
import java.util.UUID;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ValidationMessageLogRepository;

/**
 * Shared seed fixtures for {@link ValidationMessageLog} rows used by the amendment warning
 * integration tests.
 *
 * <p>Centralises the {@code ValidationMessageLog} seed shape so schema tweaks (e.g. tighter column
 * constraints or new required columns) are reflected in one place, and so subtle rules such as the
 * {@code message_code} 20-char limit or the {@code supersededByVersion = 0} "current" sentinel are
 * not duplicated across suites.
 */
public final class ValidationMessageLogFixtures {

  /** Persisted source label for warnings written by the amendment FSP path. */
  public static final String FSP_SOURCE = "FSP";

  /** Persisted source label used to verify FSP-only supersession does not touch other sources. */
  public static final String PDA_SOURCE = "PDA";

  /** Upper bound of the {@code message_code} column in {@code validation_message_log}. */
  private static final int MESSAGE_CODE_MAX_LENGTH = 20;

  private ValidationMessageLogFixtures() {}

  /**
   * Builds a "current" FSP warning row (sentinel {@code supersededByVersion = 0}) stamped at the
   * supplied claim version.
   *
   * @param submissionId submission the warning belongs to
   * @param claimId claim the warning belongs to
   * @param claimVersion the claim version the warning is stamped against
   * @param displayMessage user-facing message; also used to derive {@code technicalMessage} and
   *     {@code messageCode}
   * @return a ready-to-persist warning entity
   */
  public static ValidationMessageLog currentFspWarning(
      UUID submissionId, UUID claimId, Long claimVersion, String displayMessage) {
    return warning(
        submissionId,
        claimId,
        claimVersion,
        ValidationMessageLogRepository.CURRENT_SUPERSEDED_BY_VERSION,
        FSP_SOURCE,
        displayMessage);
  }

  /**
   * Builds an already-superseded (historical) FSP warning row so tests can prove supersession does
   * not overwrite non-current rows.
   */
  public static ValidationMessageLog supersededFspWarning(
      UUID submissionId,
      UUID claimId,
      Long claimVersion,
      Long supersededByVersion,
      String displayMessage) {
    return warning(
        submissionId, claimId, claimVersion, supersededByVersion, FSP_SOURCE, displayMessage);
  }

  /**
   * Builds a "current" non-FSP warning row (e.g. sourced by the PDA path) used to prove FSP-only
   * supersession leaves other sources untouched.
   */
  public static ValidationMessageLog currentWarning(
      UUID submissionId, UUID claimId, Long claimVersion, String source, String displayMessage) {
    return warning(
        submissionId,
        claimId,
        claimVersion,
        ValidationMessageLogRepository.CURRENT_SUPERSEDED_BY_VERSION,
        source,
        displayMessage);
  }

  private static ValidationMessageLog warning(
      UUID submissionId,
      UUID claimId,
      Long claimVersion,
      Long supersededByVersion,
      String source,
      String displayMessage) {
    ValidationMessageLog log = new ValidationMessageLog();
    log.setId(UUID.randomUUID());
    log.setSubmissionId(submissionId);
    log.setClaimId(claimId);
    log.setType(ValidationMessageType.WARNING);
    log.setSource(source);
    log.setDisplayMessage(displayMessage);
    log.setTechnicalMessage(displayMessage);
    log.setMessageCode(toMessageCode(displayMessage));
    log.setVersion(claimVersion);
    log.setSupersededByVersion(supersededByVersion);
    return log;
  }

  private static String toMessageCode(String displayMessage) {
    String normalized = displayMessage.toUpperCase(Locale.ROOT).replace('-', '_');
    return normalized.length() <= MESSAGE_CODE_MAX_LENGTH
        ? normalized
        : normalized.substring(0, MESSAGE_CODE_MAX_LENGTH);
  }
}
