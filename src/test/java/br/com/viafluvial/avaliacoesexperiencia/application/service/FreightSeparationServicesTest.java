package br.com.viafluvial.avaliacoesexperiencia.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.viafluvial.avaliacoesexperiencia.application.model.CreateEvaluationCommand;
import br.com.viafluvial.avaliacoesexperiencia.application.model.FreightCriteriaAverages;
import br.com.viafluvial.avaliacoesexperiencia.application.model.PageResult;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.EligibilityGatewayPort;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.EvaluationRepositoryPort;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.OutboxPort;
import br.com.viafluvial.avaliacoesexperiencia.domain.exception.EvaluationConflictException;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationScore;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationScores;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationStatus;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ExperienceEvaluation;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.FreightEligibility;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.FreightScores;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ManifestationType;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.TargetType;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FREIGHT evaluations never pollute TRIP summaries, lists and outbox payloads. */
class FreightSeparationServicesTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-10T12:00:00Z");
    private final EvaluationRepositoryPort repository = mock(EvaluationRepositoryPort.class);
    private final OutboxPort outbox = mock(OutboxPort.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC);
    private final BoatmanExperienceService boatmanService = new BoatmanExperienceService(repository, outbox, clock);
    private final EvaluationModerationService moderationService = new EvaluationModerationService(repository, outbox, clock);
    private final UUID boatmanId = UUID.randomUUID();

    private ExperienceEvaluation freight() {
        var eligibility = FreightEligibility.fromDelivery(UUID.randomUUID(), UUID.randomUUID(), boatmanId,
                UUID.randomUUID(), UUID.randomUUID(), NOW.minusDays(1), 30);
        return ExperienceEvaluation.submitFreight(eligibility, eligibility.contractorUserId(), FreightScores.of(5, 4, 3),
                "Tudo certo", List.of(), null, NOW);
    }

    @Test
    void freightSummaryUsesOnlyFreightAggregatesAndExposesCriteria() {
        when(repository.countPublishedByBoatman(boatmanId, TargetType.FREIGHT)).thenReturn(3L);
        when(repository.averagePublishedByBoatman(boatmanId, TargetType.FREIGHT)).thenReturn(4.0);
        when(repository.countPublishedByBoatmanAndRating(eq(boatmanId), eq(TargetType.FREIGHT), anyInt())).thenReturn(1L);
        when(repository.averagePublishedFreightCriteriaByBoatman(boatmanId))
                .thenReturn(new FreightCriteriaAverages(4.5, 4.0, 3.5));

        var summary = boatmanService.summary(boatmanId, TargetType.FREIGHT);

        assertThat(summary.targetType()).isEqualTo(TargetType.FREIGHT);
        assertThat(summary.count()).isEqualTo(3);
        assertThat(summary.average()).isEqualTo(4.0);
        assertThat(summary.nps()).isNull();
        assertThat(summary.sampleSufficient()).isTrue();
        assertThat(summary.distribution()).containsEntry(5, 1L);
        assertThat(summary.criteria().punctuality()).isEqualTo(4.5);
        verify(repository, never()).countPublishedByBoatman(boatmanId);
        verify(repository, never()).averagePublishedByBoatman(boatmanId);
    }

    @Test
    void freightSummaryWithoutCriteriaRowsIsEmpty() {
        var summary = boatmanService.summary(boatmanId, TargetType.FREIGHT);
        assertThat(summary.count()).isZero();
        assertThat(summary.sampleSufficient()).isFalse();
        assertThat(summary.criteria()).isEqualTo(FreightCriteriaAverages.empty());
    }

    @Test
    void tripSummaryAndListAreTheHistoricalOnes() {
        when(repository.countPublishedByBoatman(boatmanId)).thenReturn(2L);
        when(repository.averagePublishedByBoatman(boatmanId)).thenReturn(4.5);
        var page = new PageResult<ExperienceEvaluation>(List.of(), 0, 20, 0, 0);
        when(repository.findPublishedByBoatman(boatmanId, 0, 20)).thenReturn(page);

        var summary = boatmanService.summary(boatmanId, null);

        assertThat(summary.targetType()).isEqualTo(TargetType.TRIP);
        assertThat(summary.criteria()).isNull();
        assertThat(summary.average()).isEqualTo(4.5);
        assertThat(boatmanService.summary(boatmanId, TargetType.TRIP).count()).isEqualTo(2);
        assertThat(boatmanService.list(boatmanId, null, 0, 20)).isSameAs(page);
        assertThat(boatmanService.list(boatmanId, TargetType.TRIP, 0, 20)).isSameAs(page);
        verify(repository, never()).countPublishedByBoatman(any(), eq(TargetType.FREIGHT));
        verify(repository, never()).findPublishedByBoatman(any(), any(TargetType.class), anyInt(), anyInt());
    }

    @Test
    void freightListUsesFreightQuery() {
        var page = new PageResult<ExperienceEvaluation>(List.of(freight()), 0, 20, 1, 1);
        when(repository.findPublishedByBoatman(boatmanId, TargetType.FREIGHT, 0, 20)).thenReturn(page);
        assertThat(boatmanService.list(boatmanId, TargetType.FREIGHT, 0, 20)).isSameAs(page);
        verify(repository, never()).findPublishedByBoatman(boatmanId, 0, 20);
    }

    @Test
    void responseToFreightEvaluationCarriesTargetType() {
        var evaluation = freight();
        evaluation.publish(null, NOW);
        when(repository.findById(evaluation.id())).thenReturn(Optional.of(evaluation));

        boatmanService.respond(boatmanId, evaluation.id(), "Obrigado");

        verify(outbox).append("BOATMAN_RESPONSE_CREATED", evaluation.id(), Map.of("evaluationId", evaluation.id(),
                "boatmanId", boatmanId, "targetType", "FREIGHT", "shipmentId", evaluation.shipmentId()));
    }

    @Test
    void moderationListsByTargetTypeAndPublishesFreightWithTargetType() {
        var page = new PageResult<ExperienceEvaluation>(List.of(), 0, 20, 0, 0);
        when(repository.findForModeration(EvaluationStatus.SUBMITTED, null, null, 0, 20)).thenReturn(page);
        assertThat(moderationService.list(EvaluationStatus.SUBMITTED, null, null, 0, 20)).isSameAs(page);

        var approved = freight();
        var rejected = freight();
        when(repository.findById(approved.id())).thenReturn(Optional.of(approved));
        when(repository.findById(rejected.id())).thenReturn(Optional.of(rejected));
        moderationService.approve(approved.id(), null);
        moderationService.reject(rejected.id(), "Linguagem ofensiva");

        verify(outbox).append("EVALUATION_PUBLISHED", approved.id(), Map.of("evaluationId", approved.id(),
                "targetType", "FREIGHT", "shipmentId", approved.shipmentId()));
        verify(outbox).append("EVALUATION_REJECTED", rejected.id(), Map.of("evaluationId", rejected.id(),
                "reason", "Linguagem ofensiva", "targetType", "FREIGHT", "shipmentId", rejected.shipmentId()));
    }

    @Test
    void passengerCannotReuseTheIdempotencyKeyOfAFreightEvaluation() {
        var gateway = mock(EligibilityGatewayPort.class);
        var passengerService = new PassengerEvaluationService(repository, gateway, outbox, clock);
        var evaluation = freight();
        when(repository.findByIdempotencyKey(evaluation.passengerId(), "key-1")).thenReturn(Optional.of(evaluation));
        var command = new CreateEvaluationCommand(UUID.randomUUID(), evaluation.passengerId(),
                new EvaluationScores(new EvaluationScore(5), null, null, null, null, null, null), null,
                ManifestationType.COMPLIMENT, null, List.of(), true, "key-1");

        assertThatThrownBy(() -> passengerService.create(command)).isInstanceOf(EvaluationConflictException.class);
        verify(gateway, never()).verify(any(), any());
    }
}
