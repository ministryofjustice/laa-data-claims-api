package uk.gov.justice.laa.dstew.payments.claimsdata.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Record holding details of the office submitting a claim.
 *
 * @param account the account number of the office.
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public record BulkSubmissionOffice(String account) {}
