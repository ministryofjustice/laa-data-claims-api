package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.support.TransactionTemplate;
import uk.gov.justice.laa.dstew.payments.claimsdata.config.TestTransactionConfig;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.SubmissionEventPublisherService;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {TestTransactionConfig.class})
public class TransactionalPublisherTest {

  @Autowired private TransactionTemplate transactionTemplate;

  @MockBean private SubmissionEventPublisherService submissionEventPublisherService;

  @Test
  public void testAfterCommitCallbackExecutes() {
    // Arrange
    UUID submissionId = UUID.randomUUID();

    // Act: Execute inside a real transaction that commits
    transactionTemplate.execute(
        status -> {
          TransactionalPublisher.runAfterCommit(
              () -> submissionEventPublisherService.publishSubmissionValidationEvent(submissionId));
          return null; // no return value needed
        });

    // Assert: Verify the callback executed after commit
    Mockito.verify(submissionEventPublisherService, Mockito.times(1))
        .publishSubmissionValidationEvent(Mockito.eq(submissionId));
  }
}
