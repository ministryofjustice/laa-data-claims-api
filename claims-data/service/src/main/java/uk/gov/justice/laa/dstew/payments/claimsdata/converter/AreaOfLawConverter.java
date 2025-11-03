package uk.gov.justice.laa.dstew.payments.claimsdata.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;

/**
 * This is used to convert the areaOfLaw string into the corresponding {@link AreaOfLaw} enum value.
 */
@Component
public class AreaOfLawConverter implements Converter<String, AreaOfLaw> {

  @Override
  public AreaOfLaw convert(String source) {
    if (source == null) {
      return null;
    }
    String normalized = source.trim().replace(' ', '_');
    return AreaOfLaw.valueOf(normalized);
  }
}
