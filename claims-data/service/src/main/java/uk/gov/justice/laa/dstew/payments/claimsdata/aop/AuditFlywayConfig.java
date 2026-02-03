package uk.gov.justice.laa.dstew.payments.claimsdata.aop;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

/** Config to run Flyway migrations against the audit database. */
@Configuration
public class AuditFlywayConfig {

  /**
   * Runs Flyway migrations against the audit database at startup. Use a separate locations path and
   * schema for JaVers to keep SQL distinct.
   */
  @Bean(initMethod = "migrate", defaultCandidate = false)
  @DependsOn("auditDataSource")
  public Flyway flywayAudit(@Qualifier("auditDataSource") DataSource auditDs) {
    return Flyway.configure()
        .dataSource(auditDs)
        .schemas("javers")
        .locations("classpath:db/migration-audit")
        .baselineOnMigrate(true) // useful if schema may already exist
        .load();
  }
}
