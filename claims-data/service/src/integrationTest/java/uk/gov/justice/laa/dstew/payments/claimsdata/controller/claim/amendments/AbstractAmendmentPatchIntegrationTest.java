package uk.gov.justice.laa.dstew.payments.claimsdata.controller.claim.amendments;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.API_URI_PREFIX;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_HEADER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.AUTHORIZATION_TOKEN;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.ClaimsApiProperties;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.helper.MockServerIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

/**
 * Shared scaffolding for amendment PATCH-endpoint integration tests that exercise the external PDA
 * (Provider Details API) path via MockServer.
 *
 * <p>Extends {@link MockServerIntegrationTest} (which owns the external-HTTP stub/verify helpers)
 * and adds the pieces common to the PDA call-layer and PDA outcome-mapping suites: enabling the
 * amendments feature flag, seeding claims data, stubbing the Fee Scheme Platform calls, creating a
 * uniquely-officed submission and an amendable claim, and driving the PATCH endpoint.
 *
 * <p>It is intentionally a separate base rather than folded into {@link MockServerIntegrationTest},
 * because that class's other (non-PATCH) subclasses perform their own seeding in
 * {@code @BeforeEach}; a shared seeding hook there would double-seed and clash. Keeping it here
 * scopes the amendment-flag toggle and seeding to just the PATCH-driven PDA suites.
 */
abstract class AbstractAmendmentPatchIntegrationTest extends MockServerIntegrationTest {

  /** Governed amendment-metadata reference codes seeded by Flyway migration V41. */
  protected static final String REQUESTED_BY_PROVIDER = "PROVIDER";

  protected static final String REASON_PROVIDER_ERROR = "PROVIDER_ERROR";
  protected static final UUID VALID_USER_UUID =
      UUID.fromString("0190b6a0-9b7e-7c8a-9e2d-2f3a4b5c6d7e");

  /** The generic technical-error code claims-validation-core emits on any PDA call failure. */
  protected static final String PDA_TECHNICAL_ERROR_CODE = "TECHNICAL_ERROR_PROVIDER_DETAILS_API";

  protected static final String PATCH_A_CLAIM_ENDPOINT =
      API_URI_PREFIX + "/submissions/{submissionId}/claims/{claimId}";

  private static final String CREATED_BY = "amendment-pda-integration-test";

  // Static so office codes are unique across every subclass in the JVM, keeping the per-JVM PDA
  // cache (keyed on office) isolated between tests and classes.
  private static final AtomicInteger OFFICE_SEQ = new AtomicInteger();

  // Serialises the patch omitting null fields, so only the keys we explicitly set are sent (an
  // explicit null would be read by the service as "clear this field").
  protected static final ObjectMapper PATCH_MAPPER = nonNullMapper();

  private static ObjectMapper nonNullMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    return mapper;
  }

  @Autowired protected ClaimsApiProperties claimsApiProperties;

  private boolean originalAmendmentFlag;

  @BeforeEach
  void enableAmendmentsSeedAndStubFeeScheme() throws IOException {
    originalAmendmentFlag = claimsApiProperties.getAmendments().isEnabled();
    claimsApiProperties.getAmendments().setEnabled("true");
    seedClaimsData();
    // The non-PDA external calls succeed with the default fixtures; each test stubs the PDA
    // /schedules call itself to drive the behaviour under test.
    stubFeeSchemeEndpoints();
  }

  @AfterEach
  void restoreAmendmentsFlag() {
    claimsApiProperties.getAmendments().setEnabled(String.valueOf(originalAmendmentFlag));
  }

  /**
   * Creates a submission under the seeded bulk submission with a unique office code, giving each
   * test an isolated PDA cache key space.
   *
   * @return the new submission id
   */
  protected UUID createSubmissionWithUniqueOffice() {
    UUID id = Uuid7.timeBasedUuid();
    Submission submission =
        Submission.builder()
            .id(id)
            .bulkSubmissionId(bulkSubmission.getId())
            .officeAccountNumber("PDAOF" + OFFICE_SEQ.incrementAndGet())
            .submissionPeriod("JAN-2025")
            .areaOfLaw(AreaOfLaw.LEGAL_HELP)
            .status(SubmissionStatus.CREATED)
            .createdByUserId(CREATED_BY)
            .providerUserId(bulkSubmission.getCreatedByUserId())
            .numberOfClaims(0)
            .createdOn(CREATED_ON)
            .build();
    submissionRepository.saveAndFlush(submission);
    return id;
  }

  /**
   * Creates a VALID (amendable) claim under the given submission, applying the supplied state so a
   * test can control the effective-date-determining fields.
   *
   * @param submissionId the owning submission
   * @param state customises the claim builder (e.g. fee code and dates)
   * @return the new claim
   */
  protected Claim createAmendableClaim(UUID submissionId, Consumer<Claim.ClaimBuilder> state) {
    Claim.ClaimBuilder builder =
        Claim.builder()
            .id(Uuid7.timeBasedUuid())
            .submission(submissionRepository.getReferenceById(submissionId))
            .status(ClaimStatus.VALID)
            .lineNumber(1)
            .caseReferenceNumber("PDA-CRN")
            .matterTypeCode("MTC")
            .createdByUserId(CREATED_BY)
            .createdOn(CREATED_ON);
    state.accept(builder);
    return claimRepository.saveAndFlush(builder.build());
  }

  /**
   * A patch carrying valid amendment metadata (requested-by, reason and user id) plus the current
   * claim version so it passes the early version gate. Claims created by {@link
   * #createAmendableClaim} are freshly inserted at version {@code 0}. Tests add the field change
   * under test on top.
   *
   * @return a metadata-only claim patch
   */
  protected ClaimPatch metadataPatch() {
    ClaimPatch patch = new ClaimPatch();
    patch.setVersion(0L);
    patch.setAmendmentRequestedBy(REQUESTED_BY_PROVIDER);
    patch.setAmendmentReasonCode(REASON_PROVIDER_ERROR);
    patch.setAmendmentUserId(VALID_USER_UUID);
    return patch;
  }

  /**
   * Performs the amendment PATCH for the given submission/claim with the supplied patch body.
   *
   * @return the completed {@link MvcResult}
   */
  protected MvcResult performPatch(UUID submissionId, UUID claimId, ClaimPatch patch)
      throws Exception {
    return mockMvc
        .perform(
            patch(PATCH_A_CLAIM_ENDPOINT, submissionId, claimId)
                .header(AUTHORIZATION_HEADER, AUTHORIZATION_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(PATCH_MAPPER.writeValueAsString(patch)))
        .andReturn();
  }
}
