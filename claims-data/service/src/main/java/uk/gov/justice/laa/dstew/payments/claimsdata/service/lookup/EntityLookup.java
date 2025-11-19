package uk.gov.justice.laa.dstew.payments.claimsdata.service.lookup;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

public class EntityLookup {

  public static <T, E extends RuntimeException> T requireEntity(
      JpaRepository<T, UUID> repository, UUID id, Function<String, Supplier<E>> exception) {
    return repository
        .findById(id)
        .orElseThrow(() -> exception.apply(String.format("No entity found with id: %s", id)).get());
  }
}
