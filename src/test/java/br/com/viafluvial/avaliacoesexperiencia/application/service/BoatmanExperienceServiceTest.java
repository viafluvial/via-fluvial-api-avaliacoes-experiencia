package br.com.viafluvial.avaliacoesexperiencia.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.viafluvial.avaliacoesexperiencia.application.model.BoatmanRating;
import br.com.viafluvial.avaliacoesexperiencia.application.model.PageResult;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.EvaluationRepositoryPort;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.OutboxPort;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EligibilityEvidence;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationScore;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationScores;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ExperienceEvaluation;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ManifestationType;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoatmanExperienceServiceTest {
    private final EvaluationRepositoryPort repository = mock(EvaluationRepositoryPort.class);
    private final OutboxPort outbox = mock(OutboxPort.class);
    private final UUID boatmanId = UUID.randomUUID();
    private final BoatmanExperienceService service = new BoatmanExperienceService(repository, outbox,
            Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void calculatesSummaryAndListsPublishedEvaluations() {
        when(repository.averagePublishedByBoatman(boatmanId)).thenReturn(4.5);
        when(repository.countPublishedByBoatman(boatmanId)).thenReturn(4L);
        when(repository.averagePublishedNpsByBoatman(boatmanId)).thenReturn(9.0);
        for (int rating = 1; rating <= 5; rating++) when(repository.countPublishedByBoatmanAndRating(boatmanId, rating)).thenReturn((long) rating);
        var page = new PageResult<ExperienceEvaluation>(List.of(), 0, 20, 0, 0);
        when(repository.findPublishedByBoatman(boatmanId, 0, 20)).thenReturn(page);

        var summary = service.summary(boatmanId);

        assertThat(summary.average()).isEqualTo(4.5);
        assertThat(summary.nps()).isEqualTo(80.0);
        assertThat(summary.sampleSufficient()).isTrue();
        assertThat(service.list(boatmanId, 0, 20)).isSameAs(page);
    }

    @Test
    void respondsAndContestsThroughOutbox() {
        var evaluation = publishedEvaluation();
        when(repository.findById(evaluation.id())).thenReturn(Optional.of(evaluation));
        when(repository.save(evaluation)).thenReturn(evaluation);

        service.respond(boatmanId, evaluation.id(), "Obrigado");
        UUID contestId = service.contest(boatmanId, evaluation.id(), "Discordo");

        assertThat(contestId).isNotNull();
        verify(outbox).append(eq("BOATMAN_RESPONSE_CREATED"), eq(evaluation.id()), anyMap());
        verify(outbox).append(eq("EVALUATION_CONTESTED"), eq(evaluation.id()), anyMap());
    }

    @Test
    void leavesNpsAbsentForEmptySample() {
        when(repository.averagePublishedNpsByBoatman(boatmanId)).thenReturn(null);
        assertThat(service.summary(boatmanId).nps()).isNull();
    }

    @Test
    void summarizesRequestedBoatmenKeepingOrderAndZeroingMissing() {
        UUID other = UUID.randomUUID();
        when(repository.summarizePublishedByBoatmen(List.of(boatmanId, other)))
                .thenReturn(List.of(new BoatmanRating(boatmanId, 4.7, 12)));

        var summaries = service.summaries(java.util.Arrays.asList(boatmanId, null, other, boatmanId));

        assertThat(summaries).extracting(BoatmanRating::boatmanId).containsExactly(boatmanId, other);
        assertThat(summaries.get(0).sampleSufficient()).isTrue();
        assertThat(summaries.get(1).count()).isZero();
        assertThat(summaries.get(1).sampleSufficient()).isFalse();
        assertThat(service.summaries(null)).isEmpty();
        assertThat(service.summaries(List.of())).isEmpty();
    }

    @Test
    void listsTestimonialsWithClampedLimit() {
        var evaluation = publishedEvaluation();
        when(repository.findPublishedTestimonials(4, 20, 6)).thenReturn(List.of(evaluation));
        when(repository.findPublishedTestimonials(4, 20, 12)).thenReturn(List.of());
        when(repository.findPublishedTestimonials(4, 20, 1)).thenReturn(List.of());

        assertThat(service.testimonials(null)).containsExactly(evaluation);
        assertThat(service.testimonials(99)).isEmpty();
        assertThat(service.testimonials(0)).isEmpty();
        verify(repository).findPublishedTestimonials(4, 20, 12);
        verify(repository).findPublishedTestimonials(4, 20, 1);
    }

    private ExperienceEvaluation publishedEvaluation() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-09T12:00:00Z");
        var evidence = new EligibilityEvidence(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                boatmanId, UUID.randomUUID(), null, now.minusDays(1), now.plusDays(29));
        var evaluation = ExperienceEvaluation.submit(evidence,
                new EvaluationScores(new EvaluationScore(5), null, null, null, null, null, null), null,
                ManifestationType.COMPLIMENT, "Boa", List.of(), true, "key-12345", now);
        evaluation.publish(null, now.plusMinutes(1));
        return evaluation;
    }
}