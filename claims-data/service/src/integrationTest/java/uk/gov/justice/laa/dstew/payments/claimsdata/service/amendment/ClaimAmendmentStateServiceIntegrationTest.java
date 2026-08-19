package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CASE_REFERENCE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_4_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.FEE_CODE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.MATTER_TYPE_CODE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SCHEDULE_REFERENCE;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.UNIQUE_FILE_NUMBER;

import java.time.LocalDate;
import java.time.Month;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.PreparedAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;

/**
 * Integration tests for {@link ClaimAmendmentStateService}.
 *
 * <p>These exercise the full Spring wiring (service, MapStruct mapper, builder), the JPA
 * repositories and the retrieve-and-build behaviour against a real PostgreSQL (Testcontainers).
 * They complement the fast, mock-based unit tests by verifying the end-to-end retrieve-and-build
 * behaviour the parent amendment flow relies on:
 *
 * <ul>
 *   <li>a populated before-state and a correctly applied post-amendment state;
 *   <li>a missing claim reported as {@link Optional#empty()} (mapped upstream to {@code
 *       INVALID_CLAIM_NOT_FOUND});
 *   <li>no database side effects (this step is read-only);
 *   <li>tri-state apply semantics (explicit null clears; omitted retains);
 *   <li>UCN/UFN never recomputed from name/DOB/case-id changes;
 *   <li>selection of the latest assessment;
 *   <li>graceful handling of a claim with no associated records.
 * </ul>
 *
 * <p>The class is {@code @Transactional} to stand in for the parent orchestrator's single atomic
 * amendment transaction: the service declares no transaction of its own, so the test must supply
 * one to keep the persistence context open for the mapper's lazy navigation (e.g. {@code
 * claim.submission}), exactly as the orchestrator will in production.
 */
@TestInstance(Lifecycle.PER_CLASS)
@Transactional
@DisplayName("ClaimAmendmentStateService Integration Test")
class ClaimAmendmentStateServiceIntegrationTest extends AbstractIntegrationTest {

  // Amended (submitted) values
  private static final String AMENDED_SCHEDULE_REFERENCE = "NEW-SCH";
  private static final String AMENDED_CLIENT_FORENAME = "Alicia";
  private static final String UPDATED_SCHEDULE_REFERENCE = "CHANGED";
  private static final String AMENDED_FORENAME = "Changed";
  private static final String AMENDED_SURNAME = "Name";
  private static final LocalDate AMENDED_DATE_OF_BIRTH = LocalDate.of(1990, Month.JANUARY, 1);
  private static final String AMENDED_CASE_ID = "NEW_CASE_ID";

  @Autowired private ClaimAmendmentStateService amendmentStateService;

