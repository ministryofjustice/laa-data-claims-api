package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimAmendmentBadRequestException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimAmendmentNotFoundException;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.ClaimAmendmentStateException;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AmendedField;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AmendmentStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimAmendmentPost;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimAmendmentRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;

/** Service for handling claim amendments. */
@Service
@RequiredArgsConstructor
public class ClaimAmendmentService {
  // --- Error message constants ---
  private static final String NO_AMENDMENT_FOUND_WITH_ID_ERROR = "No amendment found with id: %s";
  private static final String NO_CLAIM_FOUND_WITH_ID_ERROR = "No claim found with id: %s";
  private static final String AMENDMENT_DOES_NOT_BELONG_TO_CLAIM_ERROR =
      "Amendment does not belong to the specified claim";
  private static final String AMENDMENT_MUST_HAVE_UPDATED_BY_USER_ID_ERROR =
      "Amendment must have updatedByUserId set to action amendment";
  private static final String CREATED_BY_USER_ID_MUST_BE_PROVIDED_ERROR =
      "createdByUserId must be provided";
  private static final String UPDATED_BY_USER_ID_MUST_BE_PROVIDED_ERROR =
      "updatedByUserId must be provided";
  private static final String CANNOT_CREATE_AMENDMENT_READY_FOR_VALIDATION_ERROR =
      "Cannot create amendment: another amendment is already in READY_FOR_VALIDATION state for this claim";
  private static final String STATUS_CAN_ONLY_BE_CHANGED_IF_READY_FOR_VALIDATION_ERROR =
      "Status can only be changed if current status is READY_FOR_VALIDATION";
  private static final String FAILED_TO_SERIALIZE_AMENDED_FIELDS_ERROR =
      "Failed to serialize amendedFields: %s";
  private static final String FAILED_TO_DESERIALIZE_CHANGED_FIELDS_ERROR =
      "Failed to deserialize changedFields to AmendedField list: %s";
  private static final String AMENDED_FIELD_METHOD_MISSING_ERROR =
      "AmendedField missing getFieldName/getNewValue method or not accessible: %s";
  private static final String INVALID_AMENDED_FIELD_ERROR = "Invalid amended field: %s";
  private static final String FIELD_VALUE_TYPE_ERROR = "Field '%s' value '%s' is not of type %s";

  // --- Allowed field name constants ---
  private static final String FIELD_PROFIT_COSTS = "profitCosts";
  private static final String FIELD_DISBURSEMENTS = "disbursements";
  private static final String FIELD_DISBURSEMENTS_VAT = "disbursementsVAT";
  private static final String FIELD_COUNSELS_COSTS = "counselsCosts";
  private static final String FIELD_POLICE_STATION_CODE = "policeStationCode";

  private static final Map<String, Class<?>> ALLOWED_FIELDS =
      Map.of(
          FIELD_PROFIT_COSTS, BigDecimal.class,
          FIELD_DISBURSEMENTS, BigDecimal.class,
          FIELD_DISBURSEMENTS_VAT, BigDecimal.class,
          FIELD_COUNSELS_COSTS, BigDecimal.class,
          FIELD_POLICE_STATION_CODE, String.class);
  private final ClaimAmendmentRepository claimAmendmentRepository;
  private final ClaimRepository claimRepository;

  /**
   * Retrieves all amendments for a given claim.
   *
   * @param claimId the claim ID
   * @return list of ClaimAmendment
   */
  @Transactional(readOnly = true)
  public List<ClaimAmendment> getAmendmentsForClaim(UUID claimId) {
    return claimAmendmentRepository.findByClaimId(claimId);
  }

