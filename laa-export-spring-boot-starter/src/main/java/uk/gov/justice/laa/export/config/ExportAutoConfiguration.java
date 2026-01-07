package uk.gov.justice.laa.export.config;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.justice.laa.export.ExportAuditSink;
import uk.gov.justice.laa.export.ExportRegistry;
import uk.gov.justice.laa.export.ExportRequestValidator;
import uk.gov.justice.laa.export.ExportSecurity;
import uk.gov.justice.laa.export.ExportService;
import uk.gov.justice.laa.export.audit.LogExportAuditSink;
import uk.gov.justice.laa.export.csv.CsvRowWriter;
import uk.gov.justice.laa.export.csv.JacksonCsvRowWriter;
import uk.gov.justice.laa.export.registry.DefaultExportRegistry;
import uk.gov.justice.laa.export.security.DefaultExportSecurity;
import uk.gov.justice.laa.export.security.PermitAllExportSecurity;
import uk.gov.justice.laa.export.service.DefaultExportRequestValidator;
import uk.gov.justice.laa.export.service.DefaultExportService;
import uk.gov.justice.laa.export.tx.TransactionalStreamRunner;

/**
 * Auto-configuration for export components.
 */
@AutoConfiguration
@EnableConfigurationProperties(LaaExportsProperties.class)
@ConditionalOnProperty(prefix = "laa.springboot.starter.exports", name = "enabled", havingValue = "true")
public class ExportAutoConfiguration {

  /**
   * Creates the CsvMapper used for streaming CSV output.
   */
  @Bean
  @ConditionalOnMissingBean
  public CsvMapper csvMapper() {
    CsvMapper mapper = new CsvMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
    return mapper;
  }

  @Bean
  @ConditionalOnMissingBean
  public CsvRowWriter csvRowWriter(CsvMapper mapper) {
    return new JacksonCsvRowWriter(mapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public TransactionalStreamRunner transactionalStreamRunner(PlatformTransactionManager ptm) {
    return new TransactionalStreamRunner(ptm);
  }

  @Bean
  @ConditionalOnMissingBean
  public ExportRequestValidator exportRequestValidator() {
    return new DefaultExportRequestValidator();
  }

  @Bean
  @ConditionalOnMissingBean
  public ExportAuditSink exportAuditSink() {
    return new LogExportAuditSink();
  }

  @Bean
  @ConditionalOnMissingBean
  public ExportRegistry exportRegistry(
      ApplicationContext applicationContext, LaaExportsProperties properties) {
    return new DefaultExportRegistry(applicationContext, properties);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
  public ExportSecurity exportSecurity() {
    return new DefaultExportSecurity();
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnMissingClass("org.springframework.security.core.context.SecurityContextHolder")
  public ExportSecurity permitAllExportSecurity() {
    return new PermitAllExportSecurity();
  }

  @Bean
  @ConditionalOnMissingBean
  public ExportService exportService(
      ExportRegistry registry,
      ExportRequestValidator validator,
      TransactionalStreamRunner txRunner,
      CsvRowWriter csvWriter,
      ExportAuditSink audit,
      ExportSecurity security) {
    return new DefaultExportService(registry, validator, txRunner, csvWriter, audit, security);
  }

}
