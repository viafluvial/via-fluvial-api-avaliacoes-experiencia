package br.com.viafluvial.avaliacoesexperiencia.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@io.swagger.v3.oas.annotations.OpenAPIDefinition(
    info = @io.swagger.v3.oas.annotations.info.Info(
        title = "api-avaliacoes-experiencia",
        version = "1.0.0",
        description = "Gerencia avaliacoes de experiencia e feedback dos usuarios para monitorar qualidade da jornada e apoiar melhorias continuas na operacao."),
    servers = {
        @io.swagger.v3.oas.annotations.servers.Server(url = "/avaliacoes-experiencia/api/v1", description = "Entrypoint padrao no API Gateway (Gravitee).")
    },
    security = {
        @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    }
)
public class OpenApiConfig {

    @Bean
    OpenAPI runtimeOpenApi() {
        String schemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("api-avaliacoes-experiencia")
                        .version("1.0.0")
                        .description("Gerencia avaliacoes de experiencia e feedback dos usuarios para monitorar qualidade da jornada e apoiar melhorias continuas na operacao."))
                .servers(List.of(new Server()
                        .url("/avaliacoes-experiencia/api/v1")
                        .description("Entrypoint padrao no API Gateway (Gravitee).")))
                .components(new Components()
                        .addSecuritySchemes(
                                schemeName,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(schemeName));
    }
}
