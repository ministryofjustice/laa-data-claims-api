package uk.gov.justice.laa.dstew.payments.claimsdata.service.audit;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Auditing of external API calls have the capacity to also audit the associated claims id and/or
 * submission id.
 *
 * <p>The {@code ExternalApiCallContext} class facilitates the capturing of these Ids and in a
 * thread safe manner stores then for when/if they are used in the external API.
 */
public final class ExternalApiCallContext implements Supplier<ExternalApiCallContext.Ids> {
  /** Identifiers for the current binding. Both may be {@code null}. */
  public record Ids(UUID claimId, UUID submissionId) {
    static final Ids NONE = new Ids(null, null);
  }

  @Override
  public Ids get() {
    RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
    if (attrs == null) {
      return Ids.NONE;
    }
    Map<String, String> vars = pathVariables(attrs);
    return new Ids(uuid(vars, "claim-id", "claimId"), uuid(vars, "submission-id", "submissionId"));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, String> pathVariables(RequestAttributes attrs) {
    Object vars =
        attrs.getAttribute(
            HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
    return vars instanceof Map<?, ?> map ? (Map<String, String>) map : Map.of();
  }

  private static UUID uuid(Map<String, String> vars, String... names) {
    for (String name : names) {
      String value = vars.get(name);
      if (value != null) {
        try {
          return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
          // Not a UUID, request validation will handle this
        }
      }
    }
    return null;
  }
}
