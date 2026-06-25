package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentReferenceData;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AmendmentReasonReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.RequestedByReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.AmendmentReferenceDataUnavailableException;
import uk.gov.justice.laa.dstew.payments.claimsdata.provider.AmendmentReferenceDataProvider;

/**
 * Amendment metadata validation step: validates the Requested By code and the Amendment Reason code
 * supplied with a claim amendment against the governed reference data, returning any issues found.
 *
 * <p>Required behaviour:
 *
 * <ul>
 *   <li>Requested By and Amendment Reason are mandatory and must be supplied as stable codes.
 *   <li>Requested By must exist and be active.
 *   <li>Amendment Reason must exist, be active, and be valid for the submitted Requested By code.
 *   <li>Validation errors are collected (not thrown) so the flow can continue to gather further
 *       messages.
 *   <li>If the governed reference data is unavailable, the step fails safely by returning a single
 *       fatal {@link
 *       ClaimAmendmentValidationCode#TECHNICAL_ERROR_AMENDMENT_METADATA_REFERENCE_DATA} error so
 *       that the flow stops and nothing is saved.
 * </ul>
 *
 * <p>The submitting user's Entra identifier is validated separately by {@link
 * AmendmentUserIdValidationStep}, as it is a pure structural check with no reference-data
 * dependency.
 *
 * <p>Each issue is returned as a {@link ClaimAmendmentValidationError} carrying the stable {@link
 * ClaimAmendmentValidationCode} that identifies the failure.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AmendmentReferenceValidationStep implements ClaimAmendmentValidationStep {

  private static final String REFERENCE_DATA_UNAVAILABLE_MESSAGE =
      "Required amendment metadata reference data was unavailable at submit time";

  private final AmendmentReferenceDataProvider amendmentReferenceDataProvider;

  /**
   * Validates the amendment metadata on the supplied state, returning any issues found.
   *
   * <p>If the governed reference data is unavailable, the step fails safely by returning a single
   * fatal {@link ClaimAmendmentValidationCode#TECHNICAL_ERROR_AMENDMENT_METADATA_REFERENCE_DATA}
   * error, so the flow stops and nothing is saved.
   *
   * @param claimAmendmentState the amendment in progress
   * @return the validation errors found by this step; an empty list means the step passed
   */
  @Override
  public List<ClaimAmendmentValidationError> validate(ClaimAmendmentState claimAmendmentState) {
    ReferenceData reference;
    try {
      reference = loadReferenceData();
    } catch (AmendmentReferenceDataUnavailableException ex) {
      // Reference data could not be read (e.g. a database failure); fail safely with a fatal error
      // rather than propagating, so the orchestrator stops the flow and nothing is saved.
      return List.of(
          ClaimAmendmentValidationError.of(
              ClaimAmendmentValidationCode.TECHNICAL_ERROR_AMENDMENT_METADATA_REFERENCE_DATA));
    }

    ClaimAmendmentPayload payload = claimAmendmentState.getRequestPayload();

    String requestedByCode = unwrap(payload.getAmendmentRequestedBy());
    String amendmentReasonCode = unwrap(payload.getAmendmentReasonCode());

    List<ClaimAmendmentValidationError> errors = new ArrayList<>();
    validateRequestedBy(requestedByCode, reference).ifPresent(errors::add);
    validateAmendmentReason(requestedByCode, amendmentReasonCode, reference).ifPresent(errors::add);
    return errors;
  }

  /**
   * Loads the governed reference data and indexes it for O(1) lookups. Signals an unavailable state
   * by throwing {@link AmendmentReferenceDataUnavailableException} if the data cannot be read or is
   * absent; {@link #validate} catches this and turns it into a fatal validation error so that
   * nothing is saved.
   */
  private ReferenceData loadReferenceData() {
    ClaimAmendmentReferenceData referenceData = amendmentReferenceDataProvider.getReferenceData();

    if (referenceData.isEmpty()) {
      log.error(
          "{} (requestedBy values: {}, reason values: {})",
          REFERENCE_DATA_UNAVAILABLE_MESSAGE,
          referenceData.requestedBy().size(),
          referenceData.reasons().size());
      throw new AmendmentReferenceDataUnavailableException();
    }

    return ReferenceData.index(referenceData.requestedBy(), referenceData.reasons());
  }

  private Optional<ClaimAmendmentValidationError> validateRequestedBy(
      String requestedByCode, ReferenceData reference) {

    if (!StringUtils.hasText(requestedByCode)) {
      return Optional.of(
          ClaimAmendmentValidationError.of(
              ClaimAmendmentValidationCode.INVALID_REQUESTED_BY_MISSING));
    }

    RequestedByReferenceEntity match = reference.requestedByByCode().get(requestedByCode);
    if (match != null) {
      if (Boolean.FALSE.equals(match.getIsActive())) {
        return Optional.of(
            ClaimAmendmentValidationError.of(
                ClaimAmendmentValidationCode.INVALID_REQUESTED_BY_INACTIVE, requestedByCode));
      }
      return Optional.empty();
    }

    if (reference.requestedByLabelsLower().contains(toLower(requestedByCode))) {
      return Optional.of(
          ClaimAmendmentValidationError.of(
              ClaimAmendmentValidationCode.INVALID_REQUESTED_BY_NOT_A_CODE));
    }
    return Optional.of(
        ClaimAmendmentValidationError.of(
            ClaimAmendmentValidationCode.INVALID_REQUESTED_BY_UNKNOWN, requestedByCode));
  }

  private Optional<ClaimAmendmentValidationError> validateAmendmentReason(
      String requestedByCode, String amendmentReasonCode, ReferenceData reference) {

    if (!StringUtils.hasText(amendmentReasonCode)) {
      return Optional.of(
          ClaimAmendmentValidationError.of(
              ClaimAmendmentValidationCode.INVALID_AMENDMENT_REASON_MISSING));
    }

    List<AmendmentReasonReferenceEntity> matchingCode =
        reference.reasonsByCode().get(amendmentReasonCode);
    if (matchingCode == null) {
      if (reference.reasonLabelsLower().contains(toLower(amendmentReasonCode))) {
        return Optional.of(
            ClaimAmendmentValidationError.of(
                ClaimAmendmentValidationCode.INVALID_AMENDMENT_REASON_NOT_A_CODE));
      }
      return Optional.of(
          ClaimAmendmentValidationError.of(
              ClaimAmendmentValidationCode.INVALID_AMENDMENT_REASON_UNKNOWN, amendmentReasonCode));
    }

    // The reason code exists. It can only be meaningfully scoped to a Requested By value that is
    // itself a recognised code; when Requested By is absent or not a code that error has already
    // been reported, so we avoid emitting a cascading "not valid for Requested By" message.
    if (!reference.requestedByByCode().containsKey(requestedByCode)) {
      return Optional.empty();
    }

    AmendmentReasonReferenceEntity scoped =
        matchingCode.stream()
            .filter(entity -> entity.getRequestedByCode().equals(requestedByCode))
            .findFirst()
            .orElse(null);

    if (scoped == null) {
      return Optional.of(
          ClaimAmendmentValidationError.of(
              ClaimAmendmentValidationCode.INVALID_AMENDMENT_REASON_FOR_REQUESTED_BY,
              amendmentReasonCode,
              requestedByCode));
    }

    if (Boolean.FALSE.equals(scoped.getIsActive())) {
      return Optional.of(
          ClaimAmendmentValidationError.of(
              ClaimAmendmentValidationCode.INVALID_AMENDMENT_REASON_INACTIVE, amendmentReasonCode));
    }
    return Optional.empty();
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
