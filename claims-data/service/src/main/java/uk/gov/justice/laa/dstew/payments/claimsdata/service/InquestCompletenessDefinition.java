package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.InquestDetail;

/** The single parameterised policy definition used to evaluate inquest-data completeness. */
@Component
public class InquestCompletenessDefinition {
  /** Fields whose mandatory state can be selected by policy configuration. */
  public enum Field {
    DECEASED_FORENAME,
    DECEASED_SURNAME,
    DECEASED_DATE_OF_BIRTH,
    DECEASED_DATE_OF_DEATH,
    CORONERS_INQUEST_REFERENCE,
    INTERESTED_GOVERNMENT_DEPARTMENT,
    INTERESTED_PUBLIC_AUTHORITY
  }

  private final Set<Field> mandatoryFields;

  /** Creates a definition from a comma-separated list of mandatory field names. */
  public InquestCompletenessDefinition(
      @Value("${inquest.mandatory-fields}") String configuredFields) {
    mandatoryFields =
        configuredFields.isBlank()
            ? EnumSet.noneOf(Field.class)
            : Arrays.stream(configuredFields.split(","))
                .map(String::trim)
                .map(Field::valueOf)
                .collect(() -> EnumSet.noneOf(Field.class), Set::add, Set::addAll);
  }

  /** Evaluates stored scalar and repeating values against this definition. */
  public boolean isComplete(
      InquestDetail detail, int interestedDepartmentCount, int interestedAuthorityCount) {
    return detail != null
        && (!mandatoryFields.contains(Field.DECEASED_FORENAME)
            || StringUtils.hasText(detail.getDeceasedForename()))
        && (!mandatoryFields.contains(Field.DECEASED_SURNAME)
            || StringUtils.hasText(detail.getDeceasedSurname()))
        && (!mandatoryFields.contains(Field.DECEASED_DATE_OF_BIRTH)
            || detail.getDeceasedDateOfBirth() != null)
        && (!mandatoryFields.contains(Field.DECEASED_DATE_OF_DEATH)
            || detail.getDeceasedDateOfDeath() != null)
        && (!mandatoryFields.contains(Field.CORONERS_INQUEST_REFERENCE)
            || StringUtils.hasText(detail.getCoronersInquestReference()))
        && (!mandatoryFields.contains(Field.INTERESTED_GOVERNMENT_DEPARTMENT)
            || interestedDepartmentCount > 0)
        && (!mandatoryFields.contains(Field.INTERESTED_PUBLIC_AUTHORITY)
            || interestedAuthorityCount > 0);
  }
}
