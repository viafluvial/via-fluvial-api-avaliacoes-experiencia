package br.com.viafluvial.avaliacoesexperiencia.config;

import br.com.viafluvial.avaliacoesexperiencia.platformsecurity.PlatformOutboundAuthInterceptor;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@Configuration
@EnableScheduling
public class ApplicationConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RestClient.Builder restClientBuilder(
            @Value("${integrations.service-token:}") String serviceToken,
            PlatformOutboundAuthInterceptor platformOutboundAuth,
            ObjectProvider<ObservationRegistry> observationRegistry) {
        // Este builder substitui o auto-configurado; o registro de observacao e aplicado aqui para
        // que o Micrometer instrumente as chamadas e propague o traceparent aos outros servicos.
        return RestClient.builder()
                .observationRegistry(observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP))
                .requestInterceptor(platformOutboundAuth).requestInterceptor((request, body, execution) -> {
            String correlationId = MDC.get("correlationId");
            if (correlationId != null && !correlationId.isBlank()) {
                request.getHeaders().set(CorrelationIdFilter.HEADER, correlationId);
            }
            if (serviceToken != null && !serviceToken.isBlank()
                    && !request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                request.getHeaders().setBearerAuth(serviceToken);
            }
            return execution.execute(request, body);
        });
    }
}