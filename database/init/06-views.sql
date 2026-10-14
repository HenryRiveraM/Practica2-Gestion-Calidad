-- =====================================================================
-- views.sql  |  Ejecutar después de schema.sql
-- Definiciones:
--   registrado  = attendance con status <> 'CANCELLED'
--   asistente   = attendance con status = 'CHECKED_IN'
--   NUEVO       = primer evento al que el participante asistió (check-in)
--   RECURRENTE  = asistencia n >= 2 (ordenada por fecha del evento)
--   participación = asistente con >= 1 interacción en actividades
--   conversión  = asistente con >= 1 fila en conversions
--   NPS         = %promotores(9-10) - %detractores(0-6)
-- =====================================================================
BEGIN;

DROP VIEW IF EXISTS vw_campaign_performance      CASCADE;
DROP VIEW IF EXISTS vw_product_analysis          CASCADE;
DROP VIEW IF EXISTS vw_activity_analysis         CASCADE;
DROP VIEW IF EXISTS vw_participant_analysis      CASCADE;
DROP VIEW IF EXISTS vw_event_performance         CASCADE;
DROP VIEW IF EXISTS vw_participant_event_history CASCADE;

-- 1) Base de la lógica nuevo / recurrente (una fila por asistencia efectiva)
CREATE VIEW vw_participant_event_history AS
WITH ranked AS (
    SELECT a.participant_id,
           a.event_id,
           e.event_date,
           a.check_in_at,
           ROW_NUMBER() OVER (PARTITION BY a.participant_id
                              ORDER BY e.event_date, a.check_in_at, a.event_id) AS attendance_number
    FROM attendances a
    JOIN events e ON e.id = a.event_id
    WHERE a.status = 'CHECKED_IN'
)
SELECT participant_id, event_id, event_date, check_in_at, attendance_number,
       CASE WHEN attendance_number = 1 THEN 'NEW' ELSE 'RECURRENT' END AS participant_type
FROM ranked;

-- 2) Dashboard / comparación de eventos (una fila por evento)
CREATE VIEW vw_event_performance AS
SELECT
    e.id AS event_id, e.name AS event_name, e.type AS event_type, e.event_date, e.location, e.status,
    e.campaign_id, c.name AS campaign_name, e.budget, e.expected_participants,
    r.registered, r.attended,
    ROUND(100.0 * r.attended / NULLIF(r.registered, 0), 1)                 AS attendance_rate_pct,
    ROUND(100.0 * r.attended / NULLIF(e.expected_participants, 0), 1)      AS expected_fill_pct,
    h.new_participants, h.recurrent_participants,
    i.total_interactions, i.participating_participants,
    ROUND(100.0 * i.participating_participants / NULLIF(r.attended, 0), 1) AS participation_rate_pct,
    ROUND(1.0 * i.total_interactions / NULLIF(r.attended, 0), 2)           AS avg_interactions_per_attendee,
    pi.product_interactions, pi.avg_product_rating, pi.high_intent_pct,
    cv.total_conversions, cv.converted_participants,
    ROUND(100.0 * cv.converted_participants / NULLIF(r.attended, 0), 1)    AS conversion_rate_pct,
    s.surveys_count, s.avg_overall_satisfaction, s.avg_organization, s.avg_service, s.avg_experience, s.avg_product_score,
    s.nps,
    ROUND(e.budget / NULLIF(r.attended, 0), 2)                             AS cost_per_attendee
FROM events e
LEFT JOIN campaigns c ON c.id = e.campaign_id
LEFT JOIN LATERAL (
    SELECT COUNT(*) FILTER (WHERE status <> 'CANCELLED') AS registered,
           COUNT(*) FILTER (WHERE status = 'CHECKED_IN') AS attended
    FROM attendances WHERE event_id = e.id) r ON TRUE
LEFT JOIN LATERAL (
    SELECT COUNT(*) FILTER (WHERE participant_type = 'NEW')       AS new_participants,
           COUNT(*) FILTER (WHERE participant_type = 'RECURRENT') AS recurrent_participants
    FROM vw_participant_event_history WHERE event_id = e.id) h ON TRUE
LEFT JOIN LATERAL (
    SELECT COUNT(*) AS total_interactions, COUNT(DISTINCT participant_id) AS participating_participants
    FROM interactions WHERE event_id = e.id) i ON TRUE
LEFT JOIN LATERAL (
    SELECT COUNT(*) AS product_interactions,
           ROUND(AVG(rating), 2) AS avg_product_rating,
           ROUND(100.0 * COUNT(*) FILTER (WHERE purchase_intent = 'HIGH')
                 / NULLIF(COUNT(purchase_intent), 0), 1) AS high_intent_pct
    FROM product_interactions WHERE event_id = e.id) pi ON TRUE
LEFT JOIN LATERAL (
    SELECT COUNT(*) AS total_conversions, COUNT(DISTINCT participant_id) AS converted_participants
    FROM conversions WHERE event_id = e.id) cv ON TRUE
LEFT JOIN LATERAL (
    SELECT COUNT(*) AS surveys_count,
           ROUND(AVG(overall_satisfaction), 2) AS avg_overall_satisfaction,
           ROUND(AVG(organization_score), 2)   AS avg_organization,
           ROUND(AVG(service_score), 2)        AS avg_service,
           ROUND(AVG(experience_score), 2)     AS avg_experience,
           ROUND(AVG(product_score), 2)        AS avg_product_score,
           ROUND(100.0 * (COUNT(*) FILTER (WHERE nps_score >= 9) - COUNT(*) FILTER (WHERE nps_score <= 6))
                 / NULLIF(COUNT(*), 0), 1)     AS nps
    FROM surveys WHERE event_id = e.id) s ON TRUE;

