package br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.viafluvial.avaliacoesexperiencia.domain.exception.EvaluationConflictException;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EligibilityEvidence;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationScore;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationScores;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationStatus;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ExperienceEvaluation;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.FreightEligibility;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.FreightScores;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ManifestationType;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.TargetType;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** V006/V007 on a real PostgreSQL: CHECK per target type, uniqueness per shipment and person, trip rows intact. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({EvaluationPersistenceAdapter.class, FreightEligibilityPersistenceAdapter.class})
@Testcontainers(disabledWithoutDocker = true)
class FreightEvaluationPersistenceTest {

    private static final String TABLE = "\"sc-avaliacoes-experiencia\".experience_evaluation";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("db-avaliacoes-experiencia")
            .withUsername("avaliacoes_app")
            .withPassword("avaliacoes_app");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired EvaluationPersistenceAdapter evaluations;
    @Autowired FreightEligibilityPersistenceAdapter eligibilities;
    @Autowired JdbcTemplate jdbc;

    private final OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
    private final UUID boatmanId = UUID.randomUUID();

    private FreightEligibility eligibility(UUID shipmentId) {
        return FreightEligibility.fromDelivery(UUID.randomUUID(), shipmentId, boatmanId, UUID.randomUUID(),
                UUID.randomUUID(), now.minusDays(1), 30);
    }

    private ExperienceEvaluation freight(FreightEligibility eligibility, UUID rater, int punctuality) {
        return ExperienceEvaluation.submitFreight(eligibility, rater, FreightScores.of(punctuality, 4, 5),
                "Carga entregue com cuidado", List.of("CHEGOU_NO_HORARIO"), null, now);
    }

    private ExperienceEvaluation trip(int overall) {
        var evidence = new EligibilityEvidence(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                boatmanId, UUID.randomUUID(), null, now.minusDays(1), now.plusDays(29));
        return ExperienceEvaluation.submit(evidence, new EvaluationScores(new EvaluationScore(overall), null, null, null,
                null, null, null), null, ManifestationType.COMPLIMENT, "Viagem tranquila e pontual", List.of(), true,
                null, now);
    }

    @Test
    void persistsEligibilityAndFreightEvaluationRoundTrip() {
        var eligibility = eligibilities.save(eligibility(UUID.randomUUID()));
        assertThat(eligibilities.findByShipmentId(eligibility.shipmentId())).contains(eligibility);

        var saved = evaluations.insertFreight(freight(eligibility, eligibility.contractorUserId(), 5));
        var loaded = evaluations.findById(saved.id()).orElseThrow();

        assertThat(loaded.targetType()).isEqualTo(TargetType.FREIGHT);
        assertThat(loaded.shipmentId()).isEqualTo(eligibility.shipmentId());
        assertThat(loaded.tripId()).isNull();
        assertThat(loaded.freightScores()).isEqualTo(FreightScores.of(5, 4, 5));
        assertThat(loaded.scores().overall().value()).isEqualTo(5);
        assertThat(loaded.tags()).containsExactly("CHEGOU_NO_HORARIO");
        assertThat(evaluations.findFreightByPassengerAndShipment(eligibility.contractorUserId(), eligibility.shipmentId()))
                .isPresent();
        assertThat(jdbc.queryForObject("select target_type from " + TABLE + " where id = ?", String.class, saved.id()))
                .isEqualTo("FREIGHT");
    }

    @Test
    void secondFreightEvaluationOfTheSameShipmentByTheSamePersonIsAConflict() {
        var eligibility = eligibilities.save(eligibility(UUID.randomUUID()));
        evaluations.insertFreight(freight(eligibility, eligibility.contractorUserId(), 5));
        // The recipient may evaluate the same shipment.
        evaluations.insertFreight(freight(eligibility, eligibility.recipientUserId(), 4));

        assertThatThrownBy(() -> evaluations.insertFreight(freight(eligibility, eligibility.contractorUserId(), 3)))
                .isInstanceOf(EvaluationConflictException.class);
    }

