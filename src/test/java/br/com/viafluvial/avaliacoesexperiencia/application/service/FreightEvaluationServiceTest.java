package br.com.viafluvial.avaliacoesexperiencia.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.viafluvial.avaliacoesexperiencia.application.model.CreateFreightEvaluationCommand;
import br.com.viafluvial.avaliacoesexperiencia.application.model.FreightDeliveredCommand;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.EvaluationRepositoryPort;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.FreightEligibilityRepositoryPort;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.OutboxPort;
import br.com.viafluvial.avaliacoesexperiencia.domain.exception.EvaluationConflictException;
import br.com.viafluvial.avaliacoesexperiencia.domain.exception.IneligibleEvaluationException;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EligibilityEvidence;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationScore;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationScores;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ExperienceEvaluation;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.FreightEligibility;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.FreightScores;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ManifestationType;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.RaterRole;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.TargetType;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FreightEvaluationServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-10T12:00:00Z");
    private EvaluationRepositoryPort repository;
    private FreightEligibilityRepositoryPort eligibilityRepository;
    private OutboxPort outbox;
    private FreightEvaluationService service;
    private final UUID shipmentId = UUID.randomUUID();
    private final UUID boatmanId = UUID.randomUUID();
    private final UUID contractor = UUID.randomUUID();
    private final UUID recipient = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(EvaluationRepositoryPort.class);
        eligibilityRepository = mock(FreightEligibilityRepositoryPort.class);
        outbox = mock(OutboxPort.class);
        service = new FreightEvaluationService(repository, eligibilityRepository, outbox,
                Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC), 30);
        when(repository.insertFreight(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private FreightEligibility eligibility(OffsetDateTime deliveredAt) {
        return FreightEligibility.fromDelivery(UUID.randomUUID(), shipmentId, boatmanId, contractor, recipient,
                deliveredAt, 30);
    }

    private CreateFreightEvaluationCommand command(UUID rater, RaterRole role, String key) {
        return new CreateFreightEvaluationCommand(shipmentId, boatmanId, rater, role, FreightScores.of(5, 4, 4),
                List.of("CHEGOU_NO_HORARIO"), "Entrega cuidadosa", key);
    }

    @Test
    void registersEligibilityOnFirstDeliveryWithThirtyDayWindow() {
        var eventId = UUID.randomUUID();
        when(outbox.registerIncoming(eventId, "FREIGHT_DELIVERED", shipmentId.toString())).thenReturn(true);
        when(eligibilityRepository.findByShipmentId(shipmentId)).thenReturn(Optional.empty());

        boolean accepted = service.receiveDelivered(new FreightDeliveredCommand(eventId, shipmentId, boatmanId,
                contractor, recipient, NOW));

        assertThat(accepted).isTrue();
        var captor = ArgumentCaptor.forClass(FreightEligibility.class);
        verify(eligibilityRepository).save(captor.capture());
        assertThat(captor.getValue().expiresAt()).isEqualTo(NOW.plusDays(30));
        assertThat(captor.getValue().eventId()).isEqualTo(eventId);
        verify(outbox, never()).append(any(), any(), anyMap());
    }

    @Test
    void duplicateEventIsIgnored() {
        var eventId = UUID.randomUUID();
        when(outbox.registerIncoming(eventId, "FREIGHT_DELIVERED", shipmentId.toString())).thenReturn(false);

        assertThat(service.receiveDelivered(new FreightDeliveredCommand(eventId, shipmentId, boatmanId, contractor,
                recipient, NOW))).isFalse();
        verify(eligibilityRepository, never()).save(any());
    }

    @Test
    void anotherEventForAnAlreadyDeliveredShipmentKeepsTheFirstWindow() {
        var eventId = UUID.randomUUID();
        when(outbox.registerIncoming(eventId, "FREIGHT_DELIVERED", shipmentId.toString())).thenReturn(true);
        when(eligibilityRepository.findByShipmentId(shipmentId)).thenReturn(Optional.of(eligibility(NOW.minusDays(3))));

        assertThat(service.receiveDelivered(new FreightDeliveredCommand(eventId, shipmentId, boatmanId, contractor,
                recipient, NOW))).isFalse();
        verify(eligibilityRepository, never()).save(any());
    }

    @Test
    void contractorEvaluatesDeliveredShipmentAndFreightEventIsEmitted() {
        when(eligibilityRepository.findByShipmentId(shipmentId)).thenReturn(Optional.of(eligibility(NOW.minusDays(2))));
        when(repository.findByIdempotencyKey(contractor, "key-1")).thenReturn(Optional.empty());
        when(repository.findFreightByPassengerAndShipment(contractor, shipmentId)).thenReturn(Optional.empty());

        var created = service.create(command(contractor, RaterRole.CONTRATANTE, " key-1 "));

        assertThat(created.targetType()).isEqualTo(TargetType.FREIGHT);
        assertThat(created.passengerId()).isEqualTo(contractor);
        assertThat(created.scores().overall().value()).isEqualTo(4);
        assertThat(created.idempotencyKey()).isEqualTo("key-1");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(outbox).append(eq("FREIGHT_EVALUATION_SUBMITTED"), eq(created.id()), payload.capture());
        assertThat(payload.getValue()).containsEntry("targetType", "FREIGHT").containsEntry("shipmentId", shipmentId)
                .containsEntry("raterRole", "CONTRATANTE").doesNotContainKey("tripId");
        verify(outbox, never()).append(eq("EVALUATION_SUBMITTED"), any(), anyMap());
    }

    @Test
    void recipientCanEvaluateToo() {
        when(eligibilityRepository.findByShipmentId(shipmentId)).thenReturn(Optional.of(eligibility(NOW.minusDays(29))));
        when(repository.findFreightByPassengerAndShipment(recipient, shipmentId)).thenReturn(Optional.empty());

        var created = service.create(command(recipient, RaterRole.DESTINATARIO, null));

        assertThat(created.passengerId()).isEqualTo(recipient);
        assertThat(created.idempotencyKey()).isNull();
    }

    @Test
    void sameIdempotencyKeyReturnsTheSameEvaluation() {
        var existing = ExperienceEvaluation.submitFreight(eligibility(NOW.minusDays(1)), contractor,
                FreightScores.of(5, 5, 5), null, List.of(), "key-1", NOW);
        when(repository.findByIdempotencyKey(contractor, "key-1")).thenReturn(Optional.of(existing));

        assertThat(service.create(command(contractor, RaterRole.CONTRATANTE, "key-1"))).isSameAs(existing);
        verify(repository, never()).insertFreight(any());
        verify(outbox, never()).append(any(), any(), anyMap());
    }

    @Test
    void idempotencyKeyOfAnotherEvaluationIsAConflict() {
        var otherShipment = FreightEligibility.fromDelivery(UUID.randomUUID(), UUID.randomUUID(), boatmanId, contractor,
                recipient, NOW.minusDays(1), 30);
        var existing = ExperienceEvaluation.submitFreight(otherShipment, contractor, FreightScores.of(5, 5, 5), null,
                List.of(), "key-1", NOW);
        when(repository.findByIdempotencyKey(contractor, "key-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(command(contractor, RaterRole.CONTRATANTE, "key-1")))
                .isInstanceOf(EvaluationConflictException.class)
                .extracting(error -> ((EvaluationConflictException) error).code()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void idempotencyKeyOfATripEvaluationIsAConflict() {
        var evidence = new EligibilityEvidence(UUID.randomUUID(), contractor, UUID.randomUUID(), UUID.randomUUID(),
                boatmanId, UUID.randomUUID(), null, NOW.minusDays(1), NOW.plusDays(29));
        var trip = ExperienceEvaluation.submit(evidence, new EvaluationScores(new EvaluationScore(5), null, null, null,
                null, null, null), null, ManifestationType.COMPLIMENT, null, List.of(), true, "key-1", NOW);
        when(repository.findByIdempotencyKey(contractor, "key-1")).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> service.create(command(contractor, RaterRole.CONTRATANTE, "key-1")))
                .isInstanceOf(EvaluationConflictException.class);
    }

    @Test
    void secondEvaluationOfTheSameShipmentByTheSamePersonIsAConflict() {
        var existing = ExperienceEvaluation.submitFreight(eligibility(NOW.minusDays(1)), contractor,
                FreightScores.of(5, 5, 5), null, List.of(), null, NOW);
        when(repository.findFreightByPassengerAndShipment(contractor, shipmentId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(command(contractor, RaterRole.CONTRATANTE, "other-key")))
                .isInstanceOf(EvaluationConflictException.class)
                .extracting(error -> ((EvaluationConflictException) error).code()).isEqualTo("ALREADY_EVALUATED");
    }

    @Test
    void shipmentWithoutDeliveryIsIneligible() {
        when(repository.findFreightByPassengerAndShipment(contractor, shipmentId)).thenReturn(Optional.empty());
        when(eligibilityRepository.findByShipmentId(shipmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(command(contractor, RaterRole.CONTRATANTE, null)))
                .isInstanceOf(IneligibleEvaluationException.class).hasMessage("SHIPMENT_NOT_DELIVERED");
    }

    @Test
    void expiredWindowWrongBoatmanAndWrongRoleAreIneligible() {
        when(repository.findFreightByPassengerAndShipment(any(), eq(shipmentId))).thenReturn(Optional.empty());
        when(eligibilityRepository.findByShipmentId(shipmentId)).thenReturn(Optional.of(eligibility(NOW.minusDays(31))));
        assertThatThrownBy(() -> service.create(command(contractor, RaterRole.CONTRATANTE, null)))
                .hasMessage("EVALUATION_WINDOW_EXPIRED");

        when(eligibilityRepository.findByShipmentId(shipmentId)).thenReturn(Optional.of(eligibility(NOW.minusDays(1))));
        assertThatThrownBy(() -> service.create(command(recipient, RaterRole.CONTRATANTE, null)))
                .hasMessage("NOT_PARTICIPANT");
        assertThatThrownBy(() -> service.create(new CreateFreightEvaluationCommand(shipmentId, UUID.randomUUID(),
                contractor, RaterRole.CONTRATANTE, FreightScores.of(5, 5, 5), null, null, null)))
                .hasMessage("BOATMAN_MISMATCH");
        verify(repository, never()).insertFreight(any());
    }
}
