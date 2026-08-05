package uk.gov.justice.laa.dstew.payments.claimsdata.client;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationRequest;
import uk.gov.justice.laa.fee.scheme.model.FeeCalculationResponse;
import uk.gov.justice.laa.fee.scheme.model.FeeDetailsResponseV2;

/**
 * REST service interface for verifying category of law against a fee code and calculating fee
 * totals. This interface communicates with the Fee Scheme Platform API.
 */
@HttpExchange(value = "/api", accept = MediaType.APPLICATION_JSON_VALUE)
public interface FeeSchemePlatformRestClient {

  /**
   * Get the fee details corresponding to the provided fee code. Can return the following HTTP
   * statuses:
   *
   * <ul>
   *   <li>200 - Success
   *   <li>400 - Bad request.
   *   <li>401 - Unauthorized.
   *   <li>403 - Forbidden.
   *   <li>404 - Category code not found.
   *   <li>500 - Internal Server Error.
   * </ul>
   *
   * @param feeCode The fee code
   * @return The corresponding category of law
   */
  @GetExchange("/v2/fee-details/{feeCode}")
  ResponseEntity<FeeDetailsResponseV2> getFeeDetails(final @PathVariable String feeCode);

  /**
   * Get the category of law corresponding to the provided fee code. Can return the following HTTP
   * statuses:
   *
   * <ul>
   *   <li>200 - Success
   *   <li>400 - Bad request.
   *   <li>401 - Unauthorized.
   *   <li>403 - Forbidden.
   *   <li>404 - Category code not found.
   *   <li>500 - Internal Server Error.
   * </ul>
   *
   * @param feeCalculationRequest The details required for the fee calculation
   * @return The result of the fee calculation
   */
  @PostExchange("/v1/fee-calculation")
  ResponseEntity<FeeCalculationResponse> calculateFee(
      final @RequestBody FeeCalculationRequest feeCalculationRequest);
}
