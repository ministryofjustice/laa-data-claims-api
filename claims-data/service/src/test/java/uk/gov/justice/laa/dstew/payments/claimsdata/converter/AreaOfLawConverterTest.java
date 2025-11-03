package uk.gov.justice.laa.dstew.payments.claimsdata.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;

class AreaOfLawConverterTest {

  private final AreaOfLawConverter converter = new AreaOfLawConverter();

  @ParameterizedTest
  @MethodSource("areaOfLawTypeProvider")
  void convert(String areaOfLaw, AreaOfLaw expected) {
    assertThat(converter.convert(areaOfLaw)).isEqualTo(expected);
  }

  private static Stream<Arguments> areaOfLawTypeProvider() {
    return Stream.of(
        Arguments.of("CRIME LOWER", AreaOfLaw.CRIME_LOWER),
        Arguments.of("LEGAL HELP", AreaOfLaw.LEGAL_HELP),
        Arguments.of("MEDIATION", AreaOfLaw.MEDIATION),
        Arguments.of(null, null));
  }
}
