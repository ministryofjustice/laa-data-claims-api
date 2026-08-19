package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;

/**
 * Applies the amended provider-entered {@code client}-table values from the post-amendment state
 * onto the managed {@link Client} entity.
 *
 * <p>Every amendable client column is copied from the post-amendment snapshot, which already
 * encodes the sparse-payload semantics (omitted fields retain their stored value; an explicit
 * {@code null} clears the field). Identity and audit columns are deliberately left untouched, and
 * this component never issues its own save.
 */
@Component
public class AmendmentClientUpdater {

  /**
   * Copies the amendable {@code client}-table fields from the post-amendment state onto the managed
   * client entity.
   *
   * @param client the managed client entity to mutate (not saved here)
   * @param postAmendmentState the proposed post-amendment values
   */
  public void applyAmendedFields(Client client, ClaimStateSnapshot postAmendmentState) {
    client.setClientForename(postAmendmentState.getClientForename());
    client.setClientSurname(postAmendmentState.getClientSurname());
    client.setClientDateOfBirth(postAmendmentState.getClientDateOfBirth());
    client.setUniqueClientNumber(postAmendmentState.getUniqueClientNumber());
    client.setClientPostcode(postAmendmentState.getClientPostcode());
    client.setGenderCode(postAmendmentState.getGenderCode());
    client.setEthnicityCode(postAmendmentState.getEthnicityCode());
    client.setDisabilityCode(postAmendmentState.getDisabilityCode());
    client.setIsLegallyAided(postAmendmentState.getIsLegallyAided());
    client.setClientTypeCode(postAmendmentState.getClientTypeCode());
    client.setHomeOfficeClientNumber(postAmendmentState.getHomeOfficeClientNumber());
    client.setClaReferenceNumber(postAmendmentState.getClaReferenceNumber());
    client.setClaExemptionCode(postAmendmentState.getClaExemptionCode());
    client.setClient2Forename(postAmendmentState.getClient2Forename());
    client.setClient2Surname(postAmendmentState.getClient2Surname());
    client.setClient2DateOfBirth(postAmendmentState.getClient2DateOfBirth());
    client.setClient2Ucn(postAmendmentState.getClient2Ucn());
    client.setClient2Postcode(postAmendmentState.getClient2Postcode());
    client.setClient2GenderCode(postAmendmentState.getClient2GenderCode());
    client.setClient2EthnicityCode(postAmendmentState.getClient2EthnicityCode());
    client.setClient2DisabilityCode(postAmendmentState.getClient2DisabilityCode());
    client.setClient2IsLegallyAided(postAmendmentState.getClient2IsLegallyAided());
  }
}
