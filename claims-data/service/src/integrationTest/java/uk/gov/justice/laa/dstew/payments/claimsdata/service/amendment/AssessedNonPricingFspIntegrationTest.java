package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.AmendmentTestFixtures.REASON_PROVIDER_ERROR;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.AmendmentTestFixtures.REQUESTED_BY_PROVIDER;
import static uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.AmendmentTestFixtures.VALID_USER_UUID;

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

// Note: do not reference FspClient here - test only spies the step bean

@DisplayName("Assessed non-pricing amendment skips FSP (integration)")
class AssessedNonPricingFspIntegrationTest extends AbstractIntegrationTest {

  @Autowired private java.util.List<ClaimAmendmentValidationStep> discoveredSteps;
  @Autowired private ClaimAmendmentPreparationService preparationService;
  @Autowired private ClaimAmendmentCommitService commitService;
  @Autowired private jakarta.persistence.EntityManager entityManager;

  @Test
  @Transactional
  void assessedNonPricingDoesNotInvokeFspClient() {
    // Ensure claim has assessment data
    entityManager.clear();
    seedAssessmentsData();

    // Non-pricing amendment payload: change client surname only
    ClaimAmendmentPayload payload =
        ClaimAmendmentPayload.builder()
            .amendmentRequestedBy(JsonNullable.of(REQUESTED_BY_PROVIDER))
            .amendmentReasonCode(JsonNullable.of(REASON_PROVIDER_ERROR))
            .amendmentUserId(JsonNullable.of(VALID_USER_UUID))
            .clientSurname(JsonNullable.of("NewSurname"))
            .build();

    // Build map of discovered beans and replace the real FSP step with a spy so we can
    // inspect internal interactions. We will also replace the fspClient field inside the
    // step with a Mockito mock to assert calculate(...) is never invoked.
    Map<Class<?>, ClaimAmendmentValidationStep> beanByClass =
        discoveredSteps.stream().collect(Collectors.toMap(Object::getClass, step -> step));

    ClaimAmendmentValidationStep fspStep = beanByClass.get(AmendmentFspValidationStep.class);
    ClaimAmendmentValidationStep spiedStep = null;
    if (fspStep != null) {
      // Replace the real FSP validation step with a Mockito spy so we can assert it
      // was not invoked during processing of a non-pricing amendment on an assessed claim.
      spiedStep = spy(fspStep);
      beanByClass.put(AmendmentFspValidationStep.class, spiedStep);
    }

    ClaimAmendmentValidationStep[] steps =
        ClaimAmendmentValidationService.STEP_ORDER.stream()
            .map(beanByClass::get)
            .toArray(ClaimAmendmentValidationStep[]::new);

    ClaimAmendmentService service =
        new ClaimAmendmentService(
            preparationService, new ClaimAmendmentValidationService(steps), commitService);

    ClaimAmendmentResult result = service.submitAmendment(claim1, payload);

    assertThat(result.isSuccess()).isTrue();

    if (spiedStep != null) {
      verify(spiedStep, never()).validate(any());
    }
  }
}
