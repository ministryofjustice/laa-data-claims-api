package uk.gov.justice.laa.dstew.payments.claimsdata.aop;

import javax.sql.DataSource;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.repository.sql.ConnectionProvider;
import org.javers.repository.sql.DialectName;
import org.javers.repository.sql.JaversSqlRepository;
import org.javers.repository.sql.SqlRepositoryBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This class constructs JaversSqlRepository and then builds a Javers instance registering that
 * repository. Basically it replicates the same as the Javers starter, but with the audit
 * datasource: Javers repository + connection provider.
 */
@Configuration
public class JaversAuditConfig {

  @Bean
  public ConnectionProvider javersConnectionProvider(@Qualifier("auditDataSource") DataSource ds) {
    return ds::getConnection;
  }

  /**
   * Constructs a {@link JaversSqlRepository} using SqlRepositoryBuilder.sqlRepository().
   *
   * @param cp the JDBC connection provider
   * @param schema the name of the schema to use in the connection
   * @return a JaversSqlRepository
   */
  @Bean
  public JaversSqlRepository javersSqlRepository(
      ConnectionProvider cp, @Value("${javers.sqlSchema:javers}") String schema) {

    return SqlRepositoryBuilder.sqlRepository()
        .withDialect(DialectName.POSTGRES)
        .withSchema(schema)
        .withConnectionProvider(cp)
        .withSchemaManagementEnabled(true)
        .build();
  }

  @Bean
  public Javers javers(JaversSqlRepository repo) {
    return JaversBuilder.javers().registerJaversRepository(repo).build();
  }
}
