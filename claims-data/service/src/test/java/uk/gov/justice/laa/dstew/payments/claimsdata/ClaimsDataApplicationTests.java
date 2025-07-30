package uk.gov.justice.laa.dstew.payments.claimsdata;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class ClaimsDataApplicationTests {

  @Container
  @ServiceConnection
  public static final PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:latest")
      .withDatabaseName("testdb")
      .withUsername("testuser")
      .withPassword("testpassword");

  @Test
  void contextLoads() {
    // empty due to only testing context load
  }
}
