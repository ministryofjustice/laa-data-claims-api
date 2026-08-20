package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context;

import io.cucumber.spring.ScenarioScope;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

/**
 * Scenario-scoped context that lets multiple amendment-related BDD step classes drive the same
 * {@code When I submit the amendment and wait for the event service to complete amendment
 * validation} step without duplicating the step definition.
 *
 * <p>The step definition itself remains on {@link
 * uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps.AmendmentMetadataValidationSteps}
 * (Cucumber requires exactly one owner per phrase). Other step classes populate {@link
 * #submissionId}, {@link #claimId} and {@link #patchJson} in their Given steps; the shared When
 * step consults this context first and falls back to its own local flow when the context is empty.
 *
 * <p>Scenario scope ensures a fresh instance per Cucumber scenario, so no explicit reset is needed.
 */
@Component
@ScenarioScope
@Getter
@Setter
public class SharedAmendmentPatchContext {

  private UUID submissionId;
  private UUID claimId;
  private String patchJson;

  /** {@code true} when all three fields are set — the submit step should use this context. */
  public boolean isPopulated() {
    return submissionId != null && claimId != null && patchJson != null;
  }
}
