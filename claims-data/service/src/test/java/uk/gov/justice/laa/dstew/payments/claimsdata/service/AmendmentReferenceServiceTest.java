package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentReferenceData;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AmendmentReasonReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.RequestedByReferenceEntity;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.AmendmentReferenceMapperImpl;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AmendmentRequestedByReferenceList;
import uk.gov.justice.laa.dstew.payments.claimsdata.provider.AmendmentReferenceDataProvider;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.UUID7;

@ExtendWith(MockitoExtension.class)
@DisplayName("AmendmentReferenceService")
class AmendmentReferenceServiceTest {

  @Mock private AmendmentReferenceDataProvider amendmentReferenceDataProvider;

  // Use the real generated MapStruct implementation (spied) rather than mocking the mapping, so the
  // tests exercise the actual entity -> model mapping. Repos and mapper are injected by Mockito.
  @Spy private AmendmentReferenceMapperImpl amendmentReferenceMapper;

  @InjectMocks private AmendmentReferenceService service;

  private RequestedByReferenceEntity requestedBy(String code, String label, int order) {
    return requestedBy(code, label, order, true);
  }

  private RequestedByReferenceEntity requestedBy(
      String code, String label, int order, boolean active) {
    return RequestedByReferenceEntity.builder()
        .id(UUID7.timeBasedUuid())
        .code(code)
        .displayLabel(label)
        .isActive(active)
        .displayOrder(order)
        .createdByUserId("test")
        .createdOn(Instant.now())
        .build();
  }

  private AmendmentReasonReferenceEntity reason(
      String requestedByCode, String code, String label, int order) {
    return reason(requestedByCode, code, label, order, true);
  }

  private AmendmentReasonReferenceEntity reason(
      String requestedByCode, String code, String label, int order, boolean active) {
    return AmendmentReasonReferenceEntity.builder()
        .id(UUID7.timeBasedUuid())
        .requestedByCode(requestedByCode)
        .code(code)
        .displayLabel(label)
        .isActive(active)
        .displayOrder(order)
        .createdByUserId("test")
        .createdOn(Instant.now())
        .build();
  }

  @Nested
  @DisplayName("getAmendmentRequestedByReferences - list assembly")
  class ListAssembly {

    @Test
    @DisplayName("returns an empty list when there are no Requested By values")
    void returnsEmptyListWhenNoRequestedByValues() {
      when(amendmentReferenceDataProvider.getReferenceData())
          .thenReturn(new ClaimAmendmentReferenceData(List.of(), List.of()));

      AmendmentRequestedByReferenceList result = service.getAmendmentRequestedByReferences();

      assertThat(result.getRequestedBy()).isEmpty();
    }

    @Test
    @DisplayName("returns a single Requested By value with its reasons in order")
    void returnsSingleRequestedByWithItsReasons() {
      when(amendmentReferenceDataProvider.getReferenceData())
          .thenReturn(
              new ClaimAmendmentReferenceData(
                  List.of(requestedBy("PROVIDER", "Provider", 10)),
                  List.of(
                      reason("PROVIDER", "PROVIDER_ERROR", "Provider Error", 10),
                      reason("PROVIDER", "CASE_REOPENED_REBILLED", "Case re-opened", 20))));

      AmendmentRequestedByReferenceList result = service.getAmendmentRequestedByReferences();

      assertThat(result.getRequestedBy()).hasSize(1);
      assertThat(result.getRequestedBy().get(0).getCode()).isEqualTo("PROVIDER");
      assertThat(result.getRequestedBy().get(0).getReasons())
          .extracting("code")
          .containsExactly("PROVIDER_ERROR", "CASE_REOPENED_REBILLED");
    }

    @Test
    @DisplayName("returns an empty reason list for a Requested By value that has no reasons")
    void requestedByWithNoReasonsReturnsEmptyReasonList() {
      when(amendmentReferenceDataProvider.getReferenceData())
          .thenReturn(
              new ClaimAmendmentReferenceData(
                  List.of(requestedBy("AUDITOR", "Auditor", 40)), List.of()));

      AmendmentRequestedByReferenceList result = service.getAmendmentRequestedByReferences();

      assertThat(result.getRequestedBy()).hasSize(1);
      assertThat(result.getRequestedBy().get(0).getReasons()).isEmpty();
    }
  }

  @Nested
  @DisplayName("getAmendmentRequestedByReferences - inactive values")
  class InactiveValues {

    @Test
    @DisplayName("includes inactive values flagged is_active=false alongside active ones")
    void includesInactiveValuesFlaggedInactive() {
      when(amendmentReferenceDataProvider.getReferenceData())
          .thenReturn(
              new ClaimAmendmentReferenceData(
                  List.of(
                      requestedBy("PROVIDER", "Provider", 10, true),
                      requestedBy("LEGACY_PARTY", "Legacy Party", 20, false)),
                  List.of(
                      reason("PROVIDER", "PROVIDER_ERROR", "Provider Error", 10, true),
                      reason("PROVIDER", "OLD_REASON", "Old reason", 20, false))));

      AmendmentRequestedByReferenceList result = service.getAmendmentRequestedByReferences();

      assertThat(result.getRequestedBy())
          .extracting("code", "isActive")
          .containsExactly(tuple("PROVIDER", true), tuple("LEGACY_PARTY", false));
      assertThat(result.getRequestedBy().get(0).getReasons())
          .extracting("code", "isActive")
          .containsExactly(tuple("PROVIDER_ERROR", true), tuple("OLD_REASON", false));
    }
  }

  @Nested
  @DisplayName("getAmendmentRequestedByReferences - party-scoped reasons")
  class PartyScopedReasons {

    @Test
    @DisplayName("nests each reason under only the Requested By value it is valid for")
    void scopesReasonsToTheirRequestedByValue() {
      when(amendmentReferenceDataProvider.getReferenceData())
          .thenReturn(
              new ClaimAmendmentReferenceData(
                  List.of(
                      requestedBy("PROVIDER", "Provider", 10),
                      requestedBy("ASSURANCE", "Assurance", 20)),
                  List.of(
                      reason(
                          "ASSURANCE",
                          "INCORRECT_MEANS_ASSESSMENT",
                          "Incorrect Means Assessment",
                          10),
                      reason("ASSURANCE", "OTHER", "Other", 20),
                      reason("PROVIDER", "PROVIDER_ERROR", "Provider Error", 10))));

      AmendmentRequestedByReferenceList result = service.getAmendmentRequestedByReferences();

      assertThat(result.getRequestedBy())
          .extracting("code")
          .containsExactly("PROVIDER", "ASSURANCE");
      assertThat(result.getRequestedBy().get(0).getReasons())
          .extracting("code")
          .containsExactly("PROVIDER_ERROR");
      assertThat(result.getRequestedBy().get(1).getReasons())
          .extracting("code")
          .containsExactly("INCORRECT_MEANS_ASSESSMENT", "OTHER");
    }
  }
}
