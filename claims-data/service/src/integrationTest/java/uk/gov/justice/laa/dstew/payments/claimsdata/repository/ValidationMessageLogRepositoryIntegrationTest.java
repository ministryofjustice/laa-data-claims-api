package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_2_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;

import java.util.List;
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
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ValidationMessageWithClaimDetailsProjection;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("ValidationMessageLogRepository Integration Test")
public class ValidationMessageLogRepositoryIntegrationTest extends AbstractIntegrationTest {

  @BeforeEach
  void setup() {
    seedValidationMessagesData();
  }

  @Test
  @DisplayName("Should count distinct claim IDs by submission ID")
  void shouldCountDistinctClaimIdsBySubmissionId() {
    long count =
        validationMessageLogRepository.countDistinctClaimIdsBySubmissionIdAndType(
            SUBMISSION_1_ID, null);
    assertThat(count).isEqualTo(2L);
  }

  @ParameterizedTest(name = "Should find validation messages for type={0}")
  @MethodSource("validationTypeProvider")
  @DisplayName("Should find validation messages by type")
  void shouldFindValidationMessagesByType(
      ValidationMessageType type, UUID claimId, String displayMessage) {
    Pageable pageable = PageRequest.of(0, 10);

    ValidationMessageLog probe = new ValidationMessageLog();
    probe.setSubmissionId(SUBMISSION_1_ID);
    probe.setClaimId(claimId);
    probe.setType(type);
    probe.setSource("SYSTEM");

    Page<ValidationMessageLog> result =
        validationMessageLogRepository.findAll(Example.of(probe), pageable);

    assertThat(result.getTotalElements()).isEqualTo(1);
    var message = result.getContent().getFirst();
    assertThat(message.getClaimId()).isEqualTo(claimId);
    assertThat(message.getSubmissionId()).isEqualTo(SUBMISSION_1_ID);
    assertThat(message.getType()).isEqualTo(type);
    assertThat(message.getSource()).isEqualTo("SYSTEM");
    assertThat(message.getDisplayMessage()).isEqualTo(displayMessage);
  }

  @Test
  @DisplayName("Should count by claim ID by error")
  void shouldCountByClaimIdError() {
    var result =
        validationMessageLogRepository.countAllByClaimIdAndType(
            CLAIM_1_ID, ValidationMessageType.ERROR);

    assertThat(result).isEqualTo(1);
  }

  @Test
  @DisplayName("Should count by claim ID by warning")
  void shouldCountByClaimIdWarning() {
    var result =
        validationMessageLogRepository.countAllByClaimIdAndType(
            CLAIM_2_ID, ValidationMessageType.WARNING);

    assertThat(result).isEqualTo(1);
  }

  private static Stream<Arguments> validationTypeProvider() {
    return Stream.of(
        Arguments.of(ValidationMessageType.ERROR, CLAIM_1_ID, "Missing case reference"),
        Arguments.of(ValidationMessageType.WARNING, CLAIM_2_ID, "Missing UFN"));
  }

  @Test
  @DisplayName(
      "findWithClaimDetailsByFilters returns claim UFN and client details for a claim-linked message")
  void findWithClaimDetailsByFilters_returnsClaimDetails() {
    Pageable pageable = PageRequest.of(0, 10);

    // CLAIM_1_ID has uniqueFileNumber=UFN_123 and a Client with forename=Alice, surname=Smith,
    // UCN=UCN_111 (seeded via seedValidationMessagesData → seedClaimsData → createClaimsTestData)
    Page<ValidationMessageWithClaimDetailsProjection> result =
        validationMessageLogRepository.findWithClaimDetailsByFilters(
            SUBMISSION_1_ID, CLAIM_1_ID, null, null, pageable);

    assertThat(result.getTotalElements()).isEqualTo(1);
    ValidationMessageWithClaimDetailsProjection proj = result.getContent().getFirst();
    assertThat(proj.getClaimId()).isEqualTo(CLAIM_1_ID);
    assertThat(proj.getUniqueFileNumber()).isEqualTo("UFN_123");
    assertThat(proj.getClientForename()).isEqualTo("Alice");
    assertThat(proj.getClientSurname()).isEqualTo("Smith");
    assertThat(proj.getUniqueClientNumber()).isEqualTo("UCN_111");
  }

