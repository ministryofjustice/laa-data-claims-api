package uk.gov.justice.laa.dstew.payments.claimsdata.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Submission;
import uk.gov.justice.laa.dstew.payments.claimsdata.util.Uuid7;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubmissionRepositoryCustomImpl Unit Tests")
class SubmissionRepositoryCustomImplTest {

  private static final String UNIQUE_VIOLATION = "23505";
  private static final String NOT_NULL_VIOLATION = "23502";

  @Mock private EntityManager entityManager;

  @InjectMocks private SubmissionRepositoryCustomImpl repository;

  private Submission newSubmission() {
    return Submission.builder().id(Uuid7.timeBasedUuid()).build();
  }

  @Test
  @DisplayName("insertNew persists and flushes the entity on the happy path")
  void insertNew_shouldPersistAndFlush() {
    Submission submission = newSubmission();

    repository.insertNew(submission);

    verify(entityManager).persist(submission);
    verify(entityManager).flush();
  }

  @Test
  @DisplayName(
      "insertNew maps a unique/primary-key violation (SQLSTATE 23505) to DuplicateKeyException")
  void insertNew_whenUniqueViolation_shouldThrowDuplicateKeyException() {
    Submission submission = newSubmission();
    UUID id = submission.getId();
    PersistenceException persistenceException =
        new PersistenceException("constraint", new SQLException("dup", UNIQUE_VIOLATION));
    doThrow(persistenceException).when(entityManager).flush();

    assertThatThrownBy(() -> repository.insertNew(submission))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining(id.toString())
        .hasCause(persistenceException);
  }

  @Test
  @DisplayName(
      "insertNew maps a non-unique integrity violation (e.g. NOT NULL, SQLSTATE 23502) to a plain DataIntegrityViolationException")
  void insertNew_whenNonUniqueViolation_shouldThrowDataIntegrityViolationException() {
    Submission submission = newSubmission();
    PersistenceException persistenceException =
        new PersistenceException("constraint", new SQLException("null", NOT_NULL_VIOLATION));
    doThrow(persistenceException).when(entityManager).flush();

    assertThatThrownBy(() -> repository.insertNew(submission))
        .isInstanceOf(DataIntegrityViolationException.class)
        .isNotInstanceOf(DuplicateKeyException.class)
        .hasCause(persistenceException);
  }

  @Test
  @DisplayName(
      "insertNew maps a persistence failure with no SQL cause to a plain DataIntegrityViolationException")
  void insertNew_whenNoSqlCause_shouldThrowDataIntegrityViolationException() {
    Submission submission = newSubmission();
    PersistenceException persistenceException = new PersistenceException("opaque failure");
    doThrow(persistenceException).when(entityManager).flush();

    assertThatThrownBy(() -> repository.insertNew(submission))
        .isInstanceOf(DataIntegrityViolationException.class)
        .isNotInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName("insertNew detects a unique violation nested deep in the cause chain")
  void insertNew_whenUniqueViolationNestedDeep_shouldThrowDuplicateKeyException() {
    Submission submission = newSubmission();
    SQLException sqlException = new SQLException("dup", UNIQUE_VIOLATION);
    RuntimeException hibernateWrapper = new RuntimeException("hibernate constraint", sqlException);
    PersistenceException persistenceException =
        new PersistenceException("jpa boundary", hibernateWrapper);
    doThrow(persistenceException).when(entityManager).flush();

    assertThatThrownBy(() -> repository.insertNew(submission))
        .isInstanceOf(DuplicateKeyException.class);
  }
}
