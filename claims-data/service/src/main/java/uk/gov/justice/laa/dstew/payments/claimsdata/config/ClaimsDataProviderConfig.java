package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.justice.laa.dstew.payments.claims.validation.core.provider.ClaimsDataProvider;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.ClaimResultSetMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.mapper.SubmissionMapper;
import uk.gov.justice.laa.dstew.payments.claimsdata.provider.RepositoryClaimsDataProvider;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.ClaimRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.repository.SubmissionRepository;

/**
 * Spring configuration that registers {@link RepositoryClaimsDataProvider} as the {@link
 * ClaimsDataProvider} bean whenever no other {@link ClaimsDataProvider} is already present in the
 * application context.
 *
 * <p>This means:
 *
 * <ul>
 *   <li>Inside the Claims API service (which owns the DB) this bean is automatically active and
 *       queries the database directly via JPA — no HTTP call to itself is needed.
 *   <li>In an external service that has its own HTTP-based implementation (e.g. a REST client),
 *       that implementation takes precedence and this bean is silently skipped.
 * </ul>
 *
 * <p>To force the HTTP implementation even inside this service, simply declare a {@link
 * ClaimsDataProvider} bean elsewhere in your configuration; {@code @ConditionalOnMissingBean} will
 * then suppress this one.
 */
@Configuration
public class ClaimsDataProviderConfig {

  /**
   * Registers a {@link RepositoryClaimsDataProvider} as the {@link ClaimsDataProvider} bean.
   *
   * <p>Only active when no other {@link ClaimsDataProvider} bean is present in the application
   * context (see {@code @ConditionalOnMissingBean}).
   *
   * @param claimRepository JPA repository for {@code Claim} entities
   * @param submissionRepository JPA repository for {@code Submission} entities
   * @param claimResultSetMapper MapStruct mapper from {@code Page<Claim>} to {@link
   *     uk.gov.justice.laa.dstew.payments.claimsdata.model.ClaimResultSet}
   * @param submissionMapper MapStruct mapper from {@code Submission} to {@link
   *     uk.gov.justice.laa.dstew.payments.claimsdata.model.SubmissionBase}
   * @return a {@link RepositoryClaimsDataProvider} wired with the supplied repositories and mappers
   */
  @Bean
  @ConditionalOnMissingBean(ClaimsDataProvider.class)
  public ClaimsDataProvider claimsDataProvider(
      ClaimRepository claimRepository,
      SubmissionRepository submissionRepository,
      ClaimResultSetMapper claimResultSetMapper,
      SubmissionMapper submissionMapper) {

    return new RepositoryClaimsDataProvider(
        claimRepository, submissionRepository, claimResultSetMapper, submissionMapper);
  }
}
