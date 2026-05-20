package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AmendmentReasonReference;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.RequestedByReference;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

class ClaimAmendmentStorageIntegrationTest extends AbstractIntegrationTest {

  private RequestedByReference providerRef;
  private RequestedByReference auditorRef;
  private AmendmentReasonReference complianceReason;
  private AmendmentReasonReference typingErrorReason;

  @BeforeEach
  void setUp() {

    // 1. Establish the entire existing baseline (BulkSubmission, Submissions, Claims, Clients,
    // etc.)
    seedClaimsData();

    // 2. Clear any lingering reference data from previous test runs to prevent PK conflicts
    // (Note: Since clearIntegrationData() doesn't know about the new tables yet, handle it here or
    // update the base)
    amendmentReasonRepository.deleteAll();
    requestedByRepository.deleteAll();

    // 3. Seed the reference data layer matching the exact rules from your V39 script
    OffsetDateTime now = OffsetDateTime.now();
    providerRef =
        requestedByRepository.saveAndFlush(
            new RequestedByReference(
                UUID.randomUUID(), "PROVIDER", "Provider", true, 10, now, now));
    auditorRef =
        requestedByRepository.saveAndFlush(
            new RequestedByReference(UUID.randomUUID(), "AUDITOR", "Auditor", true, 20, now, now));

    typingErrorReason =
        amendmentReasonRepository.saveAndFlush(
            new AmendmentReasonReference(
                UUID.randomUUID(),
                providerRef,
                "TYPING_ERROR",
                "Typing Error",
                true,
                10,
                now,
                now));
    complianceReason =
        amendmentReasonRepository.saveAndFlush(
            new AmendmentReasonReference(
                UUID.randomUUID(),
                auditorRef,
                "COMPLIANCE_CORRECTION",
                "Compliance Correction",
                true,
                10,
                now,
                now));
  }

  @Test
  void shouldRejectInvalidMetadataCombinations() {
    Claim targetClaim = claimRepository.findById(ClaimsDataTestUtil.CLAIM_1_ID).orElseThrow();

    // Create a phantom reason reference that has an ID completely unknown to the DB
    AmendmentReasonReference phantomReason =
        AmendmentReasonReference.builder()
            .id(UUID.randomUUID()) // Random UUID that doesn't exist in reference tables!
            .code("FAKE_CODE")
            .build();

    ClaimAmendment corruptAmendment =
        ClaimAmendment.builder()
            .id(Uuid7.timeBasedUuid())
            .claim(targetClaim)
            .amendmentReason(
                phantomReason) // Pass the orphan reason to trigger an actual FK failure
            .beforeState("{}")
            .requestPayload("{}")
            .diff("{}")
            .createdByUserId(ClaimsDataTestUtil.USER_ID)
            .createdOn(OffsetDateTime.now())
            .build();

    // This guarantees a DataIntegrityViolationException because the foreign key check will fail!
    assertThrows(
        DataIntegrityViolationException.class,
        () -> {
          claimAmendmentRepository.saveAndFlush(corruptAmendment);
        });
  }
}