    @Test
    void duplicateDeliveryEventIdIsRejected() {
        var first = eligibilities.save(eligibility(UUID.randomUUID()));
        var other = new FreightEligibility(UUID.randomUUID(), boatmanId, UUID.randomUUID(), null, now, now.plusDays(30),
                first.eventId());
        assertThatThrownBy(() -> eligibilities.save(other)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existingTripRowsAreTripAndTripAggregatesIgnoreFreight() {
        // Rows created before V006 (dsv seeds) received the TRIP default.
        assertThat(jdbc.queryForObject("select count(*) from " + TABLE + " where target_type <> 'TRIP'", Long.class))
                .isZero();

        var publishedTrip = trip(4);
        publishedTrip.publish(null, now);
        evaluations.save(publishedTrip);
        var eligibility = eligibilities.save(eligibility(UUID.randomUUID()));
        var publishedFreight = freight(eligibility, eligibility.contractorUserId(), 1);
        publishedFreight.publish(null, now);
        evaluations.insertFreight(publishedFreight);

        assertThat(evaluations.countPublishedByBoatman(boatmanId)).isEqualTo(1);
        assertThat(evaluations.averagePublishedByBoatman(boatmanId)).isEqualTo(4.0);
        assertThat(evaluations.countPublishedByBoatmanAndRating(boatmanId, 3)).isZero();
        assertThat(evaluations.findPublishedByBoatman(boatmanId, 0, 20).items()).extracting(ExperienceEvaluation::id)
                .containsExactly(publishedTrip.id());
        assertThat(evaluations.summarizePublishedByBoatmen(List.of(boatmanId))).singleElement()
                .satisfies(rating -> assertThat(rating.count()).isEqualTo(1));
        assertThat(evaluations.findByPassenger(eligibility.contractorUserId(), null, 0, 20).items()).isEmpty();
        assertThat(evaluations.findForModeration(EvaluationStatus.PUBLISHED, 3, 0, 20).items())
                .noneMatch(ExperienceEvaluation::isFreight);
        assertThat(evaluations.findPublishedTestimonials(1, 5, 50)).noneMatch(ExperienceEvaluation::isFreight);

        assertThat(evaluations.countPublishedByBoatman(boatmanId, TargetType.FREIGHT)).isEqualTo(1);
        assertThat(evaluations.averagePublishedByBoatman(boatmanId, TargetType.FREIGHT)).isEqualTo(3.0);
        assertThat(evaluations.countPublishedByBoatmanAndRating(boatmanId, TargetType.FREIGHT, 3)).isEqualTo(1);
        assertThat(evaluations.findPublishedByBoatman(boatmanId, TargetType.FREIGHT, 0, 20).items())
                .extracting(ExperienceEvaluation::id).containsExactly(publishedFreight.id());
        var criteria = evaluations.averagePublishedFreightCriteriaByBoatman(boatmanId);
        assertThat(criteria.punctuality()).isEqualTo(1.0);
        assertThat(criteria.cargoCare()).isEqualTo(4.0);
        assertThat(criteria.service()).isEqualTo(5.0);
        assertThat(evaluations.findForModeration(EvaluationStatus.PUBLISHED, null, null, 0, 100).items())
                .extracting(ExperienceEvaluation::id).contains(publishedTrip.id(), publishedFreight.id());
        assertThat(evaluations.findForModeration(EvaluationStatus.PUBLISHED, null, TargetType.FREIGHT, 0, 100).items())
                .allMatch(ExperienceEvaluation::isFreight);
    }

    @Test
    void emptyFreightCriteriaWhenNothingIsPublished() {
        var criteria = evaluations.averagePublishedFreightCriteriaByBoatman(UUID.randomUUID());
        assertThat(criteria.punctuality()).isNull();
        assertThat(criteria.service()).isNull();
    }

    @Test
    void tripRowStillRequiresTripReferences() {
        assertThatThrownBy(() -> jdbc.update("insert into " + TABLE + " (id, target_type, boatman_id, vessel_id, booking_id,"
                + " ticket_id, overall_score, manifestation_type, status, eligible_until, created_at, updated_at)"
                + " values (?, 'TRIP', ?, ?, ?, ?, 5, 'COMPLIMENT', 'SUBMITTED', now(), now(), now())",
                UUID.randomUUID(), boatmanId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void tripRowCannotCarryFreightScores() {
        assertThatThrownBy(() -> jdbc.update("insert into " + TABLE + " (id, trip_id, boatman_id, vessel_id, booking_id,"
                + " ticket_id, overall_score, score_punctuality, manifestation_type, status, eligible_until, created_at,"
                + " updated_at) values (?, ?, ?, ?, ?, ?, 5, 5, 'COMPLIMENT', 'SUBMITTED', now(), now(), now())",
                UUID.randomUUID(), UUID.randomUUID(), boatmanId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void freightRowRequiresShipmentAndAllCriteria() {
        assertThatThrownBy(() -> jdbc.update("insert into " + TABLE + " (id, target_type, boatman_id, overall_score,"
                + " score_punctuality, score_cargo_care, manifestation_type, status, eligible_until, created_at, updated_at)"
                + " values (?, 'FREIGHT', ?, 5, 5, 5, 'COMPLIMENT', 'SUBMITTED', now(), now(), now())",
                UUID.randomUUID(), boatmanId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void freightRowCannotReferenceATrip() {
        assertThatThrownBy(() -> jdbc.update("insert into " + TABLE + " (id, target_type, shipment_id, trip_id, boatman_id,"
                + " overall_score, score_punctuality, score_cargo_care, score_freight_service, manifestation_type, status,"
                + " eligible_until, created_at, updated_at)"
                + " values (?, 'FREIGHT', ?, ?, ?, 5, 5, 5, 5, 'COMPLIMENT', 'SUBMITTED', now(), now(), now())",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), boatmanId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void outOfRangeCriteriaAreRejected() {
        assertThatThrownBy(() -> jdbc.update("insert into " + TABLE + " (id, target_type, shipment_id, boatman_id,"
                + " overall_score, score_punctuality, score_cargo_care, score_freight_service, manifestation_type, status,"
                + " eligible_until, created_at, updated_at)"
                + " values (?, 'FREIGHT', ?, ?, 5, 6, 5, 5, 'COMPLIMENT', 'SUBMITTED', now(), now(), now())",
                UUID.randomUUID(), UUID.randomUUID(), boatmanId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownTargetTypeIsRejected() {
        assertThatThrownBy(() -> jdbc.update("insert into " + TABLE + " (id, target_type, shipment_id, boatman_id,"
                + " overall_score, score_punctuality, score_cargo_care, score_freight_service, manifestation_type, status,"
                + " eligible_until, created_at, updated_at)"
                + " values (?, 'TICKET', ?, ?, 5, 5, 5, 5, 'COMPLIMENT', 'SUBMITTED', now(), now(), now())",
                UUID.randomUUID(), UUID.randomUUID(), boatmanId)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