  @Test
  @DisplayName("findWithClaimDetailsByFilters filters by type")
  void findWithClaimDetailsByFilters_filtersByType() {
    Pageable pageable = PageRequest.of(0, 10);

    Page<ValidationMessageWithClaimDetailsProjection> errors =
        validationMessageLogRepository.findWithClaimDetailsByFilters(
            SUBMISSION_1_ID, null, ValidationMessageType.ERROR, null, pageable);
    assertThat(errors.getTotalElements()).isEqualTo(1);
    assertThat(errors.getContent().getFirst().getType()).isEqualTo(ValidationMessageType.ERROR);

    Page<ValidationMessageWithClaimDetailsProjection> warnings =
        validationMessageLogRepository.findWithClaimDetailsByFilters(
            SUBMISSION_1_ID, null, ValidationMessageType.WARNING, null, pageable);
    assertThat(warnings.getTotalElements()).isEqualTo(1);
    assertThat(warnings.getContent().getFirst().getType()).isEqualTo(ValidationMessageType.WARNING);
  }

  @Test
  @DisplayName("findWithClaimDetailsByFilters returns all messages when filters are null")
  void findWithClaimDetailsByFilters_withNullFilters_returnsAll() {
    Pageable pageable = PageRequest.of(0, 10);

    Page<ValidationMessageWithClaimDetailsProjection> result =
        validationMessageLogRepository.findWithClaimDetailsByFilters(
            SUBMISSION_1_ID, null, null, null, pageable);

    assertThat(result.getTotalElements()).isEqualTo(2);
  }

  @Test
  @DisplayName("Should persist messageCode for FSP messages with ERROR type")
  void shouldPersistMessageCodeForFspErrorMessage() {
    UUID submissionId = SUBMISSION_1_ID;
    UUID claimId = CLAIM_1_ID;
    String messageCode = "ERRALL1";

    ValidationMessageLog fspErrorMessage = new ValidationMessageLog();
    fspErrorMessage.setId(Uuid7.timeBasedUuid());
    fspErrorMessage.setSubmissionId(submissionId);
    fspErrorMessage.setClaimId(claimId);
    fspErrorMessage.setType(ValidationMessageType.ERROR);
    fspErrorMessage.setSource("FSP");
    fspErrorMessage.setDisplayMessage("Enter a valid Fee Code.");
    fspErrorMessage.setTechnicalMessage("Fee Code is invalid");
    fspErrorMessage.setMessageCode(messageCode);

    validationMessageLogRepository.save(fspErrorMessage);

    Pageable pageable = PageRequest.of(0, 10);
    Page<ValidationMessageWithClaimDetailsProjection> result =
        validationMessageLogRepository.findWithClaimDetailsByFilters(
            submissionId, claimId, ValidationMessageType.ERROR, "FSP", pageable);

    assertThat(result.getTotalElements()).isEqualTo(1);
    ValidationMessageWithClaimDetailsProjection retrieved = result.getContent().getFirst();
    assertThat(retrieved.getMessageCode()).isEqualTo(messageCode);
    assertThat(retrieved.getSource()).isEqualTo("FSP");
    assertThat(retrieved.getType()).isEqualTo(ValidationMessageType.ERROR);
  }

  @Test
  @DisplayName("Should persist messageCode for FSP messages with WARNING type")
  void shouldPersistMessageCodeForFspWarningMessage() {
    UUID submissionId = SUBMISSION_1_ID;
    UUID claimId = CLAIM_2_ID;
    String messageCode = "WARFAM1";

    ValidationMessageLog fspWarningMessage = new ValidationMessageLog();
    fspWarningMessage.setId(Uuid7.timeBasedUuid());
    fspWarningMessage.setSubmissionId(submissionId);
    fspWarningMessage.setClaimId(claimId);
    fspWarningMessage.setType(ValidationMessageType.WARNING);
    fspWarningMessage.setSource("FSP");
    fspWarningMessage.setDisplayMessage(
        "The claim exceeds the Escape Case Threshold. "
            + "An Escape Case Claim must be submitted for further costs to be paid. ");
    fspWarningMessage.setTechnicalMessage("Claim exceeds the Escape Case Threshold.");
    fspWarningMessage.setMessageCode(messageCode);

    validationMessageLogRepository.save(fspWarningMessage);

    Pageable pageable = PageRequest.of(0, 10);
    Page<ValidationMessageWithClaimDetailsProjection> result =
        validationMessageLogRepository.findWithClaimDetailsByFilters(
            submissionId, claimId, ValidationMessageType.WARNING, "FSP", pageable);

    assertThat(result.getTotalElements()).isEqualTo(1);
    ValidationMessageWithClaimDetailsProjection retrieved = result.getContent().getFirst();
    assertThat(retrieved.getMessageCode()).isEqualTo(messageCode);
    assertThat(retrieved.getSource()).isEqualTo("FSP");
    assertThat(retrieved.getType()).isEqualTo(ValidationMessageType.WARNING);
  }

