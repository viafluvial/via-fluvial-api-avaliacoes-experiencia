package br.com.viafluvial.avaliacoesexperiencia.adapters.in.web;

import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.EvaluationPage;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.EvaluationResponse;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.FreightEvaluationItem;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.FreightEvaluationPage;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.FreightScores;
import br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.Scores;
import br.com.viafluvial.avaliacoesexperiencia.application.model.PageResult;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationScore;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EvaluationScores;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.ExperienceEvaluation;
import java.util.ArrayList;
import org.springframework.stereotype.Component;

@Component
public class EvaluationWebMapper {
    public EvaluationScores toDomain(Scores value) {
        if (value == null) return null;
        return new EvaluationScores(score(value.getOverall()), score(value.getPlatform()), score(value.getBoatman()),
                score(value.getVessel()), score(value.getRoute()), score(value.getBoarding()), score(value.getService()));
    }

    public EvaluationResponse toResponse(ExperienceEvaluation value) {
        Scores scores = new Scores(value.scores().overall().value())
                .platform(raw(value.scores().platform())).boatman(raw(value.scores().boatman()))
                .vessel(raw(value.scores().vessel())).route(raw(value.scores().route()))
                .boarding(raw(value.scores().boarding())).service(raw(value.scores().service()));
        var response = new EvaluationResponse(value.id(), scores,
                br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.ManifestationType.valueOf(value.manifestationType().name()),
                br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.EvaluationStatus.valueOf(value.status().name()),
                true, value.anonymousPublicDisplay(), value.createdAt(), value.updatedAt());
        response.targetType(toDto(value.targetType())).tripId(value.tripId()).shipmentId(value.shipmentId());
        if (value.freightScores() != null) {
            response.freightScores(new FreightScores(value.freightScores().punctuality().value(),
                    value.freightScores().cargoCare().value(), value.freightScores().service().value()));
        }
        return response.boatmanId(value.boatmanId()).vesselId(value.vesselId()).routeId(value.routeId())
                .npsScore(value.npsScore() == null ? null : value.npsScore().value()).comment(value.comment())
                .tags(new ArrayList<>(value.tags())).boatmanResponse(value.boatmanResponse())
                .moderationReason(value.moderationReason());
    }

    public EvaluationPage toPage(PageResult<ExperienceEvaluation> page) {
        return new EvaluationPage(page.items().stream().map(this::toResponse).toList(), page.page(), page.size(),
                page.totalElements(), page.totalPages());
    }

    /** Internal (SERVICE) view of a freight evaluation: moderation status, reason and the carrier reply. */
    public FreightEvaluationItem toFreightItem(ExperienceEvaluation value) {
        var scores = value.freightScores();
        var item = new FreightEvaluationItem(value.id(), toDto(value.targetType()), value.shipmentId(), value.boatmanId(),
                value.scores().overall().value(),
                new FreightScores(scores.punctuality().value(), scores.cargoCare().value(), scores.service().value()),
                br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.EvaluationStatus.valueOf(value.status().name()),
                value.createdAt(), value.updatedAt());
        return item.raterUserId(value.passengerId()).comment(value.comment()).tags(new ArrayList<>(value.tags()))
                .moderationReason(value.moderationReason()).boatmanResponse(value.boatmanResponse());
    }

    public FreightEvaluationPage toFreightPage(PageResult<ExperienceEvaluation> page) {
        return new FreightEvaluationPage(page.items().stream().map(this::toFreightItem).toList(), page.page(),
                page.size(), page.totalElements(), page.totalPages());
    }

    public br.com.viafluvial.avaliacoesexperiencia.domain.model.TargetType toDomain(
            br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.TargetType value) {
        return value == null ? null : br.com.viafluvial.avaliacoesexperiencia.domain.model.TargetType.valueOf(value.name());
    }

    public br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.TargetType toDto(
            br.com.viafluvial.avaliacoesexperiencia.domain.model.TargetType value) {
        return value == null ? null
                : br.com.viafluvial.avaliacoesexperiencia.adapters.in.web.generated.dto.TargetType.valueOf(value.name());
    }

    private EvaluationScore score(Integer value) { return value == null ? null : new EvaluationScore(value); }
    private Integer raw(EvaluationScore value) { return value == null ? null : value.value(); }
}