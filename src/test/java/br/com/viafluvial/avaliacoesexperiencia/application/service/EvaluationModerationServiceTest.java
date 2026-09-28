package br.com.viafluvial.avaliacoesexperiencia.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.viafluvial.avaliacoesexperiencia.application.model.PageResult;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.EvaluationRepositoryPort;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.OutboxPort;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EligibilityEvidence;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationScore;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationScores;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationStatus;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ExperienceEvaluation;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ManifestationType;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EvaluationModerationServiceTest {
    private EvaluationRepositoryPort repository;
    private OutboxPort outbox;
    private EvaluationModerationService service;

    @BeforeEach
    void setUp() {
        repository = mock(EvaluationRepositoryPort.class); outbox = mock(OutboxPort.class);
        service = new EvaluationModerationService(repository, outbox,
                Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void approvesRejectsAndResolvesContest() {
        var approved = evaluation(); var rejected = evaluation();
        when(repository.findById(approved.id())).thenReturn(Optional.of(approved));
        when(repository.findById(rejected.id())).thenReturn(Optional.of(rejected));
        when(repository.save(approved)).thenReturn(approved); when(repository.save(rejected)).thenReturn(rejected);

        assertThat(service.approve(approved.id(), "ok").status()).isEqualTo(EvaluationStatus.PUBLISHED);
        assertThat(service.reject(rejected.id(), "PII").status()).isEqualTo(EvaluationStatus.REJECTED);
        UUID contestId = UUID.randomUUID(); service.resolveContest(contestId, true, "procedente");

        verify(outbox).append(eq("EVALUATION_PUBLISHED"), eq(approved.id()), anyMap());
        verify(outbox).append(eq("EVALUATION_REJECTED"), eq(rejected.id()), anyMap());
        verify(outbox).append(eq("EVALUATION_CONTEST_RESOLVED"), eq(contestId), anyMap());
    }

    @Test
    void returnsQueueAndIndicators() {
        var page = new PageResult<ExperienceEvaluation>(List.of(), 0, 20, 0, 0);
        when(repository.findForModeration(EvaluationStatus.UNDER_REVIEW, 2, 0, 20)).thenReturn(page);
        when(repository.averagePublished()).thenReturn(4.2); when(repository.countPublished()).thenReturn(10L);
        when(repository.averagePublishedNps()).thenReturn(8.0);
        for (int rating = 1; rating <= 5; rating++) when(repository.countPublishedByRating(rating)).thenReturn((long) rating);
        when(repository.countByStatus(EvaluationStatus.SUBMITTED)).thenReturn(2L);
        when(repository.countByStatus(EvaluationStatus.PUBLISHED)).thenReturn(10L);
        when(repository.countByStatus(EvaluationStatus.REJECTED)).thenReturn(1L);
        when(repository.countByStatus(EvaluationStatus.UNDER_REVIEW)).thenReturn(3L);

        assertThat(service.list(EvaluationStatus.UNDER_REVIEW, 2, 0, 20)).isSameAs(page);
        var indicators = service.indicators();
        assertThat(indicators.average()).isEqualTo(4.2);
        assertThat(indicators.nps()).isEqualTo(60.0);
        assertThat(indicators.underReview()).isEqualTo(3);
    }

    private ExperienceEvaluation evaluation() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-09T11:00:00Z");
        var evidence = new EligibilityEvidence(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), null, now.minusDays(1), now.plusDays(29));
        return ExperienceEvaluation.submit(evidence,
                new EvaluationScores(new EvaluationScore(4), null, null, null, null, null, null), null,
                ManifestationType.COMPLIMENT, "Boa", List.of(), true, "key-12345", now);
    }
}