package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Register the validating pageable resolver to centralise pageable parameter validation. */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    // Add our validating resolver before the default so it takes precedence.
    resolvers.addFirst(new ValidatingPageableHandlerMethodArgumentResolver());
  }
}
