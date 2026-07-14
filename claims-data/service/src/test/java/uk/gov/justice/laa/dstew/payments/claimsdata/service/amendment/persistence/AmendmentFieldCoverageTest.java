package uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimAmendmentState;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.ClaimStateSnapshot;
import uk.gov.justice.laa.dstew.payments.claimsdata.dto.amendment.DiffEntry;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Claim;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimCase;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.ClaimSummaryFee;
import uk.gov.justice.laa.dstew.payments.claimsdata.entity.Client;
import uk.gov.justice.laa.dstew.payments.claimsdata.service.amendment.fee.FeeSchemeRequestField;

/**
 * Drift-detection tests for the amendment field mappings.
 *
 * <p>The amendment machinery maintains hand-written field lists in two places that must stay in
 * lock-step with the JPA entities:
 *
 * <ul>
 *   <li>{@link AmendmentChangeDetector} - which fields appear in the audit {@code diff};
 *   <li>the per-table updaters ({@link AmendmentClaimUpdater}, {@link AmendmentClientUpdater},
 *       {@link AmendmentClaimCaseUpdater}, {@link AmendmentClaimSummaryFeeUpdater}) - which fields
 *       are actually persisted.
 * </ul>
 *
 * <p>If a new amendable column is added to an entity but not wired into these lists, amendment data
 * is silently lost (not persisted) or the audit diff is incomplete - with no compile error. These
 * tests reflect over each amendable entity and fail when a field is neither represented nor on an
 * explicit, documented ignore list, so the omission is caught at build time. When a genuinely
 * non-amendable field is added, add it to the relevant ignore list (with the reason), which forces
 * a conscious decision.
 *
 * <p>Scope is the four provider-amendable entities. The FSP {@code calculated_fee_detail} is
 * deliberately excluded: it is FSP-owned (DSTEW-1762), not provider-amendable.
 */
@DisplayName("Amendment field-coverage (drift detection) Tests")
class AmendmentFieldCoverageTest {

  /**
   * A provider-amendable entity, its diff-identifier prefix and the fields intentionally excluded
   * from amendment (identity, audit, optimistic-lock, lifecycle and relationship fields).
   */
  private record AmendableEntity<E>(Class<E> type, String prefix, Set<String> ignoredFields) {}

  // Fields common to every entity that are never provider-amendable.
  private static final Set<String> COMMON_IGNORED_FIELDS =
      Set.of("id", "createdByUserId", "createdOn", "updatedByUserId", "updatedOn");

  private static final Set<String> CLAIM_IGNORED_FIELDS =
      union(
          COMMON_IGNORED_FIELDS,
          Set.of(
              // Relationships (owned/managed separately, not scalar amendable columns).
              "submission",
              "claimCase",
              "client",
              "claimSummaryFee",
              "calculatedFeeDetails",
              // Lifecycle / status - governed by the amendment flow, not copied from the payload.
              "status",
              "isAmended",
              "hasAssessment",
              // Optimistic-lock guard.
              "version",
              // System dedup/matching field - explicitly not provider-amendable.
              "matchedClaimId"));

  private static final Set<String> CLIENT_IGNORED_FIELDS =
      union(COMMON_IGNORED_FIELDS, Set.of("claim"));

  private static final Set<String> CLAIM_CASE_IGNORED_FIELDS =
      union(COMMON_IGNORED_FIELDS, Set.of("claim"));

  private static final Set<String> CLAIM_SUMMARY_FEE_IGNORED_FIELDS =
      union(COMMON_IGNORED_FIELDS, Set.of("claim"));

  private static final AmendableEntity<Claim> CLAIM_ENTITY =
      new AmendableEntity<>(Claim.class, "claim", CLAIM_IGNORED_FIELDS);

  private static final AmendableEntity<Client> CLIENT_ENTITY =
      new AmendableEntity<>(Client.class, "client", CLIENT_IGNORED_FIELDS);

  private static final AmendableEntity<ClaimCase> CLAIM_CASE_ENTITY =
      new AmendableEntity<>(ClaimCase.class, "claimCase", CLAIM_CASE_IGNORED_FIELDS);

  private static final AmendableEntity<ClaimSummaryFee> CLAIM_SUMMARY_FEE_ENTITY =
      new AmendableEntity<>(
          ClaimSummaryFee.class, "claimSummaryFee", CLAIM_SUMMARY_FEE_IGNORED_FIELDS);

  private static final List<AmendableEntity<?>> AMENDABLE_ENTITIES =
      List.of(CLAIM_ENTITY, CLIENT_ENTITY, CLAIM_CASE_ENTITY, CLAIM_SUMMARY_FEE_ENTITY);

