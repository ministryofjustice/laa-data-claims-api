package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import uk.gov.justice.laa.dstew.payments.claimsdata.model.xml.TrimmingStringDeserializer;

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
    objectMapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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
    return XmlMapper.builder()
        .addModule(trimmingModule())
        .build();
  }

  @Bean
  public Module trimmingModule() {
    SimpleModule module = new SimpleModule();
    module.addDeserializer(String.class, new TrimmingStringDeserializer());
    return module;
  }
}
