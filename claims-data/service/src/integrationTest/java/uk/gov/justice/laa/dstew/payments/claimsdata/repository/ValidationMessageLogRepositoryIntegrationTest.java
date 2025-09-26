package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_ID;

import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;

@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("ValidationMessageLogRepository Integration Test")
public class ValidationMessageLogRepositoryIntegrationTest extends AbstractIntegrationTest {

  @BeforeEach
  void setup() {
    setupValidationMessageLogTestData();
  }

  @Test
  @DisplayName("Should count distinct claim IDs by submission ID")
  void shouldCountDistinctClaimIdsBySubmissionId() {
    long count =
        validationMessageLogRepository.countDistinctClaimIdsBySubmissionIdAndType(
            SUBMISSION_ID, null);
    assertThat(count).isEqualTo(2L);
  }

  @ParameterizedTest(name = "Should find validation messages for type={0}")
  @MethodSource("validationTypeProvider")
  @DisplayName("Should find validation messages by type")
  void shouldFindValidationMessagesByType(
      ValidationMessageType type, UUID claimId, String displayMessage) {
    Pageable pageable = PageRequest.of(0, 10);

    ValidationMessageLog probe = new ValidationMessageLog();
    probe.setSubmissionId(SUBMISSION_ID);
    probe.setClaimId(claimId);
    probe.setType(type);
    probe.setSource("SYSTEM");

    Page<ValidationMessageLog> result =
        validationMessageLogRepository.findAll(Example.of(probe), pageable);

    assertThat(result.getTotalElements()).isEqualTo(1);
    var message = result.getContent().getFirst();
    assertThat(message.getClaimId()).isEqualTo(claimId);
    assertThat(message.getSubmissionId()).isEqualTo(SUBMISSION_ID);
    assertThat(message.getType()).isEqualTo(type);
    assertThat(message.getSource()).isEqualTo("SYSTEM");
    assertThat(message.getDisplayMessage()).isEqualTo(displayMessage);
  }

  private static Stream<Arguments> validationTypeProvider() {
    return Stream.of(
        Arguments.of(ValidationMessageType.ERROR, CLAIM_ID_1, "Missing case reference"),
        Arguments.of(ValidationMessageType.WARNING, CLAIM_ID_2, "Missing UFN"));
  }
}
