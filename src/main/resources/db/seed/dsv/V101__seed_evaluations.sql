INSERT INTO "sc-avaliacoes-experiencia".experience_evaluation (
    id, trip_id, passenger_id, booking_id, ticket_id, boatman_id, vessel_id, route_id,
    overall_score, platform_score, boatman_score, vessel_score, route_score, boarding_score,
    service_score, nps_score, manifestation_type, comment, status, anonymous_public_display,
    eligible_until, created_at, updated_at, version
) VALUES
('ae000001-0000-4000-8000-000000000001', 'ae100001-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111',
 'ae200001-0000-4000-8000-000000000001', 'ae300001-0000-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222',
 'ae400001-0000-4000-8000-000000000001', 'ae500001-0000-4000-8000-000000000001', 5, 5, 5, 4, 5, 4, 5, 10,
 'COMPLIMENT', 'Viagem pontual e atendimento cuidadoso.', 'PUBLISHED', TRUE,
 CURRENT_TIMESTAMP + INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP - INTERVAL '9 days', 0),
('ae000001-0000-4000-8000-000000000002', 'ae100001-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111',
 'ae200001-0000-4000-8000-000000000002', 'ae300001-0000-4000-8000-000000000002', '22222222-2222-4222-8222-222222222222',
 'ae400001-0000-4000-8000-000000000002', 'ae500001-0000-4000-8000-000000000002', 3, 4, 3, 3, 4, 2, 3, 7,
 'SUGGESTION', 'A organizacao do embarque pode melhorar.', 'UNDER_REVIEW', TRUE,
 CURRENT_TIMESTAMP + INTERVAL '25 days', CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP - INTERVAL '5 days', 0)
ON CONFLICT (id) DO NOTHING;