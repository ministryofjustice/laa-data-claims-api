package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Slf4j
class FlywayDiagnosticIntegrationTest {

  @Autowired private Flyway flyway;

  @Test
  void printAllMigrations() {
    MigrationInfoService info = flyway.info();

    log.info("=== VVVV ALL MIGRATIONS ===");
    for (MigrationInfo migration : info.all()) {
      log.info(
          "Version: %s, Description: %s, State: %s%n",
          migration.getVersion(), migration.getDescription(), migration.getState());
    }

    log.info("\n=== VVVV PENDING MIGRATIONS ===");
    for (MigrationInfo migration : info.pending()) {
      log.info(
          "Version: %s, Description: %s%n", migration.getVersion(), migration.getDescription());
    }

    log.info("\n=== VVVV CURRENT MIGRATION ===");
    MigrationInfo current = info.current();
    if (current != null) {
      log.info("Version: %s, Description: %s%n", current.getVersion(), current.getDescription());
    } else {
      log.info("No current migration applied yet.");
    }
  }
}
