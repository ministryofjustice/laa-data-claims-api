package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.AmendmentTestFixtures.*;

import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentPayload;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentResult;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.AmendmentFspValidationStep;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation.ClaimAmendmentValidationStep;

@DisplayName("Unassessed pricing amendment invokes FSP step (integration)")
class UnassessedPricingInvokesFspIntegrationTest extends AbstractIntegrationTest {

  @Autowired private java.util.List<ClaimAmendmentValidationStep> discoveredSteps;
  @Autowired private ClaimAmendmentPreparationService preparationService;
  @Autowired private ClaimAmendmentCommitService commitService;
  @Autowired private jakarta.persistence.EntityManager entityManager;

  @Test
  @Transactional
  void unassessedPricingInvokesFspStep() {
    // Ensure no assessment data is present for the claim (unassessed case)
    entityManager.clear();

    // Build a pricing-impacting payload: change netProfitCostsAmount
    ClaimAmendmentPayload payload =
        ClaimAmendmentPayload.builder()
            .amendmentRequestedBy(JsonNullable.of(REQUESTED_BY_PROVIDER))
            .amendmentReasonCode(JsonNullable.of(REASON_PROVIDER_ERROR))
            .amendmentUserId(JsonNullable.of(VALID_USER_UUID))
            .netProfitCostsAmount(JsonNullable.of(java.math.BigDecimal.valueOf(200)))
            .build();

    // Replace the FSP validation step with a mock so we can assert it was invoked
    Map<Class<?>, ClaimAmendmentValidationStep> beanByClass =
        discoveredSteps.stream().collect(Collectors.toMap(Object::getClass, step -> step));

    ClaimAmendmentValidationStep mockFsp = mock(AmendmentFspValidationStep.class);
    beanByClass.put(AmendmentFspValidationStep.class, mockFsp);

    ClaimAmendmentValidationStep[] steps =
        ClaimAmendmentValidationService.STEP_ORDER.stream()
            .map(beanByClass::get)
            .toArray(ClaimAmendmentValidationStep[]::new);

    ClaimAmendmentService service =
        new ClaimAmendmentService(
            preparationService, new ClaimAmendmentValidationService(steps), commitService);

    ClaimAmendmentResult result = service.submitAmendment(claim1, payload);

    // Amendment should be accepted for unassessed pricing change
    assertThat(result.isSuccess()).isTrue();

    // FSP validation step must have been invoked (orchestrator reaches it for pricing change)
    verify(mockFsp).validate(org.mockito.ArgumentMatchers.any());
  }
}
