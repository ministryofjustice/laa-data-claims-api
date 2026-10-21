package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.support.BddStepFailures.step;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.SharedAmendmentPatchContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.support.BddMockServerSupport;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.CalculatedFeeDetail;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimAmendment;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.CalculatedFeeDetailRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimAmendmentRepository;

/**
 * Step definitions for {@code amendmentsFspCalculatedFeeHandoff.feature} (DSTEW-1762).
 *
 * <p>Asserts the successful-FSP persistence handoff that {@code FeeSchemeHandoffFactory} prepares
 * and {@code ClaimAmendmentCommitService} commits inside the single atomic write transaction: the
 * amendment-linked {@code calculated_fee_detail} row ({@code claim_amendment_id}), the {@code
 * is_price_changed} flag (FSP total vs the previous calculated total), the {@code
 * change_source=FSP} diff entries, retention of the previous calculated-fee row, and discard of the
 * prepared row when the atomic save rolls back.
 *
 * <p>Seeding + submit + rejection/accepted assertions reuse the DSTEW-2301 harness and DSTEW-1753
 * concurrent-writer steps; only the handoff-specific arming + read-back assertions are new here.
 */
@Slf4j
public class AmendmentFspCalculatedFeeHandoffSteps {

  @Autowired private SharedAmendmentPatchContext sharedPatchContext;
  @Autowired private CalculatedFeeDetailRepository calculatedFeeDetailRepository;
  @Autowired private ClaimAmendmentRepository claimAmendmentRepository;
  @Autowired private BddMockServerSupport mock;

  private final ObjectMapper objectMapper = new ObjectMapper();

  // Recorded when the previous calculated-fee total is set, so retention can be asserted later.
  private UUID previousCfdId;
  private BigDecimal previousCfdTotal;

  // ---------------------------------------------------------------------------
  // Given — arm the previous calculated state + the FSP monetary outcome
  // ---------------------------------------------------------------------------

  @Given("the claim's previous calculated fee total is {double}")
  public void theClaimsPreviousCalculatedFeeTotalIs(double total) {
    step(
        "set the seeded baseline calculated_fee_detail total_amount to " + total,
        () -> {
          UUID claimId = requireClaimId();
          CalculatedFeeDetail baseline =
              calculatedFeeDetailRepository
                  .findFirstByClaimIdOrderByCreatedOnDescIdDesc(claimId)
                  .orElseThrow(
                      () -> new AssertionError("No baseline calculated_fee_detail for " + claimId));
          baseline.setTotalAmount(BigDecimal.valueOf(total));
          CalculatedFeeDetail saved = calculatedFeeDetailRepository.saveAndFlush(baseline);
          previousCfdId = saved.getId();
          previousCfdTotal = saved.getTotalAmount();
        });
  }

  @Given("the FSP service will return a fee calculation with total amount {double}")
  public void theFspServiceWillReturnAFeeCalculationWithTotalAmount(double total) {
    step(
        "arm FSP fee-calculation to return 200 with feeCalculation.totalAmount=" + total,
        () -> mock.stubAmendmentFspCalculationWithTotal(total));
  }

  // ---------------------------------------------------------------------------
  // Then — prepared calculated_fee_detail row assertions
  // ---------------------------------------------------------------------------

  @Then("the latest calculated_fee_detail row for the claim has is_price_changed {word}")
  public void theLatestCalculatedFeeDetailRowHasIsPriceChanged(String expected) {
    step(
        "assert the latest calculated_fee_detail row's is_price_changed == " + expected,
        () -> {
          boolean expectedFlag = Boolean.parseBoolean(expected);
          CalculatedFeeDetail latest = requireLatestCfd();
          assertThat(latest.getIsPriceChanged())
              .as("is_price_changed on the amendment-driven calculated_fee_detail row")
              .isEqualTo(expectedFlag);
        });
  }

