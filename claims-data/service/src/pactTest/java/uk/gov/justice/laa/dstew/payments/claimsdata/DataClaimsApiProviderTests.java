package uk.gov.justice.laa.dstew.payments.claimsdata;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
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

  // A new bulk submission request
  @State("the system is ready to process a valid bulk submission")
  public void setupBulkSubmissionState() {
    // This is where you set up your test data.
    // For example, ensuring a user exists or a specific record is in the DB.
    // If no setup is needed for this specific test, you can leave it empty.
    System.out.println("Setting up state: the system is ready to process a valid bulk submission");
  }

  //@BeforeEach
  //void setUp(PactVerificationContext pactVerificationContext){
  //  pactVerificationContext.setTarget(new WebFluxSpring7Target(r));
  //}

  @TestTemplate
  @ExtendWith(PactVerificationInvocationContextProvider.class)
  void pactVerificationTestTemplate(PactVerificationContext context ) {
    // This has been set as the auth token in application.yaml
    //httpRequest.addHeader("Authorization", "00000000-0000-0000-0000-000000000000");
    context.verifyInteraction();
  }
}
