package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.SqsTestConfig;

/** This is used to isolate the common configuration for integration testing in a single class. */
@ActiveProfiles("test")
@SpringBootTest
@Import(SqsTestConfig.class)
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

  @ServiceConnection
  static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:latest");

  static {
    postgresContainer.start();
  }
}
