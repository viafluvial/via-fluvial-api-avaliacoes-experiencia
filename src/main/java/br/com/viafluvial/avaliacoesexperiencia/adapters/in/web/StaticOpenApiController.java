package br.com.viafluvial.avaliacoesexperiencia.adapters.in.web;

import io.swagger.v3.oas.annotations.Hidden;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Hidden
public class StaticOpenApiController {
    @GetMapping(value = "/openapi.yaml", produces = "application/yaml")
    ResponseEntity<String> staticContract() throws IOException {
        var resource = new ClassPathResource("static/openapi/openapi.yaml");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/yaml"))
                .body(resource.getContentAsString(StandardCharsets.UTF_8));
    }
}