  @Test
  @DisplayName("Should have null messageCode for non-FSP messages")
  void shouldHaveNullMessageCodeForNonFspMessages() {
    Pageable pageable = PageRequest.of(0, 10);

    Page<ValidationMessageWithClaimDetailsProjection> result =
        validationMessageLogRepository.findWithClaimDetailsByFilters(
            SUBMISSION_1_ID, CLAIM_1_ID, ValidationMessageType.ERROR, "SYSTEM", pageable);

    assertThat(result.getTotalElements()).isEqualTo(1);
    ValidationMessageWithClaimDetailsProjection retrieved = result.getContent().getFirst();
    assertThat(retrieved.getMessageCode()).isNull();
    assertThat(retrieved.getSource()).isEqualTo("SYSTEM");
  }

  @Test
  @DisplayName(
      "Should retain distinct messages with same display_message but different messageCode")
  void shouldRetainDistinctMessagesWithSameDisplayMessageButDifferentCode() {
    UUID submissionId = SUBMISSION_1_ID;
    UUID claimId = CLAIM_1_ID;
    String identicalDisplayMessage = "FSP validation issue";

    // Create two FSP messages with same display text but different codes
    ValidationMessageLog fspMessage1 = new ValidationMessageLog();
    fspMessage1.setId(Uuid7.timeBasedUuid());
    fspMessage1.setSubmissionId(submissionId);
    fspMessage1.setClaimId(claimId);
    fspMessage1.setType(ValidationMessageType.ERROR);
    fspMessage1.setSource("FSP");
    fspMessage1.setDisplayMessage(identicalDisplayMessage);
    fspMessage1.setTechnicalMessage("Case start date not found");
    fspMessage1.setMessageCode("ERRIA2");

    ValidationMessageLog fspMessage2 = new ValidationMessageLog();
    fspMessage2.setId(Uuid7.timeBasedUuid());
    fspMessage2.setSubmissionId(submissionId);
    fspMessage2.setClaimId(claimId);
    fspMessage2.setType(ValidationMessageType.ERROR);
    fspMessage2.setSource("FSP");
    fspMessage2.setDisplayMessage(identicalDisplayMessage);
    fspMessage2.setTechnicalMessage("Invalid Case start date");
    fspMessage2.setMessageCode("ERRIA1");

    validationMessageLogRepository.saveAll(List.of(fspMessage1, fspMessage2));

    Pageable pageable = PageRequest.of(0, 10);
    Page<ValidationMessageWithClaimDetailsProjection> result =
        validationMessageLogRepository.findWithClaimDetailsByFilters(
            submissionId, claimId, ValidationMessageType.ERROR, "FSP", pageable);

    // Both messages should be retrieved and distinguishable by their unique messageCode
    assertThat(result.getTotalElements()).isEqualTo(2);
    var codes =
        result.getContent().stream()
            .map(ValidationMessageWithClaimDetailsProjection::getMessageCode)
            .toList();
    assertThat(codes).contains("ERRIA1", "ERRIA2");
  }

  @Test
  @DisplayName("Should have null messageCode for FSP message with WARNING type when not provided")
  void shouldHaveNullMessageCodeForFspMessageWhenNotProvided() {
    UUID submissionId = SUBMISSION_1_ID;
    UUID claimId = CLAIM_2_ID;

    ValidationMessageLog fspWarningNoCode = new ValidationMessageLog();
    fspWarningNoCode.setId(Uuid7.timeBasedUuid());
    fspWarningNoCode.setSubmissionId(submissionId);
    fspWarningNoCode.setClaimId(claimId);
    fspWarningNoCode.setType(ValidationMessageType.WARNING);
    fspWarningNoCode.setSource("FSP");
    fspWarningNoCode.setDisplayMessage("Warning without code");
    fspWarningNoCode.setTechnicalMessage("Technical details for FSP warning");
    // messageCode not set, should be null

    validationMessageLogRepository.save(fspWarningNoCode);

    Pageable pageable = PageRequest.of(0, 10);
    Page<ValidationMessageWithClaimDetailsProjection> result =
        validationMessageLogRepository.findWithClaimDetailsByFilters(
            submissionId, claimId, ValidationMessageType.WARNING, "FSP", pageable);

    assertThat(result.getTotalElements()).isEqualTo(1);
    ValidationMessageWithClaimDetailsProjection retrieved = result.getContent().getFirst();
    assertThat(retrieved.getMessageCode()).isNull();
  }
}
