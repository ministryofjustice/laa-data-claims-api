package uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessagePatch;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;

/**
 * In-memory aggregate describing a claim amendment in progress, passed from the retrieval/build
 * step to downstream validation, history and persistence tasks.
 *
 * <p>It bundles the three pieces the amendment flow needs:
 *
 * <ul>
 *   <li>{@link #beforeState} - the current stored values (basis for the {@code beforeState} JSONB);
 *   <li>{@link #requestPayload} - the sparse, presence-aware submission (basis for the {@code
 *       requestPayload} JSONB);
 *   <li>{@link #postAmendmentState} - the proposed amended values, built by applying the sparse
 *       payload onto the before-state (omitted fields retain stored values; explicit nulls are
 *       retained as requested clears).
 * </ul>
 *
 * <p>The before/after snapshots plus the payload's field presence are sufficient to compute the
 * {@code diff} JSONB.
 *
 * <p>Validation steps accumulate any issues they find in {@link #validationIssues} via {@link
 * #addValidationIssue(ValidationMessagePatch)}, so the flow can collect every message before
 * deciding whether the amendment may proceed.
 */
@Data
@Builder
public class ClaimAmendmentState {

  private ClaimStateSnapshot beforeState;

  private ClaimAmendmentPayload requestPayload;

  private ClaimStateSnapshot postAmendmentState;

  @Builder.Default private List<ValidationMessagePatch> validationIssues = new ArrayList<>();

  /**
   * Records a validation issue against this amendment.
   *
   * @param issue the issue to add
   */
  public void addValidationIssue(ValidationMessagePatch issue) {
    if (validationIssues == null) {
      validationIssues = new ArrayList<>();
    }
    validationIssues.add(issue);
  }

  /**
   * Indicates whether any collected validation issue is fatal, i.e. of type {@link
   * ValidationMessageType#ERROR}. A fatal issue means the amendment must not be saved; non-fatal
   * issues (e.g. {@link ValidationMessageType#WARNING}) do not block the amendment.
   *
   * @return {@code true} if at least one validation issue is an error; {@code false} otherwise
   */
  public boolean hasFatalValidationIssues() {
    return validationIssues != null
        && validationIssues.stream()
            .anyMatch(issue -> issue.getType() == ValidationMessageType.ERROR);
  }
}
