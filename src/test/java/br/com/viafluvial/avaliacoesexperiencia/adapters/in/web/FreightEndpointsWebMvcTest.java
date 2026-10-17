package br.com.viafluvial.avaliacoesexperiencia.adapters.in.web;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.viafluvial.avaliacoesexperiencia.application.model.CreateFreightEvaluationCommand;
import br.com.viafluvial.avaliacoesexperiencia.application.model.FreightCriteriaAverages;
import br.com.viafluvial.avaliacoesexperiencia.application.model.FreightDeliveredCommand;
import br.com.viafluvial.avaliacoesexperiencia.application.model.PageResult;
import br.com.viafluvial.avaliacoesexperiencia.application.service.BoatmanExperienceService;
import br.com.viafluvial.avaliacoesexperiencia.application.service.EvaluationModerationService;
import br.com.viafluvial.avaliacoesexperiencia.application.service.FreightEvaluationService;
import br.com.viafluvial.avaliacoesexperiencia.application.service.TripEventService;
import br.com.viafluvial.avaliacoesexperiencia.config.SecurityConfig;
import br.com.viafluvial.avaliacoesexperiencia.domain.exception.EvaluationConflictException;
import br.com.viafluvial.avaliacoesexperiencia.domain.exception.IneligibleEvaluationException;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ExperienceEvaluation;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.FreightEligibility;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.FreightScores;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.RaterRole;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.TargetType;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {FreightEvaluationsController.class, ExperienceEventsController.class,
        BoatmanExperienceController.class, EvaluationModerationController.class})
@Import({SecurityConfig.class, EvaluationWebMapper.class, AuthenticatedActor.class, ApiExceptionHandler.class})
@ImportAutoConfiguration(AopAutoConfiguration.class) // class-based proxies for @PreAuthorize controllers, as in the app
@ActiveProfiles("dsv")
class FreightEndpointsWebMvcTest {

    private static final String FREIGHT_EVENT = "/avaliacoes-experiencia/internal/eventos/frete-entregue";
    private static final String FREIGHT_EVALUATION = "/avaliacoes-experiencia/internal/avaliacoes-frete";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-10T12:00:00Z");

    @Autowired MockMvc mvc;
    @MockitoBean FreightEvaluationService freightService;
    @MockitoBean TripEventService tripEventService;
    @MockitoBean BoatmanExperienceService boatmanService;
    @MockitoBean EvaluationModerationService moderationService;

    private final UUID shipmentId = UUID.randomUUID();
    private final UUID boatmanId = UUID.randomUUID();
    private final UUID contractor = UUID.randomUUID();

    private String eventBody() {
        return """
                {"eventId":"%s","shipmentId":"%s","boatmanId":"%s","contractorUserId":"%s",
                 "recipientUserId":"%s","deliveredAt":"2026-10-09T15:00:00Z"}
                """.formatted(UUID.randomUUID(), shipmentId, boatmanId, contractor, UUID.randomUUID());
    }

    private String evaluationBody(int punctuality) {
        return """
                {"shipmentId":"%s","boatmanId":"%s","raterUserId":"%s","raterRole":"CONTRATANTE",
                 "punctuality":%d,"cargoCare":4,"service":4,"tags":["CHEGOU_NO_HORARIO","CHEGOU_NO_HORARIO"],
                 "comment":"Entrega cuidadosa","idempotencyKey":"rating-123"}
                """.formatted(shipmentId, boatmanId, contractor, punctuality);
    }

    private ExperienceEvaluation freightEvaluation() {
        var eligibility = FreightEligibility.fromDelivery(UUID.randomUUID(), shipmentId, boatmanId, contractor, null,
                NOW.minusDays(1), 30);
        return ExperienceEvaluation.submitFreight(eligibility, contractor, FreightScores.of(5, 4, 4),
                "Entrega cuidadosa", List.of("CHEGOU_NO_HORARIO"), "rating-123", NOW);
    }

