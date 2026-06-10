package uk.gov.justice.laa.dstew.payments.claimsdata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.AmendmentReasonReference;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.RequestedByReference;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.AmendmentReferenceMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.AmendmentReferenceMapperImpl;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.AmendmentRequestedByReferenceList;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.AmendmentReasonReferenceRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.RequestedByReferenceRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@ExtendWith(MockitoExtension.class)
@DisplayName("AmendmentReferenceService")
class AmendmentReferenceServiceTest {

  @Mock private RequestedByReferenceRepository requestedByReferenceRepository;
  @Mock private AmendmentReasonReferenceRepository amendmentReasonReferenceRepository;

  // Use the real generated MapStruct implementation rather than mocking the mapping.
  private final AmendmentReferenceMapper amendmentReferenceMapper =
      new AmendmentReferenceMapperImpl();

  private AmendmentReferenceService serviceWithRealMapper() {
    return new AmendmentReferenceService(
        requestedByReferenceRepository,
        amendmentReasonReferenceRepository,
        amendmentReferenceMapper);
  }

  private RequestedByReference requestedBy(String code, String label, int order) {
    return RequestedByReference.builder()
        .id(Uuid7.timeBasedUuid())
        .code(code)
        .displayLabel(label)
        .isActive(true)
        .displayOrder(order)
        .createdByUserId("test")
        .createdOn(Instant.now())
        .build();
  }

  private AmendmentReasonReference reason(
      String requestedByCode, String code, String label, int order) {
    return AmendmentReasonReference.builder()
        .id(Uuid7.timeBasedUuid())
        .requestedByCode(requestedByCode)
        .code(code)
        .displayLabel(label)
        .isActive(true)
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
      when(requestedByReferenceRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
          .thenReturn(List.of());
      when(amendmentReasonReferenceRepository
              .findByIsActiveTrueOrderByRequestedByCodeAscDisplayOrderAsc())
          .thenReturn(List.of());

      AmendmentRequestedByReferenceList result =
          serviceWithRealMapper().getAmendmentRequestedByReferences();

      assertThat(result.getRequestedBy()).isEmpty();
    }

    @Test
    @DisplayName("returns a single Requested By value with its reasons in order")
    void returnsSingleRequestedByWithItsReasons() {
      when(requestedByReferenceRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
          .thenReturn(List.of(requestedBy("PROVIDER", "Provider", 10)));
      when(amendmentReasonReferenceRepository
              .findByIsActiveTrueOrderByRequestedByCodeAscDisplayOrderAsc())
          .thenReturn(
              List.of(
                  reason("PROVIDER", "PROVIDER_ERROR", "Provider Error", 10),
                  reason("PROVIDER", "CASE_REOPENED_REBILLED", "Case re-opened", 20)));

      AmendmentRequestedByReferenceList result =
          serviceWithRealMapper().getAmendmentRequestedByReferences();

      assertThat(result.getRequestedBy()).hasSize(1);
      assertThat(result.getRequestedBy().get(0).getCode()).isEqualTo("PROVIDER");
      assertThat(result.getRequestedBy().get(0).getReasons())
          .extracting("code")
          .containsExactly("PROVIDER_ERROR", "CASE_REOPENED_REBILLED");
    }

    @Test
    @DisplayName("returns an empty reason list for a Requested By value that has no reasons")
    void requestedByWithNoReasonsReturnsEmptyReasonList() {
      when(requestedByReferenceRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
          .thenReturn(List.of(requestedBy("AUDITOR", "Auditor", 40)));
      when(amendmentReasonReferenceRepository
              .findByIsActiveTrueOrderByRequestedByCodeAscDisplayOrderAsc())
          .thenReturn(List.of());

      AmendmentRequestedByReferenceList result =
          serviceWithRealMapper().getAmendmentRequestedByReferences();

      assertThat(result.getRequestedBy()).hasSize(1);
      assertThat(result.getRequestedBy().get(0).getReasons()).isEmpty();
    }
  }

  @Nested
  @DisplayName("getAmendmentRequestedByReferences - party-scoped reasons")
  class PartyScopedReasons {

    @Test
    @DisplayName("nests each reason under only the Requested By value it is valid for")
    void scopesReasonsToTheirRequestedByValue() {
      when(requestedByReferenceRepository.findByIsActiveTrueOrderByDisplayOrderAsc())
          .thenReturn(
              List.of(
                  requestedBy("PROVIDER", "Provider", 10),
                  requestedBy("ASSURANCE", "Assurance", 20)));
      when(amendmentReasonReferenceRepository
              .findByIsActiveTrueOrderByRequestedByCodeAscDisplayOrderAsc())
          .thenReturn(
              List.of(
                  reason(
                      "ASSURANCE", "INCORRECT_MEANS_ASSESSMENT", "Incorrect Means Assessment", 10),
                  reason("ASSURANCE", "OTHER", "Other", 20),
                  reason("PROVIDER", "PROVIDER_ERROR", "Provider Error", 10)));

      AmendmentRequestedByReferenceList result =
          serviceWithRealMapper().getAmendmentRequestedByReferences();

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
