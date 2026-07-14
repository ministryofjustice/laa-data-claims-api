package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ChangeSource;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.DiffEntry;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee.FeeSchemeRequestField;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence.AmendmentChangeDetector;

/**
 * Validation step that enforces the rule: if a claim already has an assessment, provider-requested
 * changes to fields that impact FSP pricing are not allowed.
 */
@Component
public class AssessedClaimPricingValidationStep implements ClaimAmendmentValidationStep {

  private final AmendmentChangeDetector changeDetector;

  @Autowired
  public AssessedClaimPricingValidationStep(AmendmentChangeDetector changeDetector) {
    this.changeDetector = changeDetector;
  }

  @Override
  public List<ClaimAmendmentValidationError> validate(ClaimAmendmentState state) {
    // If claim is not assessed, this rule does not apply.
    if (state.getBeforeState() == null || !state.getBeforeState().hasAssessment()) {
      return List.of();
    }

    var areaOfLaw = state.getBeforeState().getAreaOfLaw();

    List<DiffEntry> changes = changeDetector.detectChanges(state);
    List<String> offendingFields = new ArrayList<>();

    for (DiffEntry entry : changes) {
      if (entry.changeSource() != ChangeSource.REQUESTED) {
        continue;
      }
      String identifier = entry.fieldIdentifier();
      // fieldIdentifier is qualified like "claimSummaryFee.netProfitCostsAmount" or
      // "client.clientSurname" - pass the full identifier to impactsPricing so the
      // registry can disambiguate by entity when necessary.
      if (FeeSchemeRequestField.impactsPricing(identifier, areaOfLaw)) {
        offendingFields.add(identifier);
      }
    }

    if (offendingFields.isEmpty()) {
      return List.of();
    }

    String joined = String.join(", ", offendingFields);
    return List.of(
        ClaimAmendmentValidationError.of(
            ClaimAmendmentValidationCode.INVALID_PRICING_AMENDMENT_ON_ASSESSED_CLAIM, joined));
  }
}
