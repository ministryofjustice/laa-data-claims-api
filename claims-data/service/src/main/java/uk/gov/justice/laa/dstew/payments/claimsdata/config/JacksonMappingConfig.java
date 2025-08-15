package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Configuration for Jackson mapping beans. */
@Configuration
public class JacksonMappingConfig {
  /**
   * Provides an {@link ObjectMapper} bean.
   *
   * @return an {@link ObjectMapper} bean.
   */
  @Bean
  @Primary
  public ObjectMapper objectMapper() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    return objectMapper;
  }

  /**
   * Provides an {@link CsvMapper} bean.
   *
   * @return an {@link CsvMapper} bean.
   */
  @Bean
  public CsvMapper csvMapper() {
    return new CsvMapper();
  }

  /**
   * Provides an {@link XmlMapper} bean.
   *
   * @return an {@link XmlMapper} bean.
   */
  @Bean
  public XmlMapper xmlMapper() {
    return new XmlMapper();
  }
}
