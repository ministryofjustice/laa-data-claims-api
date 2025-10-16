package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

/** Adjust pageable resolution to return all results when no pagination parameters are supplied. */
@Configuration
public class PaginationConfig {

  @Bean
  public PageableHandlerMethodArgumentResolverCustomizer pageableResolverCustomizer() {
    return resolver -> resolver.setFallbackPageable(Pageable.unpaged());
  }
}
