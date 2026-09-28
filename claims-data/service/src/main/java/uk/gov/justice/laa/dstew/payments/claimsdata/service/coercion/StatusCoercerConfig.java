package uk.gov.justice.laa.dstew.payments.claimsdata.service.coercion;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.ClaimsApiProperties;

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
@EnableConfigurationProperties(ClaimsApiProperties.class)
class StatusCoercerConfig {

  @Bean
  StatusCoercer statusCoercer(ClaimsApiProperties properties) {
    return properties.getValidatedPendingApproval().isEnabled()
        ? StatusCoercer.NO_OP
        : new LegacyStatusCoercer();
  }
}
