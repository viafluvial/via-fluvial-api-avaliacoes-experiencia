package br.com.viafluvial.avaliacoesexperiencia.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.viafluvial.avaliacoesexperiencia.domain.exception.EvaluationConflictException;
import br.com.viafluvial.avaliacoesexperiencia.domain.exception.IneligibleEvaluationException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FreightEvaluationDomainTest {

    private static final OffsetDateTime DELIVERED = OffsetDateTime.parse("2026-10-01T12:00:00Z");
    private final UUID shipmentId = UUID.randomUUID();
    private final UUID boatmanId = UUID.randomUUID();
    private final UUID contractor = UUID.randomUUID();
    private final UUID recipient = UUID.randomUUID();

    private FreightEligibility eligibility() {
        return FreightEligibility.fromDelivery(UUID.randomUUID(), shipmentId, boatmanId, contractor, recipient,
                DELIVERED, FreightEligibility.DEFAULT_WINDOW_DAYS);
    }

    @Test
    void windowEndsThirtyDaysAfterDelivery() {
        var eligibility = eligibility();
        assertThat(eligibility.expiresAt()).isEqualTo(DELIVERED.plusDays(30));
        assertThat(eligibility.isOpenAt(DELIVERED.plusDays(30))).isTrue();
        assertThat(eligibility.isOpenAt(DELIVERED.plusDays(30).plusSeconds(1))).isFalse();
        assertThatThrownBy(() -> FreightEligibility.fromDelivery(UUID.randomUUID(), shipmentId, boatmanId, contractor,
                recipient, DELIVERED, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifiesRaterAgainstRoleBoatmanAndWindow() {
        var eligibility = eligibility();
        var now = DELIVERED.plusDays(2);
        eligibility.verify(boatmanId, contractor, RaterRole.CONTRATANTE, now);
        eligibility.verify(boatmanId, recipient, RaterRole.DESTINATARIO, now);

        assertThatThrownBy(() -> eligibility.verify(boatmanId, recipient, RaterRole.CONTRATANTE, now))
                .isInstanceOf(IneligibleEvaluationException.class).hasMessage(FreightEligibility.NOT_PARTICIPANT);
        assertThatThrownBy(() -> eligibility.verify(boatmanId, contractor, RaterRole.DESTINATARIO, now))
                .hasMessage(FreightEligibility.NOT_PARTICIPANT);
        assertThatThrownBy(() -> eligibility.verify(UUID.randomUUID(), contractor, RaterRole.CONTRATANTE, now))
                .hasMessage(FreightEligibility.BOATMAN_MISMATCH);
        assertThatThrownBy(() -> eligibility.verify(boatmanId, contractor, RaterRole.CONTRATANTE, DELIVERED.plusDays(31)))
                .hasMessage(FreightEligibility.WINDOW_EXPIRED);
    }

    @Test
    void recipientRoleIsRejectedWhenShipmentHasNoRecipientUser() {
        var eligibility = FreightEligibility.fromDelivery(UUID.randomUUID(), shipmentId, boatmanId, contractor, null,
                DELIVERED, 30);
        assertThatThrownBy(() -> eligibility.verify(boatmanId, contractor, RaterRole.DESTINATARIO, DELIVERED))
                .hasMessage(FreightEligibility.NOT_PARTICIPANT);
    }

    @Test
    void overallIsRoundedMeanOfTheThreeCriteria() {
        assertThat(FreightScores.of(5, 4, 4).overall().value()).isEqualTo(4);
        assertThat(FreightScores.of(5, 5, 4).overall().value()).isEqualTo(5);
        assertThat(FreightScores.of(1, 2, 2).overall().value()).isEqualTo(2);
        assertThat(FreightScores.of(5, 4, 4).manifestationType()).isEqualTo(ManifestationType.COMPLIMENT);
        assertThat(FreightScores.of(3, 3, 3).manifestationType()).isEqualTo(ManifestationType.SUGGESTION);
        assertThat(FreightScores.of(1, 2, 1).manifestationType()).isEqualTo(ManifestationType.COMPLAINT);
        assertThatThrownBy(() -> FreightScores.of(6, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FreightScores(null, new EvaluationScore(1), new EvaluationScore(1)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void submitsFreightEvaluationWithoutTripReferences() {
        var rater = contractor;
        var evaluation = ExperienceEvaluation.submitFreight(eligibility(), rater, FreightScores.of(5, 4, 5),
                "Carga chegou intacta e no prazo", List.of("CHEGOU_NO_HORARIO"), "key-1", DELIVERED.plusDays(1));

        assertThat(evaluation.targetType()).isEqualTo(TargetType.FREIGHT);
        assertThat(evaluation.isFreight()).isTrue();
        assertThat(evaluation.shipmentId()).isEqualTo(shipmentId);
        assertThat(evaluation.passengerId()).isEqualTo(rater);
        assertThat(evaluation.tripId()).isNull();
        assertThat(evaluation.bookingId()).isNull();
        assertThat(evaluation.ticketId()).isNull();
        assertThat(evaluation.vesselId()).isNull();
        assertThat(evaluation.boatmanId()).isEqualTo(boatmanId);
        assertThat(evaluation.scores().overall().value()).isEqualTo(5);
        assertThat(evaluation.freightScores().cargoCare().value()).isEqualTo(4);
        assertThat(evaluation.status()).isEqualTo(EvaluationStatus.SUBMITTED);
        assertThat(evaluation.anonymousPublicDisplay()).isTrue();
        assertThat(evaluation.eligibleUntil()).isEqualTo(DELIVERED.plusDays(30));
        assertThat(evaluation.idempotencyKey()).isEqualTo("key-1");
    }

    @Test
    void personalDataInFreightCommentGoesToReview() {
        var evaluation = ExperienceEvaluation.submitFreight(eligibility(), contractor, FreightScores.of(4, 4, 4),
                "Me liga 92991234567", List.of(), null, DELIVERED);
        assertThat(evaluation.status()).isEqualTo(EvaluationStatus.UNDER_REVIEW);
    }

    @Test
    void expiredWindowCannotBeSubmitted() {
        assertThatThrownBy(() -> ExperienceEvaluation.submitFreight(eligibility(), contractor, FreightScores.of(4, 4, 4),
                null, List.of(), null, DELIVERED.plusDays(40))).isInstanceOf(EvaluationConflictException.class);
    }

    @Test
    void freightEvaluationCannotBeEditedButCanBeAnsweredAndAnonymized() {
        var evaluation = ExperienceEvaluation.submitFreight(eligibility(), contractor, FreightScores.of(4, 4, 4),
                "Bom atendimento", List.of(), null, DELIVERED);
        assertThatThrownBy(() -> evaluation.update(null, null, null, "novo", null, null, DELIVERED))
                .isInstanceOf(EvaluationConflictException.class);

        evaluation.publish("ok", DELIVERED.plusHours(1));
        evaluation.respond(boatmanId, "Obrigado pela confianca", DELIVERED.plusHours(2));
        assertThat(evaluation.boatmanResponse()).isEqualTo("Obrigado pela confianca");

        evaluation.anonymize(contractor, DELIVERED.plusHours(3));
        assertThat(evaluation.passengerId()).isNull();
        assertThat(evaluation.status()).isEqualTo(EvaluationStatus.ANONYMIZED);
    }

    @Test
    void restoreKeepsTripDefaultsAndValidatesFreightReferences() {
        var now = DELIVERED;
        var scores = new EvaluationScores(new EvaluationScore(4), null, null, null, null, null, null);
        var trip = ExperienceEvaluation.restore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), boatmanId, UUID.randomUUID(), null, scores, null, ManifestationType.COMPLIMENT, null,
                List.of(), EvaluationStatus.PUBLISHED, true, null, null, null, now, now, now);
        assertThat(trip.targetType()).isEqualTo(TargetType.TRIP);
        assertThat(trip.shipmentId()).isNull();
        assertThat(trip.freightScores()).isNull();

        assertThatThrownBy(() -> ExperienceEvaluation.restore(UUID.randomUUID(), TargetType.FREIGHT, null, null,
                contractor, null, null, boatmanId, null, null, scores, FreightScores.of(4, 4, 4), null,
                ManifestationType.COMPLIMENT, null, List.of(), EvaluationStatus.PUBLISHED, true, null, null, null, now,
                now, now)).isInstanceOf(NullPointerException.class);
        assertThat(TargetType.orDefault(null)).isEqualTo(TargetType.TRIP);
    }
}
