package uk.gov.justice.laa.dstew.payments.claimsdata.entity.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MediationType;

/** Persists {@link MediationType} enums using their display value instead of the enum name. */
@Converter(autoApply = true)
public class MediationTypeConverter implements AttributeConverter<MediationType, String> {

  /**
   * Convert the MediationType attribute to its database column representation.
   *
   * @param attribute the MediationType attribute
   * @return the database column representation
   */
  @Override
  public String convertToDatabaseColumn(MediationType attribute) {
    return attribute != null ? attribute.getValue() : null;
  }

  /**
   * Convert the database column representation to a MediationType attribute.
   *
   * @param dbData the database column representation
   * @return the MediationType attribute
   */
  @Override
  public MediationType convertToEntityAttribute(String dbData) {
    return dbData != null ? MediationType.fromValue(dbData) : null;
  }
}
