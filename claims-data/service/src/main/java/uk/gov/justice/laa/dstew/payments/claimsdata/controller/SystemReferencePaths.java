package uk.gov.justice.laa.dstew.payments.claimsdata.controller;

/**
 * Single source of truth for the system reference lookup paths used by non-generated code (tests,
 * documentation references). The route itself is owned by the OpenAPI specification and applied to
 * the generated API interface; this holder simply mirrors it so a path pivot only needs to be made
 * in the spec and here.
 */
public final class SystemReferencePaths {

  private SystemReferencePaths() {
    throw new IllegalStateException("Cannot instantiate SystemReferencePaths class");
  }

  /** Base path for read-only system reference lookups. */
  public static final String BASE = "/api/v1/system/references";

  /** Amendment Requested By (with nested Amendment Reasons) reference lookup. */
  public static final String AMENDMENT_REQUESTED_BY = BASE + "/amendment-requested-by";
}
