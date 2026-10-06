package br.com.viafluvial.avaliacoesexperiencia.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Profile("dsv")
public class DevAuthenticationFilter extends OncePerRequestFilter {
    private static final UUID DEFAULT_USER = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String user = firstNonBlank(request.getHeader("X-Dev-User-Id"), request.getHeader("X-User-Id"));
            String roles = firstNonBlank(request.getHeader("X-Dev-Roles"), request.getHeader("X-Roles"));
            UUID principal = parseUuidOrDefault(user, DEFAULT_USER);
            var authorities = Arrays.stream(roles == null ? new String[]{"PASSAGEIRO"} : roles.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();

            if (authorities.isEmpty()) {
                authorities = java.util.List.of(new SimpleGrantedAuthority("ROLE_PASSAGEIRO"));
            }

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal.toString(), null, authorities));
        }
        chain.doFilter(request, response);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static UUID parseUuidOrDefault(String value, UUID fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}