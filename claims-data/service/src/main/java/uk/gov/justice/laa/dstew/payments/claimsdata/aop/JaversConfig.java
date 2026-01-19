package uk.gov.justice.laa.dstew.payments.claimsdata.aop;

import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Javers config to turn on/off auditing on. */
@Configuration
@ConditionalOnProperty(name = "javers.enabled", havingValue = "true", matchIfMissing = true)
public class JaversConfig {

  @Bean
  public Javers javers() {
    return JaversBuilder.javers().build();
  }
}
