package uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection;

import java.util.UUID;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;

/** Projection carrying validation message fields together with related claim/client details. */
public interface ValidationMessageWithClaimDetailsProjection {

  UUID getId();

  UUID getSubmissionId();

  UUID getClaimId();

  ValidationMessageType getType();

  String getSource();

  String getDisplayMessage();

  // Claim fields
  String getUniqueFileNumber();

  // Client 1 fields
  String getClientForename();

  String getClientSurname();

  String getUniqueClientNumber();

  // Client 2 fields
  String getClient2Forename();

  String getClient2Surname();

  String getClient2Ucn();
}