  @Nested
  @DisplayName("AmendmentChangeDetector diff coverage")
  class DiffCoverage {

    // Derived through the detector's public API (not a test-only accessor): drive detectChanges
    // with a before/after pair in which every scalar snapshot field differs, then collect the
    // identifiers it emits. This is exactly the set of fields the detector compares.
    private final Set<String> detectorIdentifiers = deriveDetectorIdentifiers();

    @Test
    @DisplayName("every amendable entity field is represented in the diff detector")
    void everyAmendableFieldIsInTheDiffDetector() {
      for (AmendableEntity<?> entity : AMENDABLE_ENTITIES) {
        assertThat(missingIdentifiers(entity, detectorIdentifiers))
            .as(
                "%s field(s) not represented in AmendmentChangeDetector - add them to "
                    + "claimStateFields(), or to this test's ignore list if not amendable",
                entity.type().getSimpleName())
            .isEmpty();
      }
    }

    @Test
    @DisplayName("every diff-detector identifier maps to a real, amendable entity field")
    void noStaleDiffIdentifiers() {
      Map<String, AmendableEntity<?>> byPrefix = new HashMap<>();
      AMENDABLE_ENTITIES.forEach(entity -> byPrefix.put(entity.prefix(), entity));

      List<String> stale = new ArrayList<>();
      for (String identifier : detectorIdentifiers) {
        int dot = identifier.indexOf('.');
        String prefix = identifier.substring(0, dot);
        String fieldName = identifier.substring(dot + 1);
        AmendableEntity<?> entity = byPrefix.get(prefix);
        if (entity == null || !amendableFieldNames(entity).contains(fieldName)) {
          stale.add(identifier);
        }
      }

      assertThat(stale)
          .as("diff identifier(s) that no longer map to an amendable entity field")
          .isEmpty();
    }
  }

  @Nested
  @DisplayName("Updater copy coverage")
  class UpdaterCoverage {

    @Test
    @DisplayName("AmendmentClaimUpdater copies every amendable claim field")
    void claimUpdaterCopiesAllFields() {
      assertUpdaterCopiesAllAmendableFields(
          CLAIM_ENTITY, new AmendmentClaimUpdater()::applyAmendedFields);
    }

    @Test
    @DisplayName("AmendmentClientUpdater copies every amendable client field")
    void clientUpdaterCopiesAllFields() {
      assertUpdaterCopiesAllAmendableFields(
          CLIENT_ENTITY, new AmendmentClientUpdater()::applyAmendedFields);
    }

    @Test
    @DisplayName("AmendmentClaimCaseUpdater copies every amendable claim-case field")
    void claimCaseUpdaterCopiesAllFields() {
      assertUpdaterCopiesAllAmendableFields(
          CLAIM_CASE_ENTITY, new AmendmentClaimCaseUpdater()::applyAmendedFields);
    }

    @Test
    @DisplayName("AmendmentClaimSummaryFeeUpdater copies every amendable claim-summary-fee field")
    void claimSummaryFeeUpdaterCopiesAllFields() {
      assertUpdaterCopiesAllAmendableFields(
          CLAIM_SUMMARY_FEE_ENTITY, new AmendmentClaimSummaryFeeUpdater()::applyAmendedFields);
    }
  }

  @Nested
  @DisplayName("External impact-check alignment")
  class ExternalImpactCheckAlignment {

    // The exact identifiers the change detector emits - the canonical diff vocabulary.
    private final Set<String> detectorIdentifiers = deriveDetectorIdentifiers();

    @Test
    @DisplayName("every FeeSchemeRequestField diff identifier is emitted by the change detector")
    void feeSchemeIdentifiersAreKnownToTheDetector() {
      Set<String> feeSchemeDiffIdentifiers =
          Arrays.stream(FeeSchemeRequestField.values())
              .map(FeeSchemeRequestField::getDiffFieldIdentifier)
              .filter(java.util.Objects::nonNull)
              .collect(Collectors.toSet());

      assertThat(detectorIdentifiers)
          .as(
              "FeeSchemeRequestField diff identifiers drifted from AmendmentChangeDetector - "
                  + "a mapped claim field's diff identifier no longer matches an emitted identifier")
          .containsAll(feeSchemeDiffIdentifiers);
    }
  }

  @Nested
  @DisplayName("Coverage-logic self-test (meta)")
  class CoverageLogicSelfTest {

