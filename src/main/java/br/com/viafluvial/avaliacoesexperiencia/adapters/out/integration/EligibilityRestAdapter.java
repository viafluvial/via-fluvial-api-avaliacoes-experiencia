package br.com.viafluvial.avaliacoesexperiencia.adapters.out.integration;

import br.com.viafluvial.avaliacoesexperiencia.application.model.EligibilityDecision;
import br.com.viafluvial.avaliacoesexperiencia.application.port.out.EligibilityGatewayPort;
import br.com.viafluvial.avaliacoesexperiencia.domain.model.EligibilityEvidence;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class EligibilityRestAdapter implements EligibilityGatewayPort {

    private final RestClient trips;
    private final RestClient bookings;
    private final RestClient tickets;
    private final int windowDays;

    public EligibilityRestAdapter(RestClient.Builder builder,
            @Value("${integrations.trips.base-url:http://localhost:18008/api/v1}") String tripsUrl,
            @Value("${integrations.bookings.base-url:http://localhost:18009/api/v1}") String bookingsUrl,
            @Value("${integrations.tickets.base-url:http://localhost:18010/api/v1}") String ticketsUrl,
            @Value("${evaluation.window-days:30}") int windowDays) {
        this.trips = builder.clone().baseUrl(tripsUrl).build();
        this.bookings = builder.clone().baseUrl(bookingsUrl).build();
        this.tickets = builder.clone().baseUrl(ticketsUrl).build();
        this.windowDays = windowDays;
    }

    @Override
    public EligibilityDecision verify(UUID passengerId, UUID tripId) {
        try {
            JsonNode trip = trips.get().uri("/trips/{tripId}", tripId).retrieve().body(JsonNode.class);
            if (trip == null || !"COMPLETED".equalsIgnoreCase(text(trip, "status"))) {
                return EligibilityDecision.denied(tripId, "TRIP_NOT_COMPLETED", false);
            }
            OffsetDateTime completedAt = date(trip, "actualArrivalDateTime");
            if (completedAt == null) return EligibilityDecision.denied(tripId, "TRIP_COMPLETION_TIME_MISSING", false);

            JsonNode bookingResponse = bookings.get().uri(uri -> uri.path("/bookings")
                    .queryParam("tripId", tripId).queryParam("passengerId", passengerId).build())
                    .retrieve().body(JsonNode.class);
            JsonNode booking = firstEligibleBooking(bookingResponse);
            if (booking == null) return EligibilityDecision.denied(tripId, "BOOKING_NOT_CONFIRMED", false);
            UUID bookingId = uuid(booking, "bookingId");

            JsonNode ticketResponse = tickets.get().uri("/bookings/{bookingId}/tickets", bookingId)
                    .retrieve().body(JsonNode.class);
            JsonNode ticket = firstEmbarkedTicket(ticketResponse);
            if (ticket == null) return EligibilityDecision.denied(tripId, "NO_EMBARKED_TICKET", false);

            UUID boatmanId = uuid(trip, "boatmanId");
            UUID vesselId = uuid(trip, "vesselId");
            if (boatmanId.equals(passengerId)) return EligibilityDecision.denied(tripId, "SELF_EVALUATION_NOT_ALLOWED", false);
            var evidence = new EligibilityEvidence(tripId, passengerId, bookingId, uuid(ticket, "ticketId"),
                    boatmanId, vesselId, optionalUuid(trip, "routeId"), completedAt, completedAt.plusDays(windowDays));
            if (!evidence.isEligibleAt(OffsetDateTime.now())) return EligibilityDecision.denied(tripId, "EVALUATION_WINDOW_EXPIRED", false);
            return EligibilityDecision.allowed(evidence);
        } catch (RestClientException | IllegalArgumentException exception) {
            return EligibilityDecision.denied(tripId, "ELIGIBILITY_DEPENDENCY_UNAVAILABLE", false);
        }
    }

    private JsonNode firstEligibleBooking(JsonNode response) {
        JsonNode data = response == null ? null : response.path("data");
        if (data == null || !data.isArray()) return null;
        for (JsonNode item : data) {
            String status = text(item, "status");
            if ("CONFIRMED".equalsIgnoreCase(status) || "CHECKED_IN".equalsIgnoreCase(status)) return item;
        }
        return null;
    }

    private JsonNode firstEmbarkedTicket(JsonNode response) {
        JsonNode data = response == null ? null : response.path("data");
        if (data == null || !data.isArray()) return null;
        for (JsonNode item : data) {
            if ("used".equalsIgnoreCase(text(item, "status")) || !item.path("embarkedAt").isMissingNode()
                    && !item.path("embarkedAt").isNull()) return item;
        }
        return null;
    }

    private String text(JsonNode node, String field) { return node.path(field).asText(""); }
    private UUID uuid(JsonNode node, String field) { return UUID.fromString(node.path(field).asText()); }
    private UUID optionalUuid(JsonNode node, String field) { return node.path(field).isTextual() ? UUID.fromString(node.path(field).asText()) : null; }
    private OffsetDateTime date(JsonNode node, String field) { return node.path(field).isTextual() ? OffsetDateTime.parse(node.path(field).asText()) : null; }
}