package uk.gov.justice.laa.dstew.payments.claimsdata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the Data Stewardship Payments application. */
@SpringBootApplication
public class ClaimsDataApplication {
  /**
   * The application main method.
   *
   * @param args the application arguments.
   */
  public static void main(String[] args) {
    SpringApplication.run(ClaimsDataApplication.class, args);
  }
}