  @Test
  @DisplayName("Builds before-state and applies sparse payload to produce post-amendment state")
  void retrievesAmendmentStateHappyPathBuildsBeforeAndPostState() {
    seedAssessmentsData();

    ClaimAmendmentPayload payload =
        ClaimAmendmentPayload.builder()
            .scheduleReference(JsonNullable.of(AMENDED_SCHEDULE_REFERENCE))
            .clientForename(JsonNullable.of(AMENDED_CLIENT_FORENAME))
            .isVatApplicable(JsonNullable.of(false))
            .build();

    PreparedAmendment result = amendmentStateService.retrieveAmendmentState(claim1, payload);

    ClaimAmendmentState state = result.state();

    // requestPayload is carried through as submitted
    assertThat(state.getRequestPayload()).isSameAs(payload);

    // before-state reflects the stored values
    ClaimStateSnapshot before = state.getBeforeState();
    assertThat(before.getClaimId()).isEqualTo(CLAIM_1_ID);
    assertThat(before.getSubmissionId()).isEqualTo(SUBMISSION_1_ID);
    assertThat(before.getStatus()).isEqualTo(ClaimStatus.READY_TO_PROCESS);
    assertThat(before.getScheduleReference()).isEqualTo(SCHEDULE_REFERENCE);
    assertThat(before.getCaseReferenceNumber()).isEqualTo(CASE_REFERENCE);
    assertThat(before.getFeeCode()).isEqualTo(FEE_CODE);
    assertThat(before.getUniqueFileNumber()).isEqualTo(UNIQUE_FILE_NUMBER);
    assertThat(before.getMatterTypeCode()).isEqualTo(MATTER_TYPE_CODE);
    assertThat(before.getClientForename()).isEqualTo(SEEDED_CLIENT_FORENAME);
    assertThat(before.getUniqueClientNumber()).isEqualTo(SEEDED_UNIQUE_CLIENT_NUMBER);
    assertThat(before.getCaseId()).isEqualTo(SEEDED_CASE_ID);
    assertThat(before.getAdviceTime()).isEqualTo(SEEDED_ADVICE_TIME);
    assertThat(before.getIsVatApplicable()).isTrue();
    assertThat(before.getCategoryOfLaw()).isEqualTo(SEEDED_CATEGORY_OF_LAW);
    assertThat(before.getCalculatedFeeDetail()).isNotNull();
    assertThat(before.getCalculatedFeeDetail().getCategoryOfLaw())
        .isEqualTo(SEEDED_CATEGORY_OF_LAW);
    assertThat(before.getLatestAssessment()).isNotNull();
    assertThat(before.getLatestAssessment().getAllowedTotalInclVat())
        .isEqualByComparingTo(SEEDED_LATEST_ASSESSMENT_ALLOWED_TOTAL_INCL_VAT);

    // post-amendment state: submitted fields change, omitted fields retain stored values
    ClaimStateSnapshot after = state.getPostAmendmentState();
    assertThat(after.getScheduleReference()).isEqualTo(AMENDED_SCHEDULE_REFERENCE);
    assertThat(after.getClientForename()).isEqualTo(AMENDED_CLIENT_FORENAME);
    assertThat(after.getIsVatApplicable()).isFalse();
    // unchanged (omitted) fields
    assertThat(after.getCaseReferenceNumber()).isEqualTo(CASE_REFERENCE);
    assertThat(after.getFeeCode()).isEqualTo(FEE_CODE);
    assertThat(after.getUniqueFileNumber()).isEqualTo(UNIQUE_FILE_NUMBER);
    assertThat(after.getUniqueClientNumber()).isEqualTo(SEEDED_UNIQUE_CLIENT_NUMBER);
    assertThat(after.getAdviceTime()).isEqualTo(SEEDED_ADVICE_TIME);
    // read-only context carried over unchanged
    assertThat(after.getStatus()).isEqualTo(ClaimStatus.READY_TO_PROCESS);
    assertThat(after.getCategoryOfLaw()).isEqualTo(SEEDED_CATEGORY_OF_LAW);
    assertThat(after.getCalculatedFeeDetail()).isSameAs(before.getCalculatedFeeDetail());
    assertThat(after.getLatestAssessment()).isSameAs(before.getLatestAssessment());
  }

  @Test
  @DisplayName("Performs no database writes (read-only step)")
  void retrievesAmendmentStateDoesNotMutateDatabase() {
    seedAssessmentsData();

    final long claims = claimRepository.count();
    final long clients = clientRepository.count();
    final long cases = claimCaseRepository.count();
    final long summaryFees = claimSummaryFeeRepository.count();
    final long calculatedFees = calculatedFeeDetailRepository.count();
    final long assessments = assessmentRepository.count();
    final long submissions = submissionRepository.count();

    ClaimAmendmentPayload payload =
        ClaimAmendmentPayload.builder()
            .scheduleReference(JsonNullable.of(UPDATED_SCHEDULE_REFERENCE))
            .caseReferenceNumber(JsonNullable.of((String) null))
            .build();

    amendmentStateService.retrieveAmendmentState(claim1, payload);

    assertThat(claimRepository.count()).isEqualTo(claims);
    assertThat(clientRepository.count()).isEqualTo(clients);
    assertThat(claimCaseRepository.count()).isEqualTo(cases);
    assertThat(claimSummaryFeeRepository.count()).isEqualTo(summaryFees);
    assertThat(calculatedFeeDetailRepository.count()).isEqualTo(calculatedFees);
    assertThat(assessmentRepository.count()).isEqualTo(assessments);
    assertThat(submissionRepository.count()).isEqualTo(submissions);

    // the stored claim is untouched
    Claim stored = claimRepository.findById(CLAIM_1_ID).orElseThrow();
    assertThat(stored.getScheduleReference()).isEqualTo(SCHEDULE_REFERENCE);
    assertThat(stored.getCaseReferenceNumber()).isEqualTo(CASE_REFERENCE);
  }

