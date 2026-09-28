package br.com.viafluvial.avaliacoesexperiencia.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExperienceEvaluationTest {

    private final OffsetDateTime now = OffsetDateTime.parse("2026-08-09T12:00:00Z");
    private final UUID passengerId = UUID.randomUUID();
    private final UUID boatmanId = UUID.randomUUID();

    @Test
    void sendsSafetyReportsToReview() {
        var evaluation = create(ManifestationType.SAFETY_REPORT, "Colete estava danificado");
        assertThat(evaluation.status()).isEqualTo(EvaluationStatus.UNDER_REVIEW);
    }

    @Test
    void allowsOnlyOneBoatmanResponseOnPublishedEvaluation() {
        var evaluation = create(ManifestationType.COMPLIMENT, "Boa viagem");
        evaluation.publish(null, now.plusMinutes(1));
        evaluation.respond(boatmanId, "Obrigado", now.plusMinutes(2));

        assertThat(evaluation.boatmanResponse()).isEqualTo("Obrigado");
        assertThatThrownBy(() -> evaluation.respond(boatmanId, "Outra", now.plusMinutes(3)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void anonymizationRemovesPassengerAndComment() {
        var evaluation = create(ManifestationType.SUGGESTION, "Mais assentos");
        evaluation.anonymize(passengerId, now.plusMinutes(1));
        assertThat(evaluation.passengerId()).isNull();
        assertThat(evaluation.comment()).isNull();
        assertThat(evaluation.status()).isEqualTo(EvaluationStatus.ANONYMIZED);
    }

        @Test
        void detectsPersonalDataAndAllowsModerationDecision() {
        var evaluation = create(ManifestationType.COMPLAINT, "Contato passageiro@example.com");
        assertThat(evaluation.status()).isEqualTo(EvaluationStatus.UNDER_REVIEW);

        evaluation.reject("PII no comentario", now.plusMinutes(1));

        assertThat(evaluation.status()).isEqualTo(EvaluationStatus.REJECTED);
        assertThat(evaluation.moderationReason()).isEqualTo("PII no comentario");
        assertThatThrownBy(() -> evaluation.update(null, null, null, "novo", null, null, now.plusMinutes(2)))
            .hasMessageContaining("no longer");
        }

        @Test
        void protectsOwnerBoatmanAndEvaluationWindow() {
        var evaluation = create(ManifestationType.COMPLIMENT, "Boa viagem");
        assertThatThrownBy(() -> evaluation.requireOwner(UUID.randomUUID())).hasMessageContaining("passenger");
        assertThatThrownBy(() -> evaluation.update(null, null, null, "tarde", null, null, now.plusDays(31)))
            .hasMessageContaining("expired");
        evaluation.publish("aprovada", now.plusMinutes(1));
        assertThatThrownBy(() -> evaluation.respond(UUID.randomUUID(), "resposta", now.plusMinutes(2)))
            .hasMessageContaining("boatman");
        assertThatThrownBy(() -> evaluation.contest(UUID.randomUUID(), now.plusMinutes(2)))
            .hasMessageContaining("boatman");
        }

        @Test
        void updatesAndContestsPublishedEvaluation() {
        var evaluation = create(ManifestationType.SUGGESTION, "Mais assentos");
        var updatedScores = new EvaluationScores(new EvaluationScore(4), new EvaluationScore(4), null, null, null, null, null);
        evaluation.update(updatedScores, new NpsScore(8), ManifestationType.COMPLIMENT, " Resolvido ",
            List.of("PLATAFORMA"), false, now.plusMinutes(1));
        assertThat(evaluation.scores()).isEqualTo(updatedScores);
        assertThat(evaluation.comment()).isEqualTo("Resolvido");
        assertThat(evaluation.anonymousPublicDisplay()).isFalse();
        evaluation.publish(" ", now.plusMinutes(2));
        evaluation.contest(boatmanId, now.plusMinutes(3));
        assertThat(evaluation.status()).isEqualTo(EvaluationStatus.CONTESTED);
        evaluation.flagForReview(now.plusMinutes(4));
        assertThat(evaluation.status()).isEqualTo(EvaluationStatus.CONTESTED);
        }

        @Test
        void requiresReasonAndResponseText() {
        var evaluation = create(ManifestationType.COMPLIMENT, null);
        assertThatThrownBy(() -> evaluation.reject(" ", now)).isInstanceOf(IllegalArgumentException.class);
        evaluation.publish(null, now.plusMinutes(1));
        assertThatThrownBy(() -> evaluation.respond(boatmanId, " ", now.plusMinutes(2)))
            .isInstanceOf(IllegalArgumentException.class);
        }

    private ExperienceEvaluation create(ManifestationType type, String comment) {
        var evidence = new EligibilityEvidence(UUID.randomUUID(), passengerId, UUID.randomUUID(), UUID.randomUUID(),
                boatmanId, UUID.randomUUID(), UUID.randomUUID(), now.minusDays(1), now.plusDays(29));
        var scores = new EvaluationScores(new EvaluationScore(5), null, new EvaluationScore(5), null, null, null, null);
        return ExperienceEvaluation.submit(evidence, scores, new NpsScore(9), type, comment,
            List.of("ATENDIMENTO"), true, "test-key", now);
    }
}