package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/** A no-op transaction manager for tests that simulates commit/rollback behavior. */
public class BasicTransactionManager extends AbstractPlatformTransactionManager {

  @Override
  protected Object doGetTransaction() {
    return new Object();
  }

  @Override
  protected void doBegin(Object transaction, TransactionDefinition definition) {
    // No-op
  }

  @Override
  protected void doCommit(DefaultTransactionStatus status) {
    // No-op
  }

  @Override
  protected void doRollback(DefaultTransactionStatus status) {
    // No-op
  }
}
