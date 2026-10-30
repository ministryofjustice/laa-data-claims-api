package uk.gov.justice.laa.dstew.payments.claimsdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_ID;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;

@DisplayName("Submission provider state setup")
class SubmissionProviderStateTest {

  private final DataClaimsApiProviderTests provider = new DataClaimsApiProviderTests();

  SubmissionProviderStateTest() {
    provider.submissionRepository = mock(SubmissionRepository.class);
  }

  @ParameterizedTest
  @EnumSource(SubmissionStatus.class)
  @DisplayName("Uses the submission status supplied by the consumer")
  void usesRequestedStatus(SubmissionStatus status) {
    provider.theSystemIsReadyToUpdateASubmission(Map.of("status", status.getValue()));

    assertThat(provider.submissionRepository.findByIdAndLockForUpdate(SUBMISSION_ID))
        .get()
        .extracting(submission -> submission.getStatus())
        .isEqualTo(status);
  }

  @Test
  @DisplayName("Defaults to created when the consumer supplies no status")
  void defaultsToCreatedWithoutStatus() {
    provider.theSystemIsReadyToUpdateASubmission(Map.of());

    assertThat(provider.submissionRepository.findByIdAndLockForUpdate(SUBMISSION_ID))
        .get()
        .extracting(submission -> submission.getStatus())
        .isEqualTo(SubmissionStatus.CREATED);
  }

  @Test
  @DisplayName("Rejects an unrecognised submission status")
  void rejectsUnrecognisedStatus() {
    assertThatThrownBy(
            () -> provider.theSystemIsReadyToUpdateASubmission(Map.of("status", "UNKNOWN")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
