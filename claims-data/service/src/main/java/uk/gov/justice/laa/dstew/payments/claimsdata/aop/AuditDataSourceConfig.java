package uk.gov.justice.laa.dstew.payments.claimsdata.aop;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * This uses the additional datasource for the audit database. Binds to a separate prefix and mark
 * it as not the default candidate so it won’t interfere with the main JPA datasource
 */
@Configuration
@Profile("!test")
public class AuditDataSourceConfig {

  @Bean(name = "auditDataSourceProperties", defaultCandidate = false)
  @ConfigurationProperties("audit.datasource")
  public DataSourceProperties auditDataSourceProperties() {
    return new DataSourceProperties();
  }

  @Bean(name = "auditDataSource")
  @ConfigurationProperties("audit.datasource.hikari")
  public HikariDataSource auditDataSource(
      @Qualifier("auditDataSourceProperties") DataSourceProperties props) {
    return props.initializeDataSourceBuilder().type(HikariDataSource.class).build();
  }
}
