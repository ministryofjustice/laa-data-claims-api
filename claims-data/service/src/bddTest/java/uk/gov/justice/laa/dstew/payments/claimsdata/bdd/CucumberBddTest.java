package uk.gov.justice.laa.dstew.payments.claimsdata.bdd;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/bdd")
@ConfigurationParameter(
    key = GLUE_PROPERTY_NAME,
    value =
        "uk.gov.justice.laa.dstew.payments.claimsdata.bdd,uk.gov.justice.laa.dstew.payments.claimsdata.bdd.hooks,uk.gov.justice.laa.dstew.payments.claimsdata.bdd.steps")
public class CucumberBddTest {}
