package uk.gov.justice.laa.dstew.payments.claimsdata.service.lookup;

import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * A generic interface that centralises the common lookup logic for claims api entities of type
 * {@code T} using a {@link JpaRepository}.
 *
 * <p>This interface provides a default method to retrieve an entity by its {@link UUID} identifier,
 * and throws a custom exception of type {@code E} if the entity is not found.
 *
 * @param <T> the type of the entity
 * @param <R> the type of the repository, extending {@code JpaRepository<T, UUID>}
 * @param <E> the type of the exception to be thrown when the entity is not found, must extend
 *     {@link RuntimeException}
 */
public interface AbstractEntityLookup<
    T, R extends JpaRepository<T, UUID>, E extends RuntimeException> {

  /**
   * Provides the repository {@code R} to be used by the default lookup method.
   *
   * @return the repository {@code R} used to retrieve entities {@code T} (never {@code null})
   */
  R lookup();

  /**
   * Provides a supplier for the exception to be thrown when an entity is not found.
   *
   * <p>The supplier should construct an instance of {@code E} using the provided message.
   *
   * @param message the exception message
   * @return a supplier that returns an instance of {@code E}
   */
  Supplier<E> entityNotFoundSupplier(String message);

  /**
   * Retrieves an existing {@code T} by its identifier or throws an exception {@code E} if none
   * exists.
   *
   * @param id the unique identifier of the entity to retrieve
   * @return the matching entity if found
   * @throws E if the entity is not found
   */
  default T requireEntity(UUID id) {
    return lookup()
        .findById(id)
        .orElseThrow(
            () -> entityNotFoundSupplier(String.format("No entity found with id: %s", id)).get());
  }
}
