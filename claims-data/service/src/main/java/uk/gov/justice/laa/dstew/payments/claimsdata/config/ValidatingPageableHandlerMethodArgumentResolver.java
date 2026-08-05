package uk.gov.justice.laa.dstew.payments.claimsdata.config;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NonNull;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import uk.gov.justice.laa.dstew.payments.claimsdata.exception.InvalidPageableParameterException;

/**
 * A pageable resolver that validates raw "page" and "size" request parameters and throws a
 * structured {@link InvalidPageableParameterException} on invalid input. Centralises pageable
 * validation so controllers don't need to inspect raw request parameters.
 */
public class ValidatingPageableHandlerMethodArgumentResolver
    extends PageableHandlerMethodArgumentResolver {

  @Override
  public Pageable resolveArgument(
      @NonNull MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      @NonNull NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {

    HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
    if (request != null) {
      String pageParam = request.getParameter("page");
      String sizeParam = request.getParameter("size");

      // Centralised numeric parsing/validation to avoid duplicated try/catch blocks
      validateNumericParam(pageParam, "page", 0);
      validateNumericParam(sizeParam, "size", 1);
    }

    // Delegate to super to produce the Pageable instance, then perform final validation on the
    // resolved object (covers framework-provided/resolved Pageable instances).
    Pageable resolved = super.resolveArgument(parameter, mavContainer, webRequest, binderFactory);
    if (!resolved.isUnpaged() && (resolved.getPageNumber() < 0 || resolved.getPageSize() < 1)) {
      throw new InvalidPageableParameterException("page must be >= 0 and size must be >= 1");
    }

    return resolved;
  }

  private void validateNumericParam(String raw, String name, int minInclusive) {
    if (raw == null) {
      return;
    }
    try {
      int v = Integer.parseInt(raw);
      if (v < minInclusive) {
        // Use a concise, per-parameter message to aid clients
        throw new InvalidPageableParameterException(name + " must be >= " + minInclusive);
      }
    } catch (NumberFormatException ex) {
      throw new InvalidPageableParameterException(
          name + " must be an integer >= " + minInclusive, ex);
    }
  }
}
