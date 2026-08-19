package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;

/**
 * Default implementation of {@link SubmissionRepositoryCustom}.
 *
 * <p>This class supplies the hand-written body for {@link SubmissionRepositoryCustom#insertNew}. It
 * is <strong>not</strong> a redundant second file: Spring Data's generated proxy for {@link
 * SubmissionRepository} delegates fragment calls to a bean chosen purely by the {@code
 * <FragmentInterface>Impl} naming convention, so the {@code Impl} suffix here is load-bearing.
 * Renaming this class (or dropping the {@link SubmissionRepositoryCustom} interface it implements)
 * would break the wiring and the repository would no longer expose {@code insertNew}. See {@link
 * SubmissionRepositoryCustom} for the full rationale on why the interface + {@code Impl} pair is
 * required.
 *
 * <p>The implementation deliberately lives in the repository layer (rather than the service) so
 * that the JPA-specific insert-only semantics - {@code persist} instead of {@code merge}, plus an
 * explicit {@code flush} to surface duplicate keys eagerly - stay encapsulated behind the
 * repository abstraction.
 */
public class SubmissionRepositoryCustomImpl implements SubmissionRepositoryCustom {

  /** PostgreSQL SQLSTATE for {@code unique_violation} (covers primary-key conflicts). */
  private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

  @PersistenceContext private EntityManager entityManager;

  @Override
  public void insertNew(Submission submission) {
    try {
      // persist() schedules an INSERT (never a merge/upsert); flush() forces it to run now so a
      // duplicate primary key is detected immediately by the database unique constraint.
      entityManager.persist(submission);
      entityManager.flush();
    } catch (PersistenceException ex) {
      // Classify the failure so callers can react precisely: a unique/primary-key violation becomes
      // a DuplicateKeyException (translated to a 409 by the service), while any other integrity
      // failure (e.g. NOT NULL / foreign key) stays a generic DataIntegrityViolationException so it
      // is not misreported as a duplicate. Translating here also makes the behaviour deterministic
      // regardless of whether Spring's own exception translation is applied to this fragment.
      if (isUniqueViolation(ex)) {
        throw new DuplicateKeyException("Submission already exists: " + submission.getId(), ex);
      }
      throw new DataIntegrityViolationException(
          "Failed to insert submission " + submission.getId(), ex);
    }
  }

  /**
   * Walks the cause chain looking for a {@link SQLException} whose SQLSTATE indicates a unique
   * constraint violation.
   *
   * @param throwable the top-level persistence exception
   * @return {@code true} if the underlying cause is a unique/primary-key violation
   */
  private static boolean isUniqueViolation(Throwable throwable) {
    for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sqlException
          && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())) {
        return true;
      }
    }
    return false;
  }
}
