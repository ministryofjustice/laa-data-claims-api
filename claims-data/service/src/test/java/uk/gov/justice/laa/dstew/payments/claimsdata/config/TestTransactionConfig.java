package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class TestTransactionConfig {

  @Bean
  public PlatformTransactionManager transactionManager() {
    return new BasicTransactionManager();
  }

  @Bean
  public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
    return new TransactionTemplate(transactionManager);
  }
}
