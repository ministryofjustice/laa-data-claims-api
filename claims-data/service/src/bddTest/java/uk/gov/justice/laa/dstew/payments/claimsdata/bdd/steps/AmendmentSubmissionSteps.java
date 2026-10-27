package uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps;

import static uk.gov.justice.laa.dstew.payments.claimsdata.bdd.config.BddTestConstants.DEFAULT_OFFICE;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.context.BddScenarioContext;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.BulkSubmissionFileGenerator;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.BulkSubmissionFileGenerator.ClaimOverride;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.BulkSubmissionFileGenerator.Format;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.BulkSubmissionFileGenerator.GeneratedFile;
import uk.gov.justice.laa.dstew.payments.claimsdata.bdd.generator.SubmissionPeriodHelper;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AreaOfLaw;

/** Cucumber setup for bulk-submission scenarios that exercise amendment detection. */
public class AmendmentSubmissionSteps {

  @Autowired private BddScenarioContext context;
  @Autowired private BulkSubmissionFileGenerator generator;
  @Autowired private SubmissionPeriodHelper periodHelper;

  /** Generates a Legal Help TXT submission containing the claims in the feature table. */
  @Given("an amendment submission with the following claims")
  public void anAmendmentSubmissionWithTheFollowingClaims(DataTable table) throws IOException {
    List<Map<String, String>> rows = table.asMaps(String.class, String.class);
    List<ClaimOverride> overrides = BulkSubmissionFileGenerator.overridesFromRows(rows);

    String office =
        overrides.stream()
            .map(ClaimOverride::office)
            .filter(StringUtils::isNotBlank)
            .findFirst()
            .orElse(DEFAULT_OFFICE);
    AreaOfLaw areaOfLaw = AreaOfLaw.LEGAL_HELP;
    String period = periodHelper.nextAvailablePeriod(office, areaOfLaw);
    GeneratedFile generated =
        generator.generate(
            Format.TXT, Math.max(rows.size(), 1), office, period, overrides, areaOfLaw);

    context.setGeneratedFilePath(generated.path());
    context.setLastOffice(generated.office());
    context.setLastSubmissionPeriod(generated.submissionPeriod());
  }
}
