package uk.gov.justice.laa.export.tx;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs a stream within a read-only transaction.
 */
public final class TransactionalStreamRunner {
  private final TransactionTemplate tx;

  public TransactionalStreamRunner(PlatformTransactionManager ptm) {
    this.tx = new TransactionTemplate(ptm);
    this.tx.setReadOnly(true);
  }

  /**
   * Executes the supplied stream inside a read-only transaction.
   */
  public <T> void run(Supplier<Stream<T>> streamSupplier, Consumer<T> rowConsumer) {
    tx.executeWithoutResult(
        status -> {
          try (Stream<T> stream = streamSupplier.get()) {
            stream.forEach(rowConsumer);
          }
        });
  }
}
