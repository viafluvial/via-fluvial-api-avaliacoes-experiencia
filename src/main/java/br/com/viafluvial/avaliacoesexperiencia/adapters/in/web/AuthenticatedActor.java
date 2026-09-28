package br.com.viafluvial.avaliacoesexperiencia.adapters.in.web;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedActor {
    public UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) throw new IllegalStateException("Authentication required");
        Object principal = authentication.getPrincipal();
        String value = principal instanceof Jwt jwt ? jwt.getSubject() : authentication.getName();
        return UUID.fromString(value);
    }
}