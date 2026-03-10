package uk.gov.justice.laa.dstew.payments.claimsdata.aop;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Explicit definition of a primary data source. This is necessary to instruct Spring which data
 * source to use to save claims data.
 */
@Configuration
@Profile("!test")
public class PrimaryDataSourceConfig {

  @Bean
  @Primary
  @ConfigurationProperties("spring.datasource")
  public DataSourceProperties primaryDataSourceProperties() {
    return new DataSourceProperties();
  }

  @Bean(name = "primaryDataSource")
  @Primary
  @ConfigurationProperties("spring.datasource.hikari")
  public HikariDataSource primaryDataSource(
      @Qualifier("primaryDataSourceProperties") DataSourceProperties props) {

    return props.initializeDataSourceBuilder().type(HikariDataSource.class).build();
  }
}
