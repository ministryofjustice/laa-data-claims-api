package uk.gov.justice.laa.dstew.payments.claimsdata.service.coercion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.BulkSubmissionStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimAmendmentPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionPatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionStatus;

/**
 * Unit tests for the TEMPORARY (DSTEW-2173) {@link LegacyStatusCoercer}. When this bean is removed,
 * delete this test too.
 */
@DisplayName("Legacy status coercion")
class LegacyStatusCoercerTest {

  private final LegacyStatusCoercer coercer = new LegacyStatusCoercer();

  @Nested
  @DisplayName("submission patch")
  class SubmissionPatchCoercion {

    @Test
    @DisplayName("demotes VALIDATED_PENDING_APPROVAL to VALIDATION_SUCCEEDED")
    void demotesValidatedPendingApproval() {
      SubmissionPatch patch =
          new SubmissionPatch().status(SubmissionStatus.VALIDATED_PENDING_APPROVAL);

      coercer.coerce(patch);

      assertThat(patch.getStatus()).isEqualTo(SubmissionStatus.VALIDATION_SUCCEEDED);
    }

    @Test
    @DisplayName("leaves other statuses untouched")
    void leavesOtherStatusesUntouched() {
      SubmissionPatch patch = new SubmissionPatch().status(SubmissionStatus.VALIDATION_FAILED);

      coercer.coerce(patch);

      assertThat(patch.getStatus()).isEqualTo(SubmissionStatus.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("tolerates null status and null patch")
    void toleratesNulls() {
      SubmissionPatch patch = new SubmissionPatch();

      coercer.coerce(patch);
      coercer.coerce((SubmissionPatch) null);

      assertThat(patch.getStatus()).isNull();
    }
  }

  @Nested
  @DisplayName("bulk submission patch")
  class BulkSubmissionPatchCoercion {

    @Test
    @DisplayName("demotes VALIDATED_PENDING_APPROVAL to VALIDATION_SUCCEEDED")
    void demotesValidatedPendingApproval() {
      BulkSubmissionPatch patch =
          new BulkSubmissionPatch().status(BulkSubmissionStatus.VALIDATED_PENDING_APPROVAL);

      coercer.coerce(patch);

      assertThat(patch.getStatus()).isEqualTo(BulkSubmissionStatus.VALIDATION_SUCCEEDED);
    }

    @Test
    @DisplayName("leaves other statuses untouched")
    void leavesOtherStatusesUntouched() {
      BulkSubmissionPatch patch =
          new BulkSubmissionPatch().status(BulkSubmissionStatus.VALIDATION_FAILED);

      coercer.coerce(patch);

      assertThat(patch.getStatus()).isEqualTo(BulkSubmissionStatus.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("tolerates null status and null patch")
    void toleratesNulls() {
      BulkSubmissionPatch patch = new BulkSubmissionPatch();

      coercer.coerce(patch);
      coercer.coerce((BulkSubmissionPatch) null);

      assertThat(patch.getStatus()).isNull();
    }
  }

  @Nested
  @DisplayName("claim patch")
  class ClaimPatchCoercion {

    @Test
    @DisplayName("demotes VALIDATED_PENDING_APPROVAL to VALID")
    void demotesValidatedPendingApproval() {
      ClaimAmendmentPatch patch =
          new ClaimAmendmentPatch().status(ClaimStatus.VALIDATED_PENDING_APPROVAL);

      coercer.coerce(patch);

      assertThat(patch.getStatus()).isEqualTo(ClaimStatus.VALID);
    }

    @Test
    @DisplayName("leaves other statuses untouched")
    void leavesOtherStatusesUntouched() {
      ClaimAmendmentPatch patch = new ClaimAmendmentPatch().status(ClaimStatus.INVALID);

      coercer.coerce(patch);

      assertThat(patch.getStatus()).isEqualTo(ClaimStatus.INVALID);
    }

    @Test
    @DisplayName("tolerates null status and null patch")
    void toleratesNulls() {
      ClaimAmendmentPatch patch = new ClaimAmendmentPatch();

      coercer.coerce(patch);
      coercer.coerce((ClaimAmendmentPatch) null);

      assertThat(patch.getStatus()).isNull();
    }
  }

  @Nested
  @DisplayName("submission entity (NIL create)")
  class SubmissionEntityCoercion {

    @Test
    @DisplayName("demotes VALIDATED_PENDING_APPROVAL to VALIDATION_SUCCEEDED")
    void demotesValidatedPendingApproval() {
      Submission submission =
          Submission.builder()
              .id(UUID.randomUUID())
              .status(SubmissionStatus.VALIDATED_PENDING_APPROVAL)
              .build();

      coercer.coerce(submission);

      assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.VALIDATION_SUCCEEDED);
    }

    @Test
    @DisplayName("leaves other statuses untouched")
    void leavesOtherStatusesUntouched() {
      Submission submission =
          Submission.builder().id(UUID.randomUUID()).status(SubmissionStatus.CREATED).build();

      coercer.coerce(submission);

      assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.CREATED);
    }

    @Test
    @DisplayName("tolerates null submission")
    void toleratesNull() {
      assertThatCode(() -> coercer.coerce((Submission) null)).doesNotThrowAnyException();
    }
  }
}
