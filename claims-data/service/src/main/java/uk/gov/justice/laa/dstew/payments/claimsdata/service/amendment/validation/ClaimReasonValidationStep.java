package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentReferenceData;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AmendmentReasonReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.RequestedByReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.AmendmentReferenceDataUnavailableException;
import uk.gov.justice.laa.dstew.payments.claimsdata.provider.AmendmentReferenceDataProvider;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.UUID7;

/**
 * Amendment metadata validation step: validates the Requested By code, the Amendment Reason code
 * and the submitting user's Entra UUID supplied with a claim amendment, collecting any issues onto
 * the {@link ClaimAmendmentState}.
 *
 * <p>Required behaviour:
 *
 * <ul>
 *   <li>Requested By and Amendment Reason are mandatory and must be supplied as stable codes.
 *   <li>Requested By must exist and be active.
 *   <li>Amendment Reason must exist, be active, and be valid for the submitted Requested By code.
 *   <li>The Entra user id must be structurally valid as a UUID; the Claims API does not check
 *       whether the user currently exists.
 *   <li>Validation errors are collected (not thrown) so the flow can continue to gather further
 *       messages.
 *   <li>If the governed reference data is unavailable, the step fails safely with a controlled
 *       {@link AmendmentReferenceDataUnavailableException} so that nothing is saved.
 * </ul>
 *
 * <p>Each collected error is recorded as a {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType#ERROR} validation issue
 * whose {@code source} carries the stable error code.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClaimReasonValidationStep {

  private final AmendmentReferenceDataProvider amendmentReferenceDataProvider;

  /**
   * Validates the amendment metadata on the supplied state, adding any issues to it.
   *
   * @param claimAmendmentState the amendment in progress
   * @return {@code true} if this step added no new validation issues; {@code false} otherwise
   * @throws AmendmentReferenceDataUnavailableException if the governed reference data cannot be
   *     read or is absent, in which case nothing should be saved
   */
  @Transactional(readOnly = true)
  public boolean validate(ClaimAmendmentState claimAmendmentState) {
    ReferenceData reference = loadReferenceData();
    ClaimAmendmentPayload payload = claimAmendmentState.getRequestPayload();

    String requestedByCode = unwrap(payload.getAmendmentRequestedBy());
    String amendmentReasonCode = unwrap(payload.getAmendmentReasonCode());
    String userId = unwrap(payload.getAmendmentUserId());

    boolean requestedByValid = validateRequestedBy(requestedByCode, reference, claimAmendmentState);
    boolean reasonValid =
        validateAmendmentReason(
            requestedByCode, amendmentReasonCode, reference, claimAmendmentState);
    boolean userValid = validateUserId(userId, claimAmendmentState);
    return requestedByValid && reasonValid && userValid;
  }

  /**
   * Loads the governed reference data and indexes it for O(1) lookups. Fails safely with {@link
   * AmendmentReferenceDataUnavailableException} if the data cannot be read or is absent, so that
   * nothing is saved.
   */
  private ReferenceData loadReferenceData() {
    ClaimAmendmentReferenceData referenceData = amendmentReferenceDataProvider.getReferenceData();

    if (referenceData.isEmpty()) {
      log.error(
          "{} (requestedBy values: {}, reason values: {})",
          AmendmentMetadataValidationError.TECHNICAL_ERROR_AMENDMENT_METADATA_REFERENCE_DATA
              .getTechnicalMessage(),
          referenceData.requestedBy().size(),
          referenceData.reasons().size());
      throw new AmendmentReferenceDataUnavailableException();
    }

    return ReferenceData.index(referenceData.requestedBy(), referenceData.reasons());
  }

  private boolean validateRequestedBy(
      String requestedByCode, ReferenceData reference, ClaimAmendmentState state) {

    if (!StringUtils.hasText(requestedByCode)) {
      return addIssue(state, AmendmentMetadataValidationError.INVALID_REQUESTED_BY_MISSING);
    }

    RequestedByReferenceEntity match = reference.requestedByByCode().get(requestedByCode);
    if (match != null) {
      if (Boolean.FALSE.equals(match.getIsActive())) {
        return addIssue(
            state, AmendmentMetadataValidationError.INVALID_REQUESTED_BY_INACTIVE, requestedByCode);
      }
      return true;
    }

    if (reference.requestedByLabelsLower().contains(toLower(requestedByCode))) {
      return addIssue(state, AmendmentMetadataValidationError.INVALID_REQUESTED_BY_NOT_A_CODE);
    }
    return addIssue(
        state, AmendmentMetadataValidationError.INVALID_REQUESTED_BY_UNKNOWN, requestedByCode);
  }

  private boolean validateAmendmentReason(
      String requestedByCode,
      String amendmentReasonCode,
      ReferenceData reference,
      ClaimAmendmentState state) {

    if (!StringUtils.hasText(amendmentReasonCode)) {
      return addIssue(state, AmendmentMetadataValidationError.INVALID_AMENDMENT_REASON_MISSING);
    }

    List<AmendmentReasonReferenceEntity> matchingCode =
        reference.reasonsByCode().get(amendmentReasonCode);
    if (matchingCode == null) {
      if (reference.reasonLabelsLower().contains(toLower(amendmentReasonCode))) {
        return addIssue(
            state, AmendmentMetadataValidationError.INVALID_AMENDMENT_REASON_NOT_A_CODE);
      }
      return addIssue(
          state,
          AmendmentMetadataValidationError.INVALID_AMENDMENT_REASON_UNKNOWN,
          amendmentReasonCode);
    }

    // The reason code exists. It can only be meaningfully scoped to a Requested By value that is
    // itself a recognised code; when Requested By is absent or not a code that error has already
    // been reported, so we avoid emitting a cascading "not valid for Requested By" message.
    if (!reference.requestedByByCode().containsKey(requestedByCode)) {
      return true;
    }

    AmendmentReasonReferenceEntity scoped =
        matchingCode.stream()
            .filter(entity -> entity.getRequestedByCode().equals(requestedByCode))
            .findFirst()
            .orElse(null);

    if (scoped == null) {
      return addIssue(
          state,
          AmendmentMetadataValidationError.INVALID_AMENDMENT_REASON_FOR_REQUESTED_BY,
          amendmentReasonCode,
          requestedByCode);
    }

    if (Boolean.FALSE.equals(scoped.getIsActive())) {
      return addIssue(
          state,
          AmendmentMetadataValidationError.INVALID_AMENDMENT_REASON_INACTIVE,
          amendmentReasonCode);
    }
    return true;
  }

  private boolean validateUserId(String userId, ClaimAmendmentState state) {
    if (!UUID7.isValidUuid(userId)) {
      return addIssue(state, AmendmentMetadataValidationError.INVALID_USER_IDENTIFIER_FORMAT);
    }
    return true;
  }

  /** Records the error as a validation issue and returns {@code false} (i.e. not valid). */
  private boolean addIssue(
      ClaimAmendmentState state, AmendmentMetadataValidationError error, Object... args) {
    state.addValidationIssue(error.toValidationIssue(args));
    return false;
  }

  private String unwrap(JsonNullable<String> value) {
    return value != null && value.isPresent() ? value.get() : null;
  }

  private static String toLower(String value) {
    return value == null ? null : value.toLowerCase(Locale.ROOT);
  }

  /**
   * Reference data pre-indexed for O(1) lookups, built once per validation from the governed
   * reference lists.
   *
   * @param requestedByByCode Requested By values keyed by their code
   * @param requestedByLabelsLower lower-cased Requested By display labels (for not-a-code
   *     detection)
   * @param reasonsByCode Amendment Reason values grouped by their code (may map to several
   *     Requested By values)
   * @param reasonLabelsLower lower-cased Amendment Reason display labels (for not-a-code detection)
   */
  private record ReferenceData(
      Map<String, RequestedByReferenceEntity> requestedByByCode,
      Set<String> requestedByLabelsLower,
      Map<String, List<AmendmentReasonReferenceEntity>> reasonsByCode,
      Set<String> reasonLabelsLower) {

    private static ReferenceData index(
        List<RequestedByReferenceEntity> requestedBy,
        List<AmendmentReasonReferenceEntity> reasons) {
      return new ReferenceData(
          requestedBy.stream()
              .collect(
                  Collectors.toMap(
                      RequestedByReferenceEntity::getCode,
                      entity -> entity,
                      (first, ignored) -> first)),
          requestedBy.stream()
              .map(RequestedByReferenceEntity::getDisplayLabel)
              .filter(Objects::nonNull)
              .map(label -> label.toLowerCase(Locale.ROOT))
              .collect(Collectors.toSet()),
          reasons.stream().collect(Collectors.groupingBy(AmendmentReasonReferenceEntity::getCode)),
          reasons.stream()
              .map(AmendmentReasonReferenceEntity::getDisplayLabel)
              .filter(Objects::nonNull)
              .map(label -> label.toLowerCase(Locale.ROOT))
              .collect(Collectors.toSet()));
    }
  }
}
