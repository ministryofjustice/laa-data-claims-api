package uk.gov.justice.laa.dstew.payments.claimsdata.model.csv;

/**
 * Record holding details of the office submitting a claim.
 *
 * @param account the account number of the office.
 */
public record CsvOffice(String account) {}
