package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;

/** Mapper for converting between {@link Instant} and {@link OffsetDateTime} using UTC. */
@Mapper(componentModel = "spring")
public interface GlobalDateTimeMapper {

  /**
   * Maps an {@link Instant} to an {@link OffsetDateTime} using UTC offset.
   *
   * @param value the instant to convert
   * @return the converted offset date-time or null if input is null
   */
  default OffsetDateTime map(Instant value) {
    return value != null ? value.atOffset(ZoneOffset.UTC) : null;
  }

  /**
   * Maps an {@link OffsetDateTime} to an {@link Instant}.
   *
   * @param value the offset date-time to convert
   * @return the converted instant or null if input is null
   */
  default Instant map(OffsetDateTime value) {
    return value != null ? value.toInstant() : null;
  }
}
