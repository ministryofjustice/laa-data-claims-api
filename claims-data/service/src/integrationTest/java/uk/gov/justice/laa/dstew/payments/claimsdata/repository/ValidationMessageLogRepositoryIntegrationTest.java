package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_1_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_2_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.CLAIM_4_ID;
import static uk.gov.justice.laa.dstew.payments.claimsdata.util.ClaimsDataTestUtil.SUBMISSION_1_ID;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uk.gov.justice.laa.dstew.payments.claimsdata.controller.AbstractIntegrationTest;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ValidationMessageLog;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.ValidationMessageType;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.projection.ValidationMessageWithClaimDetailsProjection;

@TestInstance(Lifecycle.PER_CLASS)
@DisplayName("ValidationMessageLogRepository Integration Test")
class ValidationMessageLogRepositoryIntegrationTest extends AbstractIntegrationTest {

  @BeforeEach
  void setup() {
    seedValidationMessagesData();
  }

  @Test
  @DisplayName("Should count distinct claim IDs by submission ID")
  void shouldCountDistinctClaimIdsBySubmissionId() {
    assertThat(
            validationMessageLogRepository.countDistinctClaimIdsBySubmissionIdAndType(
                SUBMISSION_1_ID, null))
        .isEqualTo(2L);
  }

  @Test
  @DisplayName("Should count current warning messages by claim ID")
  void shouldCountByClaimIdWarning() {
    assertThat(
            validationMessageLogRepository.countAllByClaimIdAndType(
                CLAIM_2_ID, ValidationMessageType.WARNING))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("Should treat supersededByVersion=0 as current and ignore superseded FSP warnings")
  void shouldTreatZeroSentinelAsCurrentWarning() {
    ValidationMessageLog currentWarning = buildFspWarning("current-warning", CLAIM_1_ID, 0L);
    ValidationMessageLog supersededWarning = buildFspWarning("old-warning", CLAIM_1_ID, 8L);
    validationMessageLogRepository.saveAll(List.of(currentWarning, supersededWarning));

    assertThat(
            validationMessageLogRepository.countAllByClaimIdAndType(
                CLAIM_1_ID, ValidationMessageType.WARNING))
        .isEqualTo(1);

    Pageable pageable = PageRequest.of(0, 10);
    List<ValidationMessageWithClaimDetailsProjection> result =
        validationMessageLogRepository
            .findWithClaimDetailsByFilters(
                SUBMISSION_1_ID, CLAIM_1_ID, ValidationMessageType.WARNING, "FSP", pageable)
            .getContent();

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getDisplayMessage()).isEqualTo("current-warning");
  }

  @Test
  @DisplayName("Should exclude superseded warnings when the message type filter is omitted")
  void shouldExcludeSupersededWarningsWithoutTypeFilter() {
    validationMessageLogRepository.saveAll(
        List.of(
            buildFspWarning("current-warning", CLAIM_1_ID, 0L),
            buildFspWarning("superseded-warning", CLAIM_1_ID, 8L),
            buildFspWarning("only-superseded-warning", CLAIM_4_ID, 8L)));

    List<ValidationMessageWithClaimDetailsProjection> messages =
        validationMessageLogRepository
            .findWithClaimDetailsByFilters(SUBMISSION_1_ID, null, null, null, PageRequest.of(0, 10))
            .getContent();

    assertThat(messages)
        .extracting(ValidationMessageWithClaimDetailsProjection::getDisplayMessage)
        .containsExactlyInAnyOrder("Missing case reference", "Missing UFN", "current-warning");
    assertThat(validationMessageLogRepository.countAllByClaimIdAndType(CLAIM_1_ID, null))
        .isEqualTo(2);
    assertThat(validationMessageLogRepository.countAllByClaimIdAndType(CLAIM_4_ID, null)).isZero();
    assertThat(
            validationMessageLogRepository.countDistinctClaimIdsBySubmissionIdAndType(
                SUBMISSION_1_ID, null))
        .isEqualTo(2);
    Map<UUID, Long> warningCounts =
        validationMessageLogRepository
            .countWarningsByClaimIdsAndType(List.of(CLAIM_1_ID, CLAIM_2_ID, CLAIM_4_ID), null)
            .stream()
            .collect(
                Collectors.toMap(
                    projection -> projection.getClaimId(),
                    projection -> projection.getWarningCount()));
    assertThat(warningCounts)
        .containsEntry(CLAIM_1_ID, 2L)
        .containsEntry(CLAIM_2_ID, 1L)
        .doesNotContainKey(CLAIM_4_ID);
  }

  @Test
  @DisplayName("Should supersede only current FSP warnings when a later repricing succeeds")
  void supersedeCurrentByClaimIdAndSource_updatesOnlyCurrentWarnings() {
    ValidationMessageLog currentWarning = buildFspWarning("current-warning", CLAIM_1_ID, 0L);
    ValidationMessageLog historicalWarning = buildFspWarning("historical-warning", CLAIM_1_ID, 9L);
    validationMessageLogRepository.saveAll(List.of(currentWarning, historicalWarning));

    int updated =
        validationMessageLogRepository.supersedeCurrentByClaimIdAndSource(CLAIM_1_ID, "FSP", 12L);

    assertThat(updated).isEqualTo(1);
    assertThat(
            validationMessageLogRepository.countAllByClaimIdAndType(
                CLAIM_1_ID, ValidationMessageType.WARNING))
        .isEqualTo(0);
    assertThat(
            validationMessageLogRepository.findAllById(
                List.of(currentWarning.getId(), historicalWarning.getId())))
        .extracting(ValidationMessageLog::getSupersededByVersion)
        .containsExactlyInAnyOrder(12L, 9L);
  }

  private ValidationMessageLog buildFspWarning(
      String displayMessage, UUID claimId, Long supersededByVersion) {
    ValidationMessageLog log = new ValidationMessageLog();
    log.setId(UUID.randomUUID());
    log.setSubmissionId(SUBMISSION_1_ID);
    log.setClaimId(claimId);
    log.setType(ValidationMessageType.WARNING);
    log.setSource("FSP");
    log.setDisplayMessage(displayMessage);
    log.setTechnicalMessage(displayMessage);
    log.setSupersededByVersion(supersededByVersion);
    return log;
  }
}