-- 3) Análisis y lealtad por participante
CREATE VIEW vw_participant_analysis AS
SELECT
    p.id AS participant_id,
    p.first_name || ' ' || p.last_name AS full_name,
    p.city, p.age_range, p.product_preference, p.communication_consent, p.registration_source,
    COALESCE(h.events_attended, 0) AS events_attended,
    h.first_event_date, h.last_event_date,
    CASE COALESCE(h.events_attended, 0)
         WHEN 0 THEN 'NO_ATTENDANCE' WHEN 1 THEN 'NEW' ELSE 'RECURRENT' END AS loyalty_status,
    (SELECT COUNT(*) FROM interactions i WHERE i.participant_id = p.id)          AS total_interactions,
    (SELECT COUNT(*) FROM conversions  c WHERE c.participant_id = p.id)          AS total_conversions,
    (SELECT ROUND(AVG(s.overall_satisfaction), 2) FROM surveys s WHERE s.participant_id = p.id) AS avg_satisfaction,
    (SELECT ROUND(AVG(s.nps_score), 1)            FROM surveys s WHERE s.participant_id = p.id) AS avg_nps_score
FROM participants p
LEFT JOIN LATERAL (
    SELECT COUNT(*) AS events_attended, MIN(event_date) AS first_event_date, MAX(event_date) AS last_event_date
    FROM vw_participant_event_history WHERE participant_id = p.id) h ON TRUE;

-- 4) Popularidad de actividades (ranking dentro de cada evento)
CREATE VIEW vw_activity_analysis AS
SELECT
    a.id AS activity_id, a.event_id, e.name AS event_name, a.name AS activity_name, a.type, a.status,
    COUNT(i.id)                      AS total_interactions,
    COUNT(DISTINCT i.participant_id) AS unique_participants,
    ROUND(100.0 * COUNT(DISTINCT i.participant_id)
          / NULLIF((SELECT COUNT(*) FROM attendances x WHERE x.event_id = a.event_id AND x.status = 'CHECKED_IN'), 0), 1)
                                     AS participation_rate_pct,
    RANK() OVER (PARTITION BY a.event_id ORDER BY COUNT(DISTINCT i.participant_id) DESC) AS popularity_rank
FROM activities a
JOIN events e ON e.id = a.event_id
LEFT JOIN interactions i ON i.activity_id = a.id
GROUP BY a.id, a.event_id, e.name, a.name, a.type, a.status;

-- 5) Productos por evento (rating e intención de compra)
CREATE VIEW vw_product_analysis AS
SELECT
    ep.event_id, e.name AS event_name, p.id AS product_id, p.name AS product_name, p.category, p.flavor, p.presentation,
    COUNT(pi.id)                                              AS total_interactions,
    COUNT(*) FILTER (WHERE pi.tried)                          AS total_tried,
    ROUND(AVG(pi.rating), 2)                                  AS avg_rating,
    COUNT(*) FILTER (WHERE pi.purchase_intent = 'LOW')        AS intent_low,
    COUNT(*) FILTER (WHERE pi.purchase_intent = 'MEDIUM')     AS intent_medium,
    COUNT(*) FILTER (WHERE pi.purchase_intent = 'HIGH')       AS intent_high,
    ROUND(AVG(CASE pi.purchase_intent WHEN 'LOW' THEN 1 WHEN 'MEDIUM' THEN 2 WHEN 'HIGH' THEN 3 END), 2) AS avg_intent_score
FROM event_products ep
JOIN events   e ON e.id = ep.event_id
JOIN products p ON p.id = ep.product_id
LEFT JOIN product_interactions pi ON pi.event_id = ep.event_id AND pi.product_id = ep.product_id
GROUP BY ep.event_id, e.name, p.id, p.name, p.category, p.flavor, p.presentation;

-- 6) Resultados por campaña
CREATE VIEW vw_campaign_performance AS
SELECT
    c.id AS campaign_id, c.name AS campaign_name, c.status, c.budget AS campaign_budget,
    COUNT(DISTINCT e.id)                         AS events_count,
    COALESCE(SUM(ep.registered), 0)              AS registered,
    COALESCE(SUM(ep.attended), 0)                AS attended,
    COALESCE(SUM(ep.total_interactions), 0)      AS total_interactions,
    COALESCE(SUM(ep.total_conversions), 0)       AS total_conversions,
    COALESCE(SUM(ep.converted_participants), 0)  AS converted_participants,
    ROUND(100.0 * SUM(ep.converted_participants) / NULLIF(SUM(ep.attended), 0), 1) AS conversion_rate_pct,
    (SELECT COUNT(DISTINCT a.participant_id) FROM attendances a JOIN events x ON x.id = a.event_id
      WHERE x.campaign_id = c.id AND a.status = 'CHECKED_IN')                      AS unique_attendees,
    (SELECT ROUND(AVG(s.overall_satisfaction), 2) FROM surveys s JOIN events x ON x.id = s.event_id
      WHERE x.campaign_id = c.id)                                                  AS avg_satisfaction,
    (SELECT ROUND(100.0 * (COUNT(*) FILTER (WHERE s.nps_score >= 9) - COUNT(*) FILTER (WHERE s.nps_score <= 6))
             / NULLIF(COUNT(*), 0), 1) FROM surveys s JOIN events x ON x.id = s.event_id
      WHERE x.campaign_id = c.id)                                                  AS nps
FROM campaigns c
LEFT JOIN events e ON e.campaign_id = c.id
LEFT JOIN vw_event_performance ep ON ep.event_id = e.id
GROUP BY c.id, c.name, c.status, c.budget;

COMMIT;
