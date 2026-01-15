package uk.gov.justice.laa.dstew.payments.claimsdata;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.spring.spring7.PactVerificationSpring7Provider;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Provider(value = "laa-data-claims-api")
@PactBroker
public class DataClaimsApiProviderTests extends AbstractProviderPactTests{


  @TestTemplate
  @ExtendWith(PactVerificationSpring7Provider.class)
  void pactVerificationTestTemplate(PactVerificationContext context) {
    context.verifyInteraction();
  }
}
