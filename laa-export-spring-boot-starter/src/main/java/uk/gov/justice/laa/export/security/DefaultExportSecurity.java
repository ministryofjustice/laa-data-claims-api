package uk.gov.justice.laa.export.security;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import uk.gov.justice.laa.export.ExportAccessDeniedException;
import uk.gov.justice.laa.export.ExportSecurity;
import uk.gov.justice.laa.export.model.ExportDefinition;

/**
 * Default security check based on Spring Security roles.
 */
public class DefaultExportSecurity implements ExportSecurity {

  @Override
  public void checkAllowed(ExportDefinition def) {
    if (def.getRoles().isEmpty()) {
      return;
    }
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      throw new ExportAccessDeniedException("Not authenticated for export");
    }

    Set<String> allowed = new HashSet<>(def.getRoles());
    if (allowed.contains("ALL")) {
      return;
    }

    Set<String> authorities =
        auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());

    for (String role : allowed) {
      if (authorities.contains(role)) {
        return;
      }
    }
    throw new ExportAccessDeniedException("Not authorized for export: " + def.getKey());
  }
}
