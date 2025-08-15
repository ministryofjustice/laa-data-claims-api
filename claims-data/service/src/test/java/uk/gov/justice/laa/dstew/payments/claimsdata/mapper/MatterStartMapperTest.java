package uk.gov.justice.laa.dstew.payments.claimsdata.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.MatterStart;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CreateMatterStartRequest;

class MatterStartMapperTest {

  private final MatterStartMapperImpl mapper = new MatterStartMapperImpl();

  @Test
  void toMatterStart_null_returnsNull() {
    assertNull(mapper.toMatterStart(null));
  }

  @Test
  void toMatterStart_mapsAllFields() {
    final CreateMatterStartRequest request = new CreateMatterStartRequest()
        .scheduleReference("SCH-001")
        .categoryCode("CAT-A")
        .procurementAreaCode("PA-10")
        .accessPointCode("AP-01")
        .deliveryLocation("DL-XYZ")
        .numberOfMatterStarts(7);

    final MatterStart result = mapper.toMatterStart(request);

    assertNotNull(result);
    assertEquals("SCH-001", result.getScheduleReference());
    assertEquals("CAT-A", result.getCategoryCode());
    assertEquals("PA-10", result.getProcurementAreaCode());
    assertEquals("AP-01", result.getAccessPointCode());
    assertEquals("DL-XYZ", result.getDeliveryLocation());
    assertEquals(7, result.getNumberOfMatterStarts());
  }
}
