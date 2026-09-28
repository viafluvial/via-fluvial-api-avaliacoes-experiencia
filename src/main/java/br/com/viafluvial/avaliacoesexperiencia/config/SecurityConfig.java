package br.com.viafluvial.avaliacoesexperiencia.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC = {
        "/actuator/health/**", "/v3/api-docs/**", "/openapi/**", "/swagger-ui/**",
        "/avaliacoes-experiencia/barqueiros/*/resumo"
    };

    @Bean
    @Profile("dsv")
    SecurityFilterChain dsvSecurity(HttpSecurity http, DevAuthenticationFilter filter) throws Exception {
        return base(http).authorizeHttpRequests(auth -> auth.requestMatchers(PUBLIC).permitAll()
                .anyRequest().authenticated()).addFilterBefore(filter,
                        org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class).build();
    }

    @Bean
    @Profile({"hml", "prd"})
    SecurityFilterChain jwtSecurity(HttpSecurity http) throws Exception {
        return base(http).authorizeHttpRequests(auth -> auth.requestMatchers(PUBLIC).permitAll()
                .anyRequest().authenticated()).oauth2ResourceServer(server -> server.jwt(Customizer.withDefaults())).build();
    }

    private HttpSecurity base(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable()).cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:}") String allowedOrigins,
            @Value("${app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}") String allowedOriginPatterns,
            @Value("${app.cors.allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS,HEAD}") String allowedMethods,
            @Value("${app.cors.allowed-headers:*}") String allowedHeaders,
            @Value("${app.cors.exposed-headers:Location,X-Correlation-Id,WWW-Authenticate}") String exposedHeaders,
            @Value("${app.cors.allow-credentials:true}") boolean allowCredentials,
            @Value("${app.cors.max-age:3600}") long maxAge) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(splitCsv(allowedOrigins));
        configuration.setAllowedOriginPatterns(splitCsv(allowedOriginPatterns));
        configuration.setAllowedMethods(splitCsv(allowedMethods));
        configuration.setAllowedHeaders(splitCsv(allowedHeaders));
        configuration.setExposedHeaders(splitCsv(exposedHeaders));
        configuration.setAllowCredentials(allowCredentials);
        configuration.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private List<String> splitCsv(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }
}