  /**
   * Updates the status of a claim amendment.
   *
   * @param claimId the claim ID
   * @param amendmentId the amendment ID
   * @param status the new status
   * @param updatedByUserId the user updating
   * @return optional ClaimAmendment if found and updated
   */
  @Transactional
  public Optional<ClaimAmendment> updateAmendmentStatus(
      UUID claimId, UUID amendmentId, AmendmentStatus status, String updatedByUserId) {
    validateUpdatedByUserId(updatedByUserId);
    Optional<ClaimAmendment> amendmentOpt = claimAmendmentRepository.findById(amendmentId);
    if (amendmentOpt.isPresent()) {
      ClaimAmendment amendment = amendmentOpt.get();
      if (!amendment.getClaimId().equals(claimId)) {
        throw new ClaimAmendmentBadRequestException(AMENDMENT_DOES_NOT_BELONG_TO_CLAIM_ERROR);
      }
      validateStatusIsReadyForValidation(amendment);
      amendment.setStatus(status.getValue());
      amendment.setUpdatedByUserId(updatedByUserId);
      amendment.setUpdatedOn(OffsetDateTime.now());
      claimAmendmentRepository.save(amendment);
      // If status is set to VALID, action the amendment
      if (AmendmentStatus.VALID.equals(status)) {
        actionAmendment(amendmentId);
      }
      return Optional.of(amendment);
    }
    throw new ClaimAmendmentNotFoundException(
        String.format(NO_AMENDMENT_FOUND_WITH_ID_ERROR, amendmentId));
  }

  /**
   * Creates a new amendment for a claim.
   *
   * @param claimId the claim ID
   * @param post the amendment post data
   * @return the created ClaimAmendment
   */
  @Transactional
  public ClaimAmendment createAmendment(UUID claimId, ClaimAmendmentPost post) {
    validateClaimExists(claimId);
    validateCreatedByUserId(post.getCreatedByUserId());
    validateNoReadyForValidationAmendment(claimId);
    List<AmendedField> amendedFields = post.getAmendedFields();
    validateAmendedFields(amendedFields);
    ClaimAmendment amendment = new ClaimAmendment();
    amendment.setClaimAmendmentId(UUID.randomUUID());
    amendment.setClaimId(claimId);
    amendment.setCreatedByUserId(post.getCreatedByUserId());
    amendment.setStatus(AmendmentStatus.READY_FOR_VALIDATION.getValue());
    try {
      amendment.setChangedFields(amendedFields);
    } catch (Exception e) {
      throw new ClaimAmendmentBadRequestException(
          String.format(FAILED_TO_SERIALIZE_AMENDED_FIELDS_ERROR, e.getMessage()));
    }
    return claimAmendmentRepository.save(amendment);
  }

  /**
   * Applies amendment action: if policeStationCode is set and different, update claim and audit
   * fields.
   *
   * @param amendmentId the amendment ID
   */
  public void actionAmendment(UUID amendmentId) {
    ClaimAmendment amendment =
        claimAmendmentRepository
            .findById(amendmentId)
            .orElseThrow(
                () ->
                    new ClaimAmendmentNotFoundException(
                        String.format(NO_AMENDMENT_FOUND_WITH_ID_ERROR, amendmentId)));
    // Require updatedByUserId to exist and not be blank
    String updatedByUserId = amendment.getUpdatedByUserId();
    if (updatedByUserId == null || updatedByUserId.isBlank()) {
      throw new ClaimAmendmentBadRequestException(AMENDMENT_MUST_HAVE_UPDATED_BY_USER_ID_ERROR);
    }
    Claim claim =
        claimRepository
            .findById(amendment.getClaimId())
            .orElseThrow(
                () ->
                    new ClaimAmendmentNotFoundException(
                        String.format(NO_CLAIM_FOUND_WITH_ID_ERROR, amendment.getClaimId())));
    String newPoliceStationCode = null;
    for (AmendedField field : amendment.getChangedFields()) {
      if (field != null && FIELD_POLICE_STATION_CODE.equals(field.getFieldName())) {
        newPoliceStationCode = field.getNewValue();
      }
    }
    if (newPoliceStationCode != null
        && !newPoliceStationCode.equals(claim.getPoliceStationCourtPrisonId())) {
      claim.setPoliceStationCourtPrisonId(newPoliceStationCode);
      claim.setUpdatedOn(java.time.Instant.now());
      claim.setUpdatedByUserId(updatedByUserId);
      claimRepository.save(claim);
    }
  }

  // --- Validation methods ---
  private void validateClaimExists(UUID claimId) {
    if (claimRepository.findById(claimId).isEmpty()) {
      throw new ClaimAmendmentNotFoundException(
          String.format(NO_CLAIM_FOUND_WITH_ID_ERROR, claimId));
    }
  }

