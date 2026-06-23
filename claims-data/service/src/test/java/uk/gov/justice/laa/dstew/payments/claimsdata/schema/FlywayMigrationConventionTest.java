package uk.gov.justice.laa.dstew.payments.claimsdata.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Enforces that Flyway migration scripts added after V{@value #ENFORCEMENT_FROM_VERSION} use a
 * schema-qualified name in every {@code CREATE TABLE} statement, e.g. {@code CREATE TABLE
 * claims.foo} rather than the unqualified {@code CREATE TABLE foo}.
 *
 * <p>Pre-existing migrations at or below the threshold version are exempt: they predate this
 * convention and must not be retroactively modified (Flyway checksums would fail if the files were
 * touched).
 */
@DisplayName("Flyway migration SQL convention checks")
class FlywayMigrationConventionTest {

  private static final Path MIGRATIONS_DIR = Path.of("src/main/resources/db/migration");

  /**
   * Migrations whose version number is at or below this value are exempt from the rule. Raise this
   * value when introducing a new convention so that only future scripts are subject to it.
   */
  private static final int ENFORCEMENT_FROM_VERSION = 40;

  private static final Pattern CREATE_TABLE_PATTERN =
      Pattern.compile(
          "\\bCREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(\\S+)", Pattern.CASE_INSENSITIVE);

  private static final Pattern SCHEMA_QUALIFIED_PATTERN =
      Pattern.compile("^(claims|javers)\\..+", Pattern.CASE_INSENSITIVE);

  private static final Pattern MIGRATION_VERSION_PATTERN =
      Pattern.compile("^V(\\d+)__", Pattern.CASE_INSENSITIVE);

  private static final Pattern BOUNDED_VARCHAR_PATTERN =
      Pattern.compile("\\bVARCHAR\\s*\\(\\s*\\d+\\s*\\)", Pattern.CASE_INSENSITIVE);

  @Test
  @DisplayName(
      "Every CREATE TABLE in migrations above V"
          + ENFORCEMENT_FROM_VERSION
          + " must be schema-qualified (claims. or javers.)")
  void createTableStatementsAreSchemaQualified() throws IOException {
    List<String> violations = new ArrayList<>();

    try (var files = Files.walk(MIGRATIONS_DIR)) {
      files
          .filter(p -> p.toString().endsWith(".sql"))
          .filter(p -> versionOf(p) > ENFORCEMENT_FROM_VERSION)
          .forEach(
              path -> {
                try {
                  String sql = stripLineComments(Files.readString(path));
                  Matcher matcher = CREATE_TABLE_PATTERN.matcher(sql);
                  while (matcher.find()) {
                    String tableName = matcher.group(1);
                    if (!SCHEMA_QUALIFIED_PATTERN.matcher(tableName).matches()) {
                      violations.add(path.getFileName() + " → CREATE TABLE " + tableName);
                    }
                  }
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
    }

    assertThat(violations)
        .as(
            """
        The following CREATE TABLE statements are missing a schema prefix. \
        Use 'claims.table_name' or 'javers.table_name':
        %s""",
            String.join("\n", violations))
        .isEmpty();
  }

  @Test
  @DisplayName(
      "String columns in migrations above V"
          + ENFORCEMENT_FROM_VERSION
          + " must not use bounded varchar lengths")
  void stringColumnsDoNotUseBoundedVarcharLengths() throws IOException {
    List<String> violations = new ArrayList<>();

    try (var files = Files.walk(MIGRATIONS_DIR)) {
      files
          .filter(p -> p.toString().endsWith(".sql"))
          .filter(p -> versionOf(p) > ENFORCEMENT_FROM_VERSION)
          .forEach(
              path -> {
                try {
                  String sql = stripLineComments(Files.readString(path));
                  Matcher matcher = BOUNDED_VARCHAR_PATTERN.matcher(sql);
                  while (matcher.find()) {
                    violations.add(path.getFileName() + " → " + matcher.group());
                  }
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
    }

    assertThat(violations)
        .as(
            """
        The following migrations use bounded varchar lengths. \
        Use TEXT or unbounded VARCHAR unless a database-enforced length is explicitly required:
        %s""",
            String.join("\n", violations))
        .isEmpty();
  }

  private static int versionOf(Path path) {
    Matcher matcher = MIGRATION_VERSION_PATTERN.matcher(path.getFileName().toString());
    return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
  }

  private static String stripLineComments(String sql) {
    return sql.replaceAll("--[^\n]*", "");
  }
}
