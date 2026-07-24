package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ChangeSource;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationCode;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentValidationError;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.DiffEntry;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence.AmendmentChangeDetector;

@ExtendWith(MockitoExtension.class)
@DisplayName("AmendmentNoChangeValidationStep")
class AmendmentNoChangeValidationStepTest {

  @Mock private AmendmentChangeDetector changeDetector;

  @InjectMocks private AmendmentNoChangeValidationStep step;

  private final ClaimAmendmentState state = ClaimAmendmentState.builder().build();

  @Test
  @DisplayName("no detected changes yields a single fatal 204 no-change error")
  void noChangesYieldsFatalNoContentError() {
    when(changeDetector.detectChanges(state)).thenReturn(List.of());

    List<ClaimAmendmentValidationError> errors = step.validate(state);

    assertThat(errors).hasSize(1);
    ClaimAmendmentValidationError error = errors.get(0);
    assertThat(error.getCode())
        .isEqualTo(ClaimAmendmentValidationCode.NO_AMENDMENT_CHANGES_SUBMITTED.toString());
    assertThat(error.isFatal()).isTrue();
    assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  @DisplayName("at least one detected change passes with no errors")
  void detectedChangesPasses() {
    when(changeDetector.detectChanges(state))
        .thenReturn(
            List.of(new DiffEntry("client.clientSurname", ChangeSource.REQUESTED, "Old", "New")));

    assertThat(step.validate(state)).isEmpty();
  }
}
