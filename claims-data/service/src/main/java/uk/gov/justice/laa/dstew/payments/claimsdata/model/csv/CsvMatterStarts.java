package uk.gov.justice.laa.dstew.payments.claimsdata.model.csv;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.CategoryCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.MediationType;

/** Record holding submission matter starts details. */
@JsonNaming(PropertyNamingStrategies.UpperSnakeCaseStrategy.class)
public record CsvMatterStarts(
    String scheduleRef,
    String procurementArea,
    String accessPoint,
    CategoryCode categoryCode,
    String deliveryLocation,
    MediationType mediationType,
    String numberOfMatterStarts) {}