  @Then("the latest calculated_fee_detail row is linked to the committed amendment")
  public void theLatestCalculatedFeeDetailRowIsLinkedToTheCommittedAmendment() {
    step(
        "assert the latest calculated_fee_detail row's claim_amendment_id equals the committed"
            + " amendment id",
        () -> {
          CalculatedFeeDetail latest = requireLatestCfd();
          ClaimAmendment committed = requireCommittedAmendment();
          assertThat(latest.getClaimAmendment())
              .as("the prepared calculated_fee_detail row must be linked to an amendment")
              .isNotNull();
          assertThat(latest.getClaimAmendment().getId())
              .as("claim_amendment_id must equal the committed amendment id")
              .isEqualTo(committed.getId());
        });
  }

  @Then("the claim has exactly {int} calculated_fee_detail rows")
  public void theClaimHasExactlyCalculatedFeeDetailRows(int expected) {
    step(
        "assert the claim has exactly " + expected + " calculated_fee_detail row(s)",
        () ->
            assertThat(countCfd(requireClaimId()))
                .as("calculated_fee_detail row count for the claim")
                .isEqualTo((long) expected));
  }

  @Then("the previous calculated_fee_detail row is retained unchanged")
  public void thePreviousCalculatedFeeDetailRowIsRetainedUnchanged() {
    step(
        "assert the pre-amendment calculated_fee_detail row is still present, unlinked and with its"
            + " original total",
        () -> {
          assertThat(previousCfdId)
              .as("previous calculated_fee_detail id must have been recorded")
              .isNotNull();
          CalculatedFeeDetail previous =
              calculatedFeeDetailRepository
                  .findById(previousCfdId)
                  .orElseThrow(
                      () ->
                          new AssertionError(
                              "Previous calculated_fee_detail row was removed: " + previousCfdId));
          assertThat(previous.getClaimAmendment())
              .as("the previous (pre-amendment) row must NOT be linked to the new amendment")
              .isNull();
          assertThat(previous.getTotalAmount())
              .as("the previous row's total must be left untouched")
              .isEqualByComparingTo(previousCfdTotal);
        });
  }

  @Then("the committed amendment diff contains a change_source {string} entry for field {string}")
  public void theCommittedAmendmentDiffContainsAChangeSourceEntryForField(
      String changeSource, String fieldIdentifier) {
    step(
        "assert the committed amendment diff carries a change_source=\""
            + changeSource
            + "\" entry for field \""
            + fieldIdentifier
            + "\"",
        () -> {
          ClaimAmendment committed = requireCommittedAmendment();
          JsonNode diff = objectMapper.readTree(committed.getDiff());
          JsonNode changes = diff.path("changes");
          assertThat(changes.isArray())
              .as("committed amendment diff must carry a 'changes' array (diff=%s)", diff)
              .isTrue();
          boolean found = false;
          for (JsonNode change : changes) {
            if (fieldIdentifier.equals(change.path("field_identifier").asText())
                && changeSource.equals(change.path("change_source").asText())) {
              found = true;
              break;
            }
          }
          assertThat(found)
              .as(
                  "expected a change_source=%s diff entry for field %s (diff=%s)",
                  changeSource, fieldIdentifier, diff)
              .isTrue();
        });
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private UUID requireClaimId() {
    UUID claimId = sharedPatchContext.getClaimId();
    assertThat(claimId).as("a claim must be seeded first").isNotNull();
    return claimId;
  }

  private CalculatedFeeDetail requireLatestCfd() {
    UUID claimId = requireClaimId();
    return calculatedFeeDetailRepository
        .findFirstByClaimIdOrderByCreatedOnDescIdDesc(claimId)
        .orElseThrow(() -> new AssertionError("No calculated_fee_detail row for " + claimId));
  }

  private ClaimAmendment requireCommittedAmendment() {
    UUID claimId = requireClaimId();
    List<ClaimAmendment> rows = claimAmendmentRepository.findByClaimIdOrderByIdDesc(claimId);
    assertThat(rows)
        .as("exactly one committed claim_amendment row is expected for claim %s", claimId)
        .hasSize(1);
    return rows.getFirst();
  }

  private long countCfd(UUID claimId) {
    return calculatedFeeDetailRepository.findAll().stream()
        .map(CalculatedFeeDetail::getClaim)
        .filter(java.util.Objects::nonNull)
        .map(claim -> claim.getId())
        .filter(claimId::equals)
        .count();
  }
}
