package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Configuration class for setting up Aspect-Oriented Programming (AOP) in the application. This
 * class enables support for handling components marked with AspectJ's @Aspect annotation.
 *
 * <p>It is annotated with @Configuration, indicating that it is a source of bean definitions for
 * the application context. The @EnableAspectJAutoProxy annotation is used to enable support for
 * proxy-based AOP, allowing the use of advice, pointcuts, and other AOP features in the
 * application.
 */
@Configuration
@EnableAspectJAutoProxy
public class AopConfig {}
