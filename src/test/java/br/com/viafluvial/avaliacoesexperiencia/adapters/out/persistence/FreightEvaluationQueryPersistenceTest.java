package br.com.viafluvial.avaliacoesexperiencia.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.viafluvial.avaliacoesexperiencia.domain.model.EligibilityEvidence;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationScore;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationScores;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationStatus;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ExperienceEvaluation;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.FreightEligibility;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.FreightScores;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ManifestationType;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Internal freight evaluation query (GET /internal/avaliacoes-frete) on a real PostgreSQL. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(EvaluationPersistenceAdapter.class)
@Testcontainers(disabledWithoutDocker = true)
class FreightEvaluationQueryPersistenceTest {

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

    private final OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
    private final UUID boatmanId = UUID.randomUUID();
    private final UUID otherBoatmanId = UUID.randomUUID();

    private ExperienceEvaluation freight(UUID boatman, int minutesAgo) {
        var eligibility = FreightEligibility.fromDelivery(UUID.randomUUID(), UUID.randomUUID(), boatman,
                UUID.randomUUID(), null, now.minusDays(1), 30);
        return ExperienceEvaluation.submitFreight(eligibility, eligibility.contractorUserId(), FreightScores.of(5, 4, 4),
                "Carga entregue com cuidado", List.of("CHEGOU_NO_HORARIO"), null, now.minusMinutes(minutesAgo));
    }

    private ExperienceEvaluation trip(UUID boatman) {
        var evidence = new EligibilityEvidence(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                boatman, UUID.randomUUID(), null, now.minusDays(1), now.plusDays(29));
        return ExperienceEvaluation.submit(evidence, new EvaluationScores(new EvaluationScore(5), null, null, null,
                null, null, null), null, ManifestationType.COMPLIMENT, "Viagem tranquila e pontual", List.of(), true,
                null, now);
    }

    @Test
    void returnsOnlyFreightEvaluationsOfTheBoatmanNewestFirstWithModerationAndReply() {
        var oldest = freight(boatmanId, 30);
        var published = freight(boatmanId, 20);
        published.publish("Conteudo adequado", now.minusMinutes(15));
        published.respond(boatmanId, "Obrigado pela confianca", now.minusMinutes(10));
        var rejected = freight(boatmanId, 10);
        rejected.reject("Comentario ofensivo", now.minusMinutes(5));
        List.of(oldest, published, rejected).forEach(evaluations::insertFreight);
        evaluations.insertFreight(freight(otherBoatmanId, 1));
        var tripOfTheSameBoatman = trip(boatmanId);
        tripOfTheSameBoatman.publish(null, now);
        evaluations.save(tripOfTheSameBoatman);

        var page = evaluations.findFreight(boatmanId, null, 0, 20);

        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.items()).allMatch(ExperienceEvaluation::isFreight)
                .extracting(ExperienceEvaluation::id)
                .containsExactly(rejected.id(), published.id(), oldest.id());
        var loaded = page.items().get(1);
        assertThat(loaded.status()).isEqualTo(EvaluationStatus.PUBLISHED);
        assertThat(loaded.moderationReason()).isEqualTo("Conteudo adequado");
        assertThat(loaded.boatmanResponse()).isEqualTo("Obrigado pela confianca");
        assertThat(loaded.freightScores()).isEqualTo(FreightScores.of(5, 4, 4));
        assertThat(page.items().get(0).moderationReason()).isEqualTo("Comentario ofensivo");
    }

    @Test
    void filtersByModerationStatus() {
        var submitted = freight(boatmanId, 3);
        var published = freight(boatmanId, 2);
        published.publish(null, now);
        var otherPublished = freight(otherBoatmanId, 1);
        otherPublished.publish(null, now);
        List.of(submitted, published, otherPublished).forEach(evaluations::insertFreight);

        assertThat(evaluations.findFreight(boatmanId, EvaluationStatus.PUBLISHED, 0, 20).items())
                .extracting(ExperienceEvaluation::id).containsExactly(published.id());
        assertThat(evaluations.findFreight(boatmanId, EvaluationStatus.SUBMITTED, 0, 20).items())
                .extracting(ExperienceEvaluation::id).containsExactly(submitted.id());
        assertThat(evaluations.findFreight(boatmanId, EvaluationStatus.REJECTED, 0, 20).items()).isEmpty();
        assertThat(evaluations.findFreight(null, EvaluationStatus.PUBLISHED, 0, 100).items())
                .allMatch(ExperienceEvaluation::isFreight)
                .allMatch(evaluation -> evaluation.status() == EvaluationStatus.PUBLISHED)
                .extracting(ExperienceEvaluation::id).contains(published.id(), otherPublished.id());
    }

    @Test
    void withoutFiltersListsFreightOfEveryBoatmanAndNeverTrips() {
        var mine = evaluations.insertFreight(freight(boatmanId, 2));
        var other = evaluations.insertFreight(freight(otherBoatmanId, 1));
        var tripEvaluation = evaluations.save(trip(boatmanId));

        var page = evaluations.findFreight(null, null, 0, 100);

        assertThat(page.items()).allMatch(ExperienceEvaluation::isFreight)
                .extracting(ExperienceEvaluation::id).contains(mine.id(), other.id())
                .doesNotContain(tripEvaluation.id());
    }

    @Test
    void paginatesWithStableOrder() {
        var evaluationsOfTheBoatman = List.of(freight(boatmanId, 5), freight(boatmanId, 4), freight(boatmanId, 3),
                freight(boatmanId, 2), freight(boatmanId, 1));
        evaluationsOfTheBoatman.forEach(evaluations::insertFreight);

        var first = evaluations.findFreight(boatmanId, null, 0, 2);
        var second = evaluations.findFreight(boatmanId, null, 1, 2);
        var last = evaluations.findFreight(boatmanId, null, 2, 2);
        var beyond = evaluations.findFreight(boatmanId, null, 3, 2);

        assertThat(first.totalElements()).isEqualTo(5);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(2);
        assertThat(first.items()).extracting(ExperienceEvaluation::id)
                .containsExactly(evaluationsOfTheBoatman.get(4).id(), evaluationsOfTheBoatman.get(3).id());
        assertThat(second.items()).extracting(ExperienceEvaluation::id)
                .containsExactly(evaluationsOfTheBoatman.get(2).id(), evaluationsOfTheBoatman.get(1).id());
        assertThat(last.items()).extracting(ExperienceEvaluation::id)
                .containsExactly(evaluationsOfTheBoatman.get(0).id());
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.page()).isEqualTo(3);
        assertThat(beyond.totalElements()).isEqualTo(5);
    }
}