    /**
     * A synthetic stand-in entity, used only to prove the detection primitive itself flags an
     * unmapped field. It exercises the same reflection path as the real entities - a static field
     * and an ignore-listed field must be skipped, leaving exactly the two "amendable" fields - so a
     * regression in {@link #amendableFieldNames}/{@link #missingIdentifiers} would fail here
     * without needing to perturb a production entity.
     */
    @SuppressWarnings("unused") // fields are inspected reflectively, never read directly
    private static final class SampleEntity {
      private static final String STATIC_CONSTANT = "must be ignored (static)";
      private String mappedField;
      private String unmappedField;
      private String deliberatelyIgnoredField;
    }

    private final AmendableEntity<SampleEntity> sampleEntity =
        new AmendableEntity<>(SampleEntity.class, "sample", Set.of("deliberatelyIgnoredField"));

    @Test
    @DisplayName("amendable fields exclude static and ignore-listed fields")
    void amendableFieldsExcludeStaticAndIgnored() {
      assertThat(amendableFieldNames(sampleEntity))
          .containsExactlyInAnyOrder("mappedField", "unmappedField");
    }

    @Test
    @DisplayName("an amendable field absent from the covered set is reported as missing")
    void reportsUnmappedField() {
      Set<String> covered = Set.of("sample.mappedField"); // omits sample.unmappedField

      assertThat(missingIdentifiers(sampleEntity, covered)).containsExactly("sample.unmappedField");
    }

