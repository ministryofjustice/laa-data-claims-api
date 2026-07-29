package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;

/**
 * Custom repository fragment providing insert-only persistence for {@link Submission}.
 *
 * <p>The standard {@link JpaRepository#save(Object) save} method performs a JPA {@code merge} for
 * entities with an assigned (client supplied) primary key, which silently turns a duplicate create
 * into an update (upsert). This fragment instead issues a true SQL {@code INSERT} so the database
 * primary-key constraint is the single source of truth for uniqueness.
 *
 * <h2>Why this interface is necessary (and cannot be collapsed into a single class)</h2>
 *
 * <p>{@link SubmissionRepository} is never implemented by hand - at runtime Spring Data generates a
 * dynamic <em>proxy</em> for it. That proxy can only expose methods declared on an interface the
 * repository extends; it has no visibility of arbitrary standalone classes. Spring Data's "custom
 * implementation" contract therefore requires two collaborating types:
 *
 * <ol>
 *   <li>this <strong>fragment interface</strong>, which declares {@link #insertNew(Submission)} and
 *       is extended by {@link SubmissionRepository}, so the method becomes part of the repository's
 *       public API and can be invoked as {@code submissionRepository.insertNew(...)};
 *   <li>a matching <strong>implementation class</strong> ({@link SubmissionRepositoryCustomImpl}),
 *       which Spring Data discovers <em>by naming convention</em> ({@code <FragmentInterface>Impl})
 *       and wires in behind the proxy to service calls to this method.
 * </ol>
 *
 * <p>If the interface were removed and only the {@code Impl} kept, {@link SubmissionRepository}
 * would have nothing to extend, {@code submissionRepository.insertNew(...)} would not compile, and
 * the {@code Impl} would become an orphan bean the proxy never delegates to. There is deliberately
 * no "single custom class inside a repository" option in Spring Data - the interface plus {@code
 * Impl} pair is the irreducible minimum for adding hand-written behaviour to a generated
 * repository. The only ways to avoid the pair are to leave the repository abstraction altogether
 * (for example a native {@code @Modifying} insert query on the repository, or injecting an {@link
 * jakarta.persistence.EntityManager} into the service) - both considered and rejected in favour of
 * keeping insert-only persistence encapsulated in the repository.
 */
public interface SubmissionRepositoryCustom {

  /**
   * Insert a brand-new submission using {@code EntityManager.persist} followed by an immediate
   * {@code flush}. The flush forces the {@code INSERT} to hit the database within the current
   * transaction so that a duplicate primary key is rejected synchronously: a unique/primary-key
   * violation is surfaced as a {@link org.springframework.dao.DuplicateKeyException}, while any
   * other integrity failure surfaces as a generic {@link
   * org.springframework.dao.DataIntegrityViolationException} (so it is not misreported as a
   * duplicate) rather than being deferred to commit.
   *
   * @param submission the fully populated, not-yet-persisted submission entity
   */
  void insertNew(Submission submission);
}
