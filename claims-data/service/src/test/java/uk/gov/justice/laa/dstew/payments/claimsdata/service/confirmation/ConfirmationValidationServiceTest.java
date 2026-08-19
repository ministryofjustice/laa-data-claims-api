package uk.gov.justice.laa.dstew.payments.claimsdata.service.confirmation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionResponse;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;

class ConfirmationValidationServiceTest {

  @Test
  void succeedsWithoutChangingSubmissionWhenNoValidatorsAreRegistered() {
    SubmissionResponse submission =
        new SubmissionResponse().status(SubmissionStatus.READY_FOR_SUBMISSION);
    ConfirmationValidationService service = new ConfirmationValidationService(List.of());

    assertThat(service.validate(submission)).isEmpty();
    assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.READY_FOR_SUBMISSION);
  }
}
