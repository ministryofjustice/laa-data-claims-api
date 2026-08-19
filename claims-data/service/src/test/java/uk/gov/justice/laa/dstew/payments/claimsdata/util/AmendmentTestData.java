package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Shared synthetic test data for the claim-amendment unit and serialisation tests (builder, mapper,
 * payload and Jackson tests).
 *
 * <p>Centralises the "before-state" dataset, the submitted "amended" values and the serialised JSON
 * property names so those tests assert against a single source of truth, mirroring the existing
 * {@link ClaimsDataTestUtil} pattern.
 */
public final class AmendmentTestData {

  private AmendmentTestData() {}

  // Identity and context
  public static final UUID CLAIM_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  public static final UUID SUBMISSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  public static final long VERSION = 7L;
  public static final int LINE_NUMBER = 1;
  public static final String OFFICE_ACCOUNT_NUMBER = "OFF123";
  public static final String SUBMISSION_PERIOD = "APR-2026";
  public static final String CREATED_BY_USER_ID = "user";
  public static final String PROVIDER_USER_ID = "provider";

  // Before-state (stored) values
  public static final String SCHEDULE_REFERENCE = "SCH-1";
  public static final String CASE_REFERENCE_NUMBER = "CRN-1";
  public static final String UNIQUE_FILE_NUMBER = "UFN-1";
  public static final LocalDate CASE_START_DATE = LocalDate.of(2026, 1, 1);
  public static final LocalDate CASE_CONCLUDED_DATE = LocalDate.of(2026, 2, 1);
  public static final LocalDate REPRESENTATION_ORDER_DATE = LocalDate.of(2026, 1, 15);
  public static final String MATTER_TYPE_CODE = "MT-1";
  public static final String FEE_CODE = "FEE-1";
  public static final String CATEGORY_OF_LAW = "CAT-1";
  public static final String CLIENT_FORENAME = "Ada";
  public static final String CLIENT_SURNAME = "Lovelace";
  public static final LocalDate CLIENT_DATE_OF_BIRTH = LocalDate.of(1990, 5, 20);
  public static final String UNIQUE_CLIENT_NUMBER = "UCN-1";
  public static final String CASE_ID = "CASE-1";
  public static final String EXEMPTION_CRITERIA_SATISFIED = "Y";
  public static final BigDecimal NET_PROFIT_COSTS_AMOUNT = new BigDecimal("100.50");
  public static final int CMRH_ORAL_COUNT = 2;
  public static final BigDecimal ASSESSED_TOTAL_INCL_VAT = new BigDecimal("250.00");

  // Amended (submitted) values
  public static final String AMENDED_FEE_CODE = "FEE-2";
  public static final String AMENDED_CLIENT_FORENAME = "Grace";
  public static final String AMENDED_CLIENT_SURNAME = "Hopper";
  public static final LocalDate AMENDED_CLIENT_DATE_OF_BIRTH = LocalDate.of(1985, 3, 10);
  public static final String AMENDED_CASE_ID = "CASE-2";
  public static final String AMENDED_UNIQUE_CLIENT_NUMBER = "UCN-2";
  public static final String AMENDED_UNIQUE_FILE_NUMBER = "UFN-2";
  public static final String UPDATED_SCHEDULE_REFERENCE = "SCH-2";

  // Serialised JSON property names
  public static final String FIELD_SCHEDULE_REFERENCE = "scheduleReference";
  public static final String FIELD_UNIQUE_FILE_NUMBER = "uniqueFileNumber";
  public static final String FIELD_CASE_REFERENCE_NUMBER = "caseReferenceNumber";
  public static final String FIELD_HAS_ASSESSMENT = "hasAssessment";
  public static final String FIELD_CLAIM_ID = "claimId";
  public static final String FIELD_BEFORE_STATE = "beforeState";
  public static final String FIELD_REQUEST_PAYLOAD = "requestPayload";
  public static final String FIELD_POST_AMENDMENT_STATE = "postAmendmentState";
}