  private void validateCreatedByUserId(String createdByUserId) {
    if (createdByUserId == null || createdByUserId.isBlank()) {
      throw new ClaimAmendmentBadRequestException(CREATED_BY_USER_ID_MUST_BE_PROVIDED_ERROR);
    }
  }

  private void validateNoReadyForValidationAmendment(UUID claimId) {
    List<ClaimAmendment> existing = claimAmendmentRepository.findByClaimId(claimId);
    for (ClaimAmendment ca : existing) {
      if (AmendmentStatus.READY_FOR_VALIDATION
          .getValue()
          .equals(ca.getStatus() != null ? ca.getStatus() : "")) {
        throw new ClaimAmendmentStateException(CANNOT_CREATE_AMENDMENT_READY_FOR_VALIDATION_ERROR);
      }
    }
  }

  private void validateUpdatedByUserId(String updatedByUserId) {
    if (updatedByUserId == null || updatedByUserId.isBlank()) {
      throw new ClaimAmendmentBadRequestException(UPDATED_BY_USER_ID_MUST_BE_PROVIDED_ERROR);
    }
  }

  private void validateStatusIsReadyForValidation(ClaimAmendment amendment) {
    if (!AmendmentStatus.READY_FOR_VALIDATION
        .getValue()
        .equals(amendment.getStatus() != null ? amendment.getStatus() : "")) {
      throw new ClaimAmendmentStateException(
          STATUS_CAN_ONLY_BE_CHANGED_IF_READY_FOR_VALIDATION_ERROR);
    }
  }

  /**
   * Validates that all amended fields are allowed. Throws an exception if any invalid field is
   * present. Allowed fields: profitCosts, disbursements, disbursementsVAT, counselsCosts,
   * policeStationCode
   */
  private void validateAmendedFields(List<AmendedField> amendedFields) {
    if (amendedFields == null) {
      return;
    }
    for (AmendedField field : amendedFields) {
      if (field == null) {
        continue;
      }
      String name;
      Object value;
      try {
        name = (String) field.getClass().getMethod("getFieldName").invoke(field);
        value = field.getClass().getMethod("getNewValue").invoke(field);
      } catch (Exception e) {
        throw new ClaimAmendmentBadRequestException(
            String.format(AMENDED_FIELD_METHOD_MISSING_ERROR, e.getMessage()));
      }
      Class<?> expectedType = ALLOWED_FIELDS.get(name);
      if (expectedType == null) {
        throw new ClaimAmendmentBadRequestException(
            String.format(INVALID_AMENDED_FIELD_ERROR, name));
      }
      if (value == null) {
        continue; // null is allowed, skip type validation
      }
      if (!isValueOfType(value, expectedType)) {
        throw new ClaimAmendmentBadRequestException(
            String.format(FIELD_VALUE_TYPE_ERROR, name, value, expectedType.getSimpleName()));
      }
    }
  }

  private boolean isValueOfType(Object value, Class<?> expectedType) {
    if (expectedType == BigDecimal.class) {
      if (value instanceof BigDecimal) {
        return true;
      }
      if (value instanceof Number) {
        return true;
      }
      if (value instanceof String) {
        try {
          new BigDecimal((String) value);
          return true;
        } catch (NumberFormatException e) {
          return false;
        }
      }
      return false;
    } else if (expectedType == String.class) {
      return value instanceof String;
    }
    return false;
  }

  /**
   * Converts a JSON string of changed fields to a list of AmendedField objects.
   *
   * @param changedFields the JSON string
   * @return list of AmendedField
   */
  public List<@Valid AmendedField> convertChangedFieldsToAmendedFields(String changedFields) {
    if (changedFields == null || changedFields.isEmpty()) {
      return List.of();
    }
    try {
      ObjectMapper mapper = new ObjectMapper();
      // AmendedField is a POJO, so we can deserialize as a list
      return List.of(mapper.readValue(changedFields, AmendedField[].class));
    } catch (Exception e) {
      throw new ClaimAmendmentBadRequestException(
          String.format(FAILED_TO_DESERIALIZE_CHANGED_FIELDS_ERROR, e.getMessage()));
    }
  }
}
