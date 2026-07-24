package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.InquestDetail;

class InquestCompletenessDefinitionTest {
  @Test
  void evaluatesOnlyTheConfiguredMandatoryFields() {
    InquestCompletenessDefinition definition =
        new InquestCompletenessDefinition("DECEASED_SURNAME,INTERESTED_GOVERNMENT_DEPARTMENT");
    InquestDetail detail = new InquestDetail();
    detail.setDeceasedSurname("Jones");

    assertThat(definition.isComplete(detail, 1, 0)).isTrue();
    assertThat(definition.isComplete(detail, 0, 20)).isFalse();
  }

  @Test
  void treatsBlankConfiguredScalarAsIncomplete() {
    InquestCompletenessDefinition definition =
        new InquestCompletenessDefinition("CORONERS_INQUEST_REFERENCE");
    InquestDetail detail = new InquestDetail();
    detail.setCoronersInquestReference(" ");

    assertThat(definition.isComplete(detail, 0, 0)).isFalse();
  }

  @Test
  void treatsAnAbsentDetailRowAsIncomplete() {
    InquestCompletenessDefinition definition = new InquestCompletenessDefinition("");

    assertThat(definition.isComplete(null, 0, 0)).isFalse();
  }
}
