package uk.gov.justice.laa.dstew.payments.claimsdata.util;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Utility class for running something after the transaction commits. */
public class TransactionalPublisher {

  /**
   * Registers a callback to be executed after the current transaction successfully commits. If no
   * transaction is active, the callback runs immediately.
   *
   * @param action the {@link Runnable} to execute after commit
   */
  public static void runAfterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              action.run();
            }
          });
    } else {
      action.run(); // fallback if no transaction
    }
  }
}
