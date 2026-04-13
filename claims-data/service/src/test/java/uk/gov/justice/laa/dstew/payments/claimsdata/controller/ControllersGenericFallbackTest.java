package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.*;
import uk.gov.justice.laa.dstew.payments.claimsdata.validator.BulkSubmissionFileValidator;

/**
 * Centralised tests to ensure each controller's resilience4j fallback returns HTTP 429 when the
 * rate limiter triggers. Tests invoke the private genericFallback(RequestNotPermitted) method via
 * reflection to validate the response without wiring Resilience4j in tests.
 */
@ExtendWith(MockitoExtension.class)
class ControllersGenericFallbackTest {

  @Test
  void claimController_genericFallback_returns429() throws Exception {
    ClaimController controller = new ClaimController(mock(ClaimService.class));
    invokeGenericFallbackAndAssert429(controller);
  }

  @Test
  void bulkSubmissionController_genericFallback_returns429() throws Exception {
    BulkSubmissionController controller =
        new BulkSubmissionController(
            mock(BulkSubmissionService.class), mock(BulkSubmissionFileValidator.class));
    invokeGenericFallbackAndAssert429(controller);
  }

  @Test
  void matterStartsController_genericFallback_returns429() throws Exception {
    MatterStartsController controller = new MatterStartsController(mock(MatterStartService.class));
    invokeGenericFallbackAndAssert429(controller);
  }

  @Test
  void submissionController_genericFallback_returns429() throws Exception {
    SubmissionController controller = new SubmissionController(mock(SubmissionService.class));
    invokeGenericFallbackAndAssert429(controller);
  }

  @Test
  void assessmentController_genericFallback_returns429() throws Exception {
    AssessmentController controller = new AssessmentController(mock(AssessmentService.class));
    invokeGenericFallbackAndAssert429(controller);
  }

  @Test
  void validationController_genericFallback_returns429() throws Exception {
    ValidationController controller =
        new ValidationController(mock(ValidationMessageService.class));
    invokeGenericFallbackAndAssert429(controller);
  }

  private void invokeGenericFallbackAndAssert429(Object controller) throws Exception {
    Method m =
        controller.getClass().getDeclaredMethod("genericFallback", RequestNotPermitted.class);
    m.setAccessible(true);
    RequestNotPermitted ex = mock(RequestNotPermitted.class);
    Object result = m.invoke(controller, ex);
    assertThat(result).isInstanceOf(ResponseEntity.class);
    ResponseEntity<?> resp = (ResponseEntity<?>) result;
    assertThat(resp.getStatusCode().value()).isEqualTo(429);
  }
}