    @Test
    void freightDeliveredEventIsAcceptedThenReportedAsAlreadyProcessed() throws Exception {
        when(freightService.receiveDelivered(any())).thenReturn(true, false);

        mvc.perform(post(FREIGHT_EVENT).header("X-Dev-Roles", "SERVICE").contentType(MediaType.APPLICATION_JSON)
                .content(eventBody()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("EVENT_ACCEPTED"))
                .andExpect(jsonPath("$.resourceId").value(shipmentId.toString()));
        mvc.perform(post(FREIGHT_EVENT).header("X-Dev-Roles", "SERVICE").contentType(MediaType.APPLICATION_JSON)
                .content(eventBody()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("EVENT_ALREADY_PROCESSED"));

        var captor = ArgumentCaptor.forClass(FreightDeliveredCommand.class);
        verify(freightService, org.mockito.Mockito.times(2)).receiveDelivered(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().deliveredAt())
                .isEqualTo(OffsetDateTime.parse("2026-10-09T15:00:00Z"));
    }

    @Test
    void internalFreightEndpointsRequireServiceRole() throws Exception {
        for (String roles : List.of("PASSAGEIRO", "ADMINISTRADOR", "GESTOR")) {
            mvc.perform(post(FREIGHT_EVENT).header("X-Dev-Roles", roles).contentType(MediaType.APPLICATION_JSON)
                    .content(eventBody())).andExpect(status().isForbidden());
            mvc.perform(post(FREIGHT_EVALUATION).header("X-Dev-Roles", roles).contentType(MediaType.APPLICATION_JSON)
                    .content(evaluationBody(5))).andExpect(status().isForbidden());
        }
        verify(freightService, never()).receiveDelivered(any());
        verify(freightService, never()).create(any());
    }

    @Test
    void invalidFreightEventIsRejected() throws Exception {
        mvc.perform(post(FREIGHT_EVENT).header("X-Dev-Roles", "SERVICE").contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
        verify(freightService, never()).receiveDelivered(any());
    }

    @Test
    void createsFreightEvaluationOnBehalfOfTheRater() throws Exception {
        var evaluation = freightEvaluation();
        when(freightService.create(any())).thenReturn(evaluation);

        mvc.perform(post(FREIGHT_EVALUATION).header("X-Dev-Roles", "SERVICE").contentType(MediaType.APPLICATION_JSON)
                .content(evaluationBody(5)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/avaliacoes-experiencia/avaliacoes/" + evaluation.id()))
                .andExpect(jsonPath("$.id").value(evaluation.id().toString()))
                .andExpect(jsonPath("$.targetType").value("FREIGHT"))
                .andExpect(jsonPath("$.shipmentId").value(shipmentId.toString()))
                .andExpect(jsonPath("$.tripId").value(nullValue()))
                .andExpect(jsonPath("$.boatmanId").value(boatmanId.toString()))
                .andExpect(jsonPath("$.scores.overall").value(4))
                .andExpect(jsonPath("$.freightScores.punctuality").value(5))
                .andExpect(jsonPath("$.freightScores.cargoCare").value(4))
                .andExpect(jsonPath("$.freightScores.service").value(4))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        var captor = ArgumentCaptor.forClass(CreateFreightEvaluationCommand.class);
        verify(freightService).create(captor.capture());
        var command = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(command.raterRole()).isEqualTo(RaterRole.CONTRATANTE);
        org.assertj.core.api.Assertions.assertThat(command.raterUserId()).isEqualTo(contractor);
        org.assertj.core.api.Assertions.assertThat(command.tags()).containsExactly("CHEGOU_NO_HORARIO");
        org.assertj.core.api.Assertions.assertThat(command.idempotencyKey()).isEqualTo("rating-123");
    }

    @Test
    void idempotencyHeaderIsUsedWhenBodyHasNoKey() throws Exception {
        when(freightService.create(any())).thenReturn(freightEvaluation());
        String body = evaluationBody(5).replace(",\"idempotencyKey\":\"rating-123\"", "");

        mvc.perform(post(FREIGHT_EVALUATION).header("X-Dev-Roles", "SERVICE").header("Idempotency-Key", "header-key-1")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());

        var captor = ArgumentCaptor.forClass(CreateFreightEvaluationCommand.class);
        verify(freightService).create(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().idempotencyKey()).isEqualTo("header-key-1");
    }

    @Test
    void scoresOutOfRangeAreRejected() throws Exception {
        mvc.perform(post(FREIGHT_EVALUATION).header("X-Dev-Roles", "SERVICE").contentType(MediaType.APPLICATION_JSON)
                .content(evaluationBody(6))).andExpect(status().isBadRequest());
        verify(freightService, never()).create(any());
    }

    @Test
    void duplicateEvaluationIsA409ProblemDetail() throws Exception {
        when(freightService.create(any())).thenThrow(
                new EvaluationConflictException("Rater already evaluated this shipment", "ALREADY_EVALUATED"));

        mvc.perform(post(FREIGHT_EVALUATION).header("X-Dev-Roles", "SERVICE").contentType(MediaType.APPLICATION_JSON)
                .content(evaluationBody(5)))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("ALREADY_EVALUATED"));
    }

    @Test
    void ineligibleRaterIsA422ProblemDetail() throws Exception {
        when(freightService.create(any())).thenThrow(new IneligibleEvaluationException("NOT_PARTICIPANT"));

        mvc.perform(post(FREIGHT_EVALUATION).header("X-Dev-Roles", "SERVICE").contentType(MediaType.APPLICATION_JSON)
                .content(evaluationBody(5)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NOT_PARTICIPANT"));
    }

    @Test
    void freightSummaryExposesCriteriaAndTripSummaryStaysTheDefault() throws Exception {
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int rating = 1; rating <= 5; rating++) distribution.put(rating, rating == 5 ? 3L : 0L);
        when(boatmanService.summary(boatmanId, TargetType.FREIGHT)).thenReturn(new BoatmanExperienceService.Summary(
                5.0, 3, null, distribution, true, TargetType.FREIGHT, new FreightCriteriaAverages(5.0, 4.5, 4.0)));
        when(boatmanService.summary(boatmanId, TargetType.TRIP)).thenReturn(new BoatmanExperienceService.Summary(
                4.0, 1, 80.0, distribution, false));

        mvc.perform(get("/avaliacoes-experiencia/barqueiros/{id}/resumo", boatmanId).param("targetType", "FREIGHT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetType").value("FREIGHT"))
                .andExpect(jsonPath("$.verifiedEvaluationCount").value(3))
                .andExpect(jsonPath("$.criteria.punctuality").value(5.0))
                .andExpect(jsonPath("$.criteria.cargoCare").value(4.5))
                .andExpect(jsonPath("$.criteria.service").value(4.0));
        mvc.perform(get("/avaliacoes-experiencia/barqueiros/{id}/resumo", boatmanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetType").value("TRIP"))
                .andExpect(jsonPath("$.averageRating").value(4.0))
                .andExpect(jsonPath("$.nps").value(80.0))
                .andExpect(jsonPath("$.criteria").doesNotExist());
        mvc.perform(get("/avaliacoes-experiencia/barqueiros/{id}/resumo", boatmanId).param("targetType", "OTHER"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void managerListsFreightEvaluationsSeparately() throws Exception {
        UUID manager = UUID.randomUUID();
        when(boatmanService.list(manager, TargetType.FREIGHT, 0, 20))
                .thenReturn(new PageResult<>(List.of(freightEvaluation()), 0, 20, 1, 1));
        when(boatmanService.list(manager, null, 0, 20)).thenReturn(new PageResult<>(List.of(), 0, 20, 0, 0));

        mvc.perform(get("/avaliacoes-experiencia/barqueiros/me/avaliacoes").param("targetType", "FREIGHT")
                .header("X-Dev-Roles", "GESTOR").header("X-Dev-User-Id", manager.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].targetType").value("FREIGHT"));
        mvc.perform(get("/avaliacoes-experiencia/barqueiros/me/avaliacoes")
                .header("X-Dev-Roles", "GESTOR").header("X-Dev-User-Id", manager.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void serviceRespondsOnBehalfOfTheBoatmanInformedInHeader() throws Exception {
        UUID evaluationId = UUID.randomUUID();
        mvc.perform(post("/avaliacoes-experiencia/avaliacoes/{id}/resposta", evaluationId)
                .header("X-Dev-Roles", "SERVICE").header("X-Boatman-Id", boatmanId.toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Obrigado pela avaliacao\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("RESPONSE_CREATED"));
        verify(boatmanService).respond(boatmanId, evaluationId, "Obrigado pela avaliacao");
    }

    @Test
    void serviceResponseWithoutBoatmanHeaderIsRejected() throws Exception {
        mvc.perform(post("/avaliacoes-experiencia/avaliacoes/{id}/resposta", UUID.randomUUID())
                .header("X-Dev-Roles", "SERVICE").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Obrigado\"}"))
                .andExpect(status().isBadRequest());
        verify(boatmanService, never()).respond(any(), any(), any());
    }

    @Test
    void managerStillRespondsAsAuthenticatedBoatmanIgnoringHeader() throws Exception {
        UUID manager = UUID.randomUUID();
        UUID evaluationId = UUID.randomUUID();
        mvc.perform(post("/avaliacoes-experiencia/avaliacoes/{id}/resposta", evaluationId)
                .header("X-Dev-Roles", "GESTOR").header("X-Dev-User-Id", manager.toString())
                .header("X-Boatman-Id", boatmanId.toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Obrigado\"}"))
                .andExpect(status().isCreated());
        verify(boatmanService).respond(manager, evaluationId, "Obrigado");
        mvc.perform(post("/avaliacoes-experiencia/avaliacoes/{id}/resposta", evaluationId)
                .header("X-Dev-Roles", "PASSAGEIRO").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Obrigado\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderationQueueFiltersByTargetTypeOnlyWhenRequested() throws Exception {
        when(moderationService.list(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0, 0));

        mvc.perform(get("/avaliacoes-experiencia/admin/avaliacoes").header("X-Dev-Roles", "ADMINISTRADOR"))
                .andExpect(status().isOk());
        verify(moderationService).list(null, null, null, 0, 20);
        mvc.perform(get("/avaliacoes-experiencia/admin/avaliacoes").param("targetType", "FREIGHT")
                .header("X-Dev-Roles", "ADMINISTRADOR")).andExpect(status().isOk());
        verify(moderationService).list(null, null, TargetType.FREIGHT, 0, 20);
        verify(moderationService, never()).list(any(), any(), anyInt(), anyInt());
        verify(moderationService, never()).list(eq(null), eq(null), eq(TargetType.TRIP), anyInt(), anyInt());
    }
}
