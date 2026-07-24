package uk.gov.justice.laa.dstew.payments.claimsdata.exception;

import java.util.List;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.confirmation.ClaimConfirmationError;

/** Raised when claim-level errors prevent a draft submission from being confirmed. */
@Getter
public class ConfirmationValidationException extends ClaimsDataException {

  private final List<ClaimConfirmationError> claimReports;

  public ConfirmationValidationException(List<ClaimConfirmationError> claimReports) {
    super("Submission cannot be confirmed", HttpStatus.BAD_REQUEST);
    this.claimReports = List.copyOf(claimReports);
  }
}
