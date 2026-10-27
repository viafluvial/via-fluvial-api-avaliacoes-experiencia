package br.com.viafluvial.avaliacoesexperiencia.adapters.in.web;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.viafluvial.avaliacoesexperiencia.application.model.PageResult;
import br.com.viafluvial.avaliacoesexperiencia.application.service.FreightEvaluationService;
import br.com.viafluvial.avaliacoesexperiencia.config.SecurityConfig;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationStatus;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ExperienceEvaluation;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.FreightEligibility;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.FreightScores;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** GET /internal/avaliacoes-frete: SERVICE-only paginated read of freight evaluations. */
@WebMvcTest(controllers = FreightEvaluationsController.class)
@Import({SecurityConfig.class, EvaluationWebMapper.class, AuthenticatedActor.class, ApiExceptionHandler.class})
@ImportAutoConfiguration(AopAutoConfiguration.class) // class-based proxies for @PreAuthorize controllers, as in the app
@ActiveProfiles("dsv")
class FreightEvaluationsQueryWebMvcTest {

    private static final String PATH = "/avaliacoes-experiencia/internal/avaliacoes-frete";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-10T12:00:00Z");

    @Autowired MockMvc mvc;
    @MockitoBean FreightEvaluationService service;

    private final UUID shipmentId = UUID.randomUUID();
    private final UUID boatmanId = UUID.randomUUID();
    private final UUID contractor = UUID.randomUUID();

    private ExperienceEvaluation publishedWithReply() {
        var eligibility = FreightEligibility.fromDelivery(UUID.randomUUID(), shipmentId, boatmanId, contractor, null,
                NOW.minusDays(1), 30);
        var evaluation = ExperienceEvaluation.submitFreight(eligibility, contractor, FreightScores.of(5, 4, 4),
                "Entrega cuidadosa", List.of("CHEGOU_NO_HORARIO"), "rating-123", NOW);
        evaluation.publish("Conteudo adequado", NOW.plusHours(1));
        evaluation.respond(boatmanId, "Obrigado pela confianca", NOW.plusHours(2));
        return evaluation;
    }

    @Test
    void serviceListsFreightEvaluationsWithModerationAndReply() throws Exception {
        var evaluation = publishedWithReply();
        when(service.list(boatmanId, EvaluationStatus.PUBLISHED, 1, 50))
                .thenReturn(new PageResult<>(List.of(evaluation), 1, 50, 51, 2));

        mvc.perform(get(PATH).header("X-Roles", "SERVICE").param("boatmanId", boatmanId.toString())
                .param("status", "PUBLISHED").param("page", "1").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(51))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].id").value(evaluation.id().toString()))
                .andExpect(jsonPath("$.items[0].targetType").value("FREIGHT"))
                .andExpect(jsonPath("$.items[0].shipmentId").value(shipmentId.toString()))
                .andExpect(jsonPath("$.items[0].boatmanId").value(boatmanId.toString()))
                .andExpect(jsonPath("$.items[0].raterUserId").value(contractor.toString()))
                .andExpect(jsonPath("$.items[0].rating").value(4))
                .andExpect(jsonPath("$.items[0].freightScores.punctuality").value(5))
                .andExpect(jsonPath("$.items[0].freightScores.cargoCare").value(4))
                .andExpect(jsonPath("$.items[0].freightScores.service").value(4))
                .andExpect(jsonPath("$.items[0].comment").value("Entrega cuidadosa"))
                .andExpect(jsonPath("$.items[0].tags[0]").value("CHEGOU_NO_HORARIO"))
                .andExpect(jsonPath("$.items[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.items[0].moderationReason").value("Conteudo adequado"))
                .andExpect(jsonPath("$.items[0].boatmanResponse").value("Obrigado pela confianca"))
                .andExpect(jsonPath("$.items[0].createdAt").exists())
                .andExpect(jsonPath("$.items[0].updatedAt").exists())
                .andExpect(jsonPath("$.items[0].tripId").doesNotExist());
    }

    @Test
    void pendingEvaluationHasNoReplyNorModerationReason() throws Exception {
        var eligibility = FreightEligibility.fromDelivery(UUID.randomUUID(), shipmentId, boatmanId, contractor, null,
                NOW.minusDays(1), 30);
        var pending = ExperienceEvaluation.submitFreight(eligibility, contractor, FreightScores.of(2, 2, 3), null,
                List.of(), null, NOW);
        when(service.list(null, null, 0, 20)).thenReturn(new PageResult<>(List.of(pending), 0, 20, 1, 1));

        mvc.perform(get(PATH).header("X-Roles", "SERVICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$.items[0].rating").value(2))
                .andExpect(jsonPath("$.items[0].boatmanResponse").value(nullValue()))
                .andExpect(jsonPath("$.items[0].moderationReason").value(nullValue()));
    }

    @Test
    void defaultsToFirstPageOfTwentyWithoutFilters() throws Exception {
        when(service.list(null, null, 0, 20)).thenReturn(new PageResult<>(List.of(), 0, 20, 0, 0));

        mvc.perform(get(PATH).header("X-Roles", "SERVICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0));
        verify(service).list(null, null, 0, 20);
    }

    @Test
    void everyModerationStatusIsAcceptedAsFilter() throws Exception {
        when(service.list(any(), any(), anyInt(), anyInt())).thenReturn(new PageResult<>(List.of(), 0, 20, 0, 0));
        for (EvaluationStatus value : EvaluationStatus.values()) {
            mvc.perform(get(PATH).header("X-Roles", "SERVICE").param("status", value.name()))
                    .andExpect(status().isOk());
            verify(service).list(null, value, 0, 20);
        }
    }

    @Test
    void onlyServiceRoleMayReadFreightEvaluations() throws Exception {
        for (String roles : List.of("PASSAGEIRO", "GESTOR", "ADMINISTRADOR", "SUPORTE", "AUDITOR")) {
            mvc.perform(get(PATH).header("X-Roles", roles)).andExpect(status().isForbidden());
        }
        // No roles header: the dev filter authenticates as PASSAGEIRO.
        mvc.perform(get(PATH)).andExpect(status().isForbidden());
        verify(service, never()).list(any(), any(), any(), any());
    }

    @Test
    void invalidBoatmanIdIsA400() throws Exception {
        mvc.perform(get(PATH).header("X-Roles", "SERVICE").param("boatmanId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
        verify(service, never()).list(any(), any(), any(), any());
    }

    @Test
    void unknownStatusIsA400() throws Exception {
        mvc.perform(get(PATH).header("X-Roles", "SERVICE").param("status", "APPROVED"))
                .andExpect(status().isBadRequest());
        verify(service, never()).list(any(), any(), any(), any());
    }

    @Test
    void outOfRangePaginationIsA400() throws Exception {
        for (String[] params : List.of(new String[] {"size", "101"}, new String[] {"size", "0"},
                new String[] {"page", "-1"}, new String[] {"size", "abc"})) {
            mvc.perform(get(PATH).header("X-Roles", "SERVICE").param(params[0], params[1]))
                    .andExpect(status().isBadRequest());
        }
        verify(service, never()).list(any(), any(), any(), any());
    }
}
