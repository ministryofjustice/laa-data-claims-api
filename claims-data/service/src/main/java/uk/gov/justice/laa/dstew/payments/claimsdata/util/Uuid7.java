package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import com.fasterxml.uuid.Generators;
import java.util.UUID;

/**
 * This is used to generate UUIDs version 7 based on timestamp values.
 */
public final class Uuid7 {

  public Uuid7() {
    throw new IllegalStateException("Cannot instantiate Uuid7 class");
  }

  public static UUID timeBasedUuid() {
    return Generators.timeBasedEpochGenerator().generate();
  }
}