    @Test
    @DisplayName("nothing is reported when every amendable field is covered")
    void reportsNothingWhenAllCovered() {
      Set<String> covered = Set.of("sample.mappedField", "sample.unmappedField");

      assertThat(missingIdentifiers(sampleEntity, covered)).isEmpty();
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Builds a snapshot with a distinct non-null value for every amendable field of the entity, runs
   * the updater against a fresh entity instance, then asserts each amendable field was copied
   * through with that exact value. A field the updater forgets to copy stays at its default ({@code
   * null}) and fails the assertion.
   */
  private static <E> void assertUpdaterCopiesAllAmendableFields(
      AmendableEntity<E> entity, BiConsumer<E, ClaimStateSnapshot> updater) {
    List<String> fields = amendableFieldNames(entity);

    Object builder = invokeNoArg(ClaimStateSnapshot.class, "builder");
    Map<String, Object> expected = new HashMap<>();
    int seed = 1;
    for (String name : fields) {
      Method builderMethod = singleArgMethod(builder.getClass(), name);
      Object value = sampleValue(builderMethod.getParameterTypes()[0], seed++);
      invoke(builder, builderMethod, value);
      expected.put(name, value);
    }
    ClaimStateSnapshot snapshot = (ClaimStateSnapshot) invokeNoArg(builder, "build");

    E instance = newInstance(entity.type());
    updater.accept(instance, snapshot);

    for (String name : fields) {
      assertThat(readField(instance, name))
          .as(
              "%s.%s was not copied by its amendment updater (amendment data would be lost)",
              entity.type().getSimpleName(), name)
          .isEqualTo(expected.get(name));
    }
  }

  private static List<String> amendableFieldNames(AmendableEntity<?> entity) {
    return Arrays.stream(entity.type().getDeclaredFields())
        .filter(field -> !Modifier.isStatic(field.getModifiers()))
        .filter(field -> !field.isSynthetic()) // e.g. JaCoCo's $jacocoData
        .map(Field::getName)
        .filter(name -> !entity.ignoredFields().contains(name))
        .toList();
  }

  /**
   * The amendable-field identifiers ({@code prefix.field}) of the entity that are absent from the
   * supplied {@code covered} set. The single detection primitive shared by the real diff-coverage
   * test and the meta test below.
   */
  private static List<String> missingIdentifiers(AmendableEntity<?> entity, Set<String> covered) {
    return amendableFieldNames(entity).stream()
        .map(name -> entity.prefix() + "." + name)
        .filter(identifier -> !covered.contains(identifier))
        .toList();
  }

  /**
   * Derives the set of field identifiers the {@link AmendmentChangeDetector} compares, using only
   * its public API: build a before/after pair where every scalar snapshot field differs, run {@link
   * AmendmentChangeDetector#detectChanges} and collect the emitted identifiers. Every scalar field
   * the detector inspects therefore surfaces (no reliance on a test-only accessor). The FSP fee
   * snapshots are left unset, so only the requested claim-state identifiers are produced.
   */
  private static Set<String> deriveDetectorIdentifiers() {
    List<Field> scalarFields = scalarSnapshotFields();

    Object beforeBuilder = invokeNoArg(ClaimStateSnapshot.class, "builder");
    Object afterBuilder = invokeNoArg(ClaimStateSnapshot.class, "builder");
    for (int i = 0; i < scalarFields.size(); i++) {
      Field snapshotField = scalarFields.get(i);
      Class<?> type = snapshotField.getType();
      Method beforeMethod = singleArgMethod(beforeBuilder.getClass(), snapshotField.getName());
      Method afterMethod = singleArgMethod(afterBuilder.getClass(), snapshotField.getName());
      // Even vs odd seeds guarantee the before/after values differ for every type (booleans flip
      // parity), so the detector emits an identifier for each field it compares.
      invoke(beforeBuilder, beforeMethod, sampleValue(type, 2 * i));
      invoke(afterBuilder, afterMethod, sampleValue(type, 2 * i + 1));
    }

    ClaimStateSnapshot before = (ClaimStateSnapshot) invokeNoArg(beforeBuilder, "build");
    ClaimStateSnapshot after = (ClaimStateSnapshot) invokeNoArg(afterBuilder, "build");
    ClaimAmendmentState state =
        ClaimAmendmentState.builder().beforeState(before).postAmendmentState(after).build();

    return new AmendmentChangeDetector()
        .detectChanges(state).stream().map(DiffEntry::fieldIdentifier).collect(Collectors.toSet());
  }

  /** The scalar {@link ClaimStateSnapshot} fields (those {@link #sampleValue} can populate). */
  private static List<Field> scalarSnapshotFields() {
    return Arrays.stream(ClaimStateSnapshot.class.getDeclaredFields())
        .filter(field -> !Modifier.isStatic(field.getModifiers()))
        .filter(field -> !field.isSynthetic())
        .filter(field -> SUPPORTED_SCALAR_TYPES.contains(field.getType()))
        .toList();
  }

  private static final Set<Class<?>> SUPPORTED_SCALAR_TYPES =
      Set.of(
          String.class,
          Integer.class,
          int.class,
          Long.class,
          long.class,
          Boolean.class,
          boolean.class,
          BigDecimal.class,
          LocalDate.class,
          UUID.class);

  private static Set<String> union(Set<String> a, Set<String> b) {
    return java.util.stream.Stream.concat(a.stream(), b.stream())
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static Object sampleValue(Class<?> type, int seed) {
    if (type == String.class) {
      return "value-" + seed;
    }
    if (type == Integer.class || type == int.class) {
      return seed;
    }
    if (type == Long.class || type == long.class) {
      return (long) seed;
    }
    if (type == Boolean.class || type == boolean.class) {
      return seed % 2 == 0;
    }
    if (type == BigDecimal.class) {
      return new BigDecimal(seed + ".00");
    }
    if (type == LocalDate.class) {
      return LocalDate.of(2020, Month.JANUARY, 1).plusDays(seed);
    }
    if (type == UUID.class) {
      return new UUID(0L, seed);
    }
    throw new IllegalStateException(
        "No sample value for type " + type.getName() + " - extend AmendmentFieldCoverageTest");
  }

  private static Method singleArgMethod(Class<?> owner, String name) {
    return Arrays.stream(owner.getMethods())
        .filter(method -> method.getName().equals(name) && method.getParameterCount() == 1)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No single-argument builder/setter '"
                        + name
                        + "' on "
                        + owner.getSimpleName()
                        + " - the entity field has no matching ClaimStateSnapshot field"));
  }

  private static Object readField(Object target, String name) {
    try {
      Field field = target.getClass().getDeclaredField(name);
      field.setAccessible(true);
      return field.get(target);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException("Cannot read field " + name, ex);
    }
  }

  private static <T> T newInstance(Class<T> type) {
    try {
      return type.getDeclaredConstructor().newInstance();
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException("Cannot instantiate " + type.getSimpleName(), ex);
    }
  }

  private static Object invokeNoArg(Object target, String methodName) {
    Class<?> owner = target instanceof Class<?> clazz ? clazz : target.getClass();
    Object receiver = target instanceof Class<?> ? null : target;
    Method method =
        Arrays.stream(owner.getMethods())
            .filter(
                candidate ->
                    candidate.getName().equals(methodName) && candidate.getParameterCount() == 0)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No no-arg method " + methodName));
    return invoke(receiver, method, null);
  }

  private static Object invoke(Object receiver, Method method, Object arg) {
    try {
      return arg == null ? method.invoke(receiver) : method.invoke(receiver, arg);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException("Cannot invoke " + method.getName(), ex);
    }
  }
}
