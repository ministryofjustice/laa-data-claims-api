package uk.gov.justice.laa.dstew.payments.claimsdata.service.coercion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * TEMPORARY (DSTEW-2173): supplies the no-op {@link StatusCoercer} bean when the
 * validated-pending-approval lifecycle is enabled.
 *
 * <p>Only an explicit {@code true} enables the no-op implementation. Invalid and missing property
 * values select {@link LegacyStatusCoercer}, so configuration mistakes cannot activate the new
 * lifecycle accidentally.
 *
 * <p>To remove once the validated-pending-approval lifecycle no longer needs gating: delete this
 * class (see {@link StatusCoercer} for the full removal checklist).
 */
@Configuration
class StatusCoercerConfig {

  @Bean
  StatusCoercer statusCoercer(
      @Value("${laa.claims.api.validated-pending-approval.enabled:false}") String enabled) {
    return Boolean.parseBoolean(enabled.trim()) ? StatusCoercer.NO_OP : new LegacyStatusCoercer();
  }
}