  @Test
  @DisplayName("Explicit null in the payload clears the field in the post-state only")
  void retrievesAmendmentStateExplicitNullClearsPostStateButKeepsBeforeState() {
    seedClaimsData();

    ClaimAmendmentPayload payload =
        ClaimAmendmentPayload.builder().scheduleReference(JsonNullable.of((String) null)).build();

    PreparedAmendment preparedAmendment =
        amendmentStateService.retrieveAmendmentState(claim1, payload);

    assertThat(preparedAmendment.state().getBeforeState().getScheduleReference())
        .isEqualTo(SCHEDULE_REFERENCE);
    assertThat(preparedAmendment.state().getPostAmendmentState().getScheduleReference()).isNull();
  }

  @Test
  @DisplayName("UCN and UFN are never recomputed when name, DOB or case id are amended")
  void retrievesAmendmentStateDoesNotRecomputeUcnOrUfn() {
    seedClaimsData();

    ClaimAmendmentPayload payload =
        ClaimAmendmentPayload.builder()
            .clientForename(JsonNullable.of(AMENDED_FORENAME))
            .clientSurname(JsonNullable.of(AMENDED_SURNAME))
            .clientDateOfBirth(JsonNullable.of(AMENDED_DATE_OF_BIRTH))
            .caseId(JsonNullable.of(AMENDED_CASE_ID))
            .build();

    ClaimStateSnapshot after =
        amendmentStateService
            .retrieveAmendmentState(claim1, payload)
            .state()
            .getPostAmendmentState();
    // amended identity inputs applied
    assertThat(after.getClientForename()).isEqualTo(AMENDED_FORENAME);
    assertThat(after.getCaseId()).isEqualTo(AMENDED_CASE_ID);
    // derived identifiers left untouched (not recomputed)
    assertThat(after.getUniqueClientNumber()).isEqualTo(SEEDED_UNIQUE_CLIENT_NUMBER);
    assertThat(after.getUniqueFileNumber()).isEqualTo(UNIQUE_FILE_NUMBER);
  }

  @Test
  @DisplayName("Selects the latest assessment for the before-state")
  void retrievesAmendmentStateSelectsLatestAssessment() {
    // CLAIM_1 has two assessments; the later one (240.00) must win over the earlier (120.00).
    seedAssessmentsData();

    ClaimStateSnapshot before =
        amendmentStateService
            .retrieveAmendmentState(claim1, ClaimAmendmentPayload.builder().build())
            .state()
            .getBeforeState();

    assertThat(before.getLatestAssessment()).isNotNull();
    assertThat(before.getLatestAssessment().getAllowedTotalInclVat())
        .isEqualByComparingTo(SEEDED_LATEST_ASSESSMENT_ALLOWED_TOTAL_INCL_VAT);
  }

  @Test
  @DisplayName("Builds a snapshot for a claim that has no associated records")
  void retrievesAmendmentStateClaimWithoutAssociationsBuildsSnapshotWithNulls() {
    // CLAIM_4 is persisted with a submission but no client, case, summary fee, calc fee or
    // assessment.
    seedClaimsData();

    ClaimStateSnapshot before =
        amendmentStateService
            .retrieveAmendmentState(claim4, ClaimAmendmentPayload.builder().build())
            .state()
            .getBeforeState();
    assertThat(before.getClaimId()).isEqualTo(CLAIM_4_ID);
    assertThat(before.getScheduleReference()).isEqualTo(SCHEDULE_REFERENCE);
    assertThat(before.getClientForename()).isNull();
    assertThat(before.getCaseId()).isNull();
    assertThat(before.getCategoryOfLaw()).isNull();
    assertThat(before.getCalculatedFeeDetail()).isNull();
    assertThat(before.getLatestAssessment()).isNull();
    assertThat(before.hasAssessment()).isFalse();
  }
}
