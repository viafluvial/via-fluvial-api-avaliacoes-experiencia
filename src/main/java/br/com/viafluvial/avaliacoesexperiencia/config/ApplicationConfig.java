package br.com.viafluvial.avaliacoesexperiencia.config;

import java.time.Clock;
import org.slf4j.MDC;
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
            @Value("${integrations.service-token:}") String serviceToken) {
        return RestClient.builder().requestInterceptor((request, body, execution) -> {
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