package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimFields;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPost;

@ExtendWith(MockitoExtension.class)
class ClientMapperTest {

  @InjectMocks private ClientMapperImpl mapper = new ClientMapperImpl();

  @Spy private GlobalStringMapper globalStringMapper = new GlobalStringMapperImpl();

  @Test
  void toClient_null_returnsNull() {
    assertNull(mapper.toClient(null));
  }

  @Test
  void toClient_mapsAllFields() {
    final LocalDate dob1 = LocalDate.of(1990, 5, 20);
    final LocalDate dob2 = LocalDate.of(1992, 7, 15);

    final ClaimPost post =
        new ClaimPost()
            .clientForename("John")
            .clientSurname("Doe")
            .clientDateOfBirth(dob1)
            .uniqueClientNumber("UCN-123")
            .clientPostcode("AB1 2CD")
            .genderCode("M")
            .ethnicityCode("ETH1")
            .disabilityCode("DIS1")
            .isLegallyAided(true)
            .clientTypeCode("TYPE-A")
            .homeOfficeClientNumber("HO-999")
            .claReferenceNumber("CLA-111")
            .claExemptionCode("EX-22")
            .client2Forename("Jane")
            .client2Surname("Roe")
            .client2DateOfBirth(dob2)
            .client2Ucn("UCN-456")
            .client2Postcode("EF3 4GH")
            .client2GenderCode("F")
            .client2EthnicityCode("ETH2")
            .client2DisabilityCode("DIS2")
            .client2IsLegallyAided(false);

    final Client client = mapper.toClient(post);

    assertNotNull(client);
    assertEquals("John", client.getClientForename());
    assertEquals("Doe", client.getClientSurname());
    assertEquals(dob1, client.getClientDateOfBirth());
    assertEquals("UCN-123", client.getUniqueClientNumber());
    assertEquals("AB1 2CD", client.getClientPostcode());
    assertEquals("M", client.getGenderCode());
    assertEquals("ETH1", client.getEthnicityCode());
    assertEquals("DIS1", client.getDisabilityCode());
    assertTrue(client.getIsLegallyAided());
    assertEquals("TYPE-A", client.getClientTypeCode());
    assertEquals("HO-999", client.getHomeOfficeClientNumber());
    assertEquals("CLA-111", client.getClaReferenceNumber());
    assertEquals("EX-22", client.getClaExemptionCode());
    assertEquals("Jane", client.getClient2Forename());
    assertEquals("Roe", client.getClient2Surname());
    assertEquals(dob2, client.getClient2DateOfBirth());
    assertEquals("UCN-456", client.getClient2Ucn());
    assertEquals("EF3 4GH", client.getClient2Postcode());
    assertEquals("F", client.getClient2GenderCode());
    assertEquals("ETH2", client.getClient2EthnicityCode());
    assertEquals("DIS2", client.getClient2DisabilityCode());
    assertFalse(client.getClient2IsLegallyAided());
  }

  @Test
  void updateClaimFieldsFromClient_nullEntity_noChange() {
    final ClaimFields fields =
        ClaimFields.builder().clientForename("Keep").clientSurname("Same").build();

    mapper.updateClaimFieldsFromClient(null, fields);

    assertEquals("Keep", fields.getClientForename());
    assertEquals("Same", fields.getClientSurname());
  }

  @Test
  void updateClaimFieldsFromClient_updatesOnlyNonNullFields() {
    final LocalDate initialDob = LocalDate.of(1980, 1, 1);
    final ClaimFields fields =
        ClaimFields.builder()
            .clientForename("OldForename")
            .clientSurname("OldSurname")
            .clientDateOfBirth(initialDob)
            .uniqueClientNumber("OLD-UCN")
            .clientPostcode("OLD-PC")
            .genderCode("OLD-G")
            .ethnicityCode("OLD-E")
            .disabilityCode("OLD-D")
            .isLegallyAided(false)
            .clientTypeCode("OLD-TYPE")
            .homeOfficeClientNumber("OLD-HO")
            .claReferenceNumber("OLD-CLA")
            .claExemptionCode("OLD-EX")
            .client2Forename("Old2F")
            .client2Surname("Old2S")
            .client2DateOfBirth(LocalDate.of(1985, 2, 2))
            .client2Ucn("OLD-UCN2")
            .client2Postcode("OLD-PC2")
            .client2GenderCode("OLD-G2")
            .client2EthnicityCode("OLD-E2")
            .client2DisabilityCode("OLD-D2")
            .client2IsLegallyAided(true)
            .build();

    final LocalDate newDob1 = LocalDate.of(1999, 9, 9);
    final Client entity =
        Client.builder()
            .clientForename("NewForename")
            .clientSurname("NewSurname")
            .clientDateOfBirth(newDob1)
            .uniqueClientNumber(null)
            .clientPostcode("NEW-PC")
            .genderCode(null)
            .ethnicityCode("NEW-E")
            .disabilityCode(null)
            .isLegallyAided(true)
            .clientTypeCode("NEW-TYPE")
            .homeOfficeClientNumber(null)
            .claReferenceNumber("NEW-CLA")
            .claExemptionCode(null)
            .client2Forename("New2F")
            .client2Surname(null)
            .client2DateOfBirth(null)
            .client2Ucn("NEW-UCN2")
            .client2Postcode(null)
            .client2GenderCode("NEW-G2")
            .client2EthnicityCode(null)
            .client2DisabilityCode("NEW-D2")
            .client2IsLegallyAided(false)
            .build();

    mapper.updateClaimFieldsFromClient(entity, fields);

    assertEquals("NewForename", fields.getClientForename());
    assertEquals("NewSurname", fields.getClientSurname());
    assertEquals(newDob1, fields.getClientDateOfBirth());
    assertEquals("OLD-UCN", fields.getUniqueClientNumber());
    assertEquals("NEW-PC", fields.getClientPostcode());
    assertEquals("OLD-G", fields.getGenderCode());
    assertEquals("NEW-E", fields.getEthnicityCode());
    assertEquals("OLD-D", fields.getDisabilityCode());
    assertTrue(fields.getIsLegallyAided());
    assertEquals("NEW-TYPE", fields.getClientTypeCode());
    assertEquals("OLD-HO", fields.getHomeOfficeClientNumber());
    assertEquals("NEW-CLA", fields.getClaReferenceNumber());
    assertEquals("OLD-EX", fields.getClaExemptionCode());
    assertEquals("New2F", fields.getClient2Forename());
    assertEquals("Old2S", fields.getClient2Surname());
    assertEquals(LocalDate.of(1985, 2, 2), fields.getClient2DateOfBirth());
    assertEquals("NEW-UCN2", fields.getClient2Ucn());
    assertEquals("OLD-PC2", fields.getClient2Postcode());
    assertEquals("NEW-G2", fields.getClient2GenderCode());
    assertEquals("OLD-E2", fields.getClient2EthnicityCode());
    assertEquals("NEW-D2", fields.getClient2DisabilityCode());
    assertFalse(fields.getClient2IsLegallyAided());
  }
}
