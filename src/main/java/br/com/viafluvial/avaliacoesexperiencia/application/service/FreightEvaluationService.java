package br.com.viafluvial.avaliacoesexperiencia.application.service;

import br.com.viafluvial.avaliacoesexperiencia.application.model.CreateFreightEvaluationCommand;
import br.com.viafluvial.avaliacoesexperiencia.application.model.FreightDeliveredCommand;
import br.com.viafluvial.avaliacoesexperiencia.application.model.PageResult;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.EvaluationRepositoryPort;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.FreightEligibilityRepositoryPort;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.OutboxPort;
import br.com.viafluvial.avaliacoesexperiencia.domain.exception.EvaluationConflictException;
import br.com.viafluvial.avaliacoesexperiencia.domain.exception.IneligibleEvaluationException;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationStatus;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ExperienceEvaluation;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.FreightEligibility;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Freight evaluations (target_type = FREIGHT): eligibility registered from freight.delivered and evaluations
 * submitted by api-fretes-cargas on behalf of the contractor or recipient of the shipment.
 */
@Service
public class FreightEvaluationService {

    public static final String FREIGHT_DELIVERED_EVENT = "FREIGHT_DELIVERED";
    public static final String FREIGHT_EVALUATION_SUBMITTED = "FREIGHT_EVALUATION_SUBMITTED";
    public static final String ALREADY_EVALUATED = "ALREADY_EVALUATED";
    public static final String IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED";
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private final EvaluationRepositoryPort repository;
    private final FreightEligibilityRepositoryPort eligibilityRepository;
    private final OutboxPort outbox;
    private final Clock clock;
    private final int windowDays;

    public FreightEvaluationService(EvaluationRepositoryPort repository,
            FreightEligibilityRepositoryPort eligibilityRepository, OutboxPort outbox, Clock clock,
            @Value("${evaluation.freight-window-days:30}") int windowDays) {
        this.repository = repository;
        this.eligibilityRepository = eligibilityRepository;
        this.outbox = outbox;
        this.clock = clock;
        this.windowDays = windowDays;
    }

    /**
     * Registers the evaluation window of a delivered shipment (expires_at = deliveredAt + window). Idempotent by
     * eventId through processed_event; a shipment keeps the window of its first delivery event.
     *
     * @return true on the first delivery of the event, false when it was already processed
     */
    @Transactional
    public boolean receiveDelivered(FreightDeliveredCommand command) {
        var eligibility = FreightEligibility.fromDelivery(command.eventId(), command.shipmentId(),
                command.boatmanId(), command.contractorUserId(), command.recipientUserId(), command.deliveredAt(),
                windowDays);
        boolean firstDelivery = outbox.registerIncoming(command.eventId(), FREIGHT_DELIVERED_EVENT,
                command.shipmentId().toString());
        if (!firstDelivery) return false;
        if (eligibilityRepository.findByShipmentId(command.shipmentId()).isPresent()) return false;
        eligibilityRepository.save(eligibility);
        return true;
    }

    @Transactional
    public ExperienceEvaluation create(CreateFreightEvaluationCommand command) {
        String key = normalize(command.idempotencyKey());
        if (key != null) {
            Optional<ExperienceEvaluation> existing = repository.findByIdempotencyKey(command.raterUserId(), key);
            if (existing.isPresent()) return replay(existing.get(), command);
        }
        if (repository.findFreightByPassengerAndShipment(command.raterUserId(), command.shipmentId()).isPresent()) {
            throw new EvaluationConflictException("Rater already evaluated this shipment", ALREADY_EVALUATED);
        }
        FreightEligibility eligibility = eligibilityRepository.findByShipmentId(command.shipmentId())
                .orElseThrow(() -> new IneligibleEvaluationException(FreightEligibility.NOT_DELIVERED));
        OffsetDateTime now = OffsetDateTime.now(clock);
        eligibility.verify(command.boatmanId(), command.raterUserId(), command.raterRole(), now);

        var evaluation = ExperienceEvaluation.submitFreight(eligibility, command.raterUserId(), command.scores(),
                command.comment(), command.tags() == null ? List.of() : command.tags(), key, now);
        var saved = repository.insertFreight(evaluation);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evaluationId", saved.id());
        payload.put("targetType", saved.targetType().name());
        payload.put("shipmentId", saved.shipmentId());
        payload.put("boatmanId", saved.boatmanId());
        payload.put("raterRole", command.raterRole().name());
        payload.put("overallScore", saved.scores().overall().value());
        payload.put("status", saved.status().name());
        payload.put("idempotencyKey", key == null ? "" : key);
        outbox.append(FREIGHT_EVALUATION_SUBMITTED, saved.id(), payload);
        return saved;
    }

    /**
     * Internal (SERVICE) read of FREIGHT evaluations in any moderation status, newest first, optionally filtered by
     * boatman and status. Trip evaluations are never returned.
     *
     * @throws IllegalArgumentException when page is negative or size is outside 1..{@value #MAX_PAGE_SIZE}
     */
    @Transactional(readOnly = true)
    public PageResult<ExperienceEvaluation> list(UUID boatmanId, EvaluationStatus status, Integer page, Integer size) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (pageNumber < 0) throw new IllegalArgumentException("page must be greater than or equal to 0");
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return repository.findFreight(boatmanId, status, pageNumber, pageSize);
    }

    /** Same idempotency key returns the same evaluation; reusing it for another evaluation is a conflict. */
    private ExperienceEvaluation replay(ExperienceEvaluation existing, CreateFreightEvaluationCommand command) {
        if (!existing.isFreight() || !Objects.equals(existing.shipmentId(), command.shipmentId())) {
            throw new EvaluationConflictException("Idempotency key already used for another evaluation",
                    IDEMPOTENCY_KEY_REUSED);
        }
        return existing;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
