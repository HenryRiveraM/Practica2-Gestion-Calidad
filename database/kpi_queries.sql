-- =====================================================================
-- kpi_queries.sql  |  Consultas de KPIs (globales; añade WHERE event_id = ? para un evento)
-- =====================================================================

-- 1. Total de registrados (no cancelados)
SELECT COUNT(*) AS total_registered FROM attendances WHERE status <> 'CANCELLED';

-- 2. Total de asistentes (check-ins)
SELECT COUNT(*) AS total_attended FROM attendances WHERE status = 'CHECKED_IN';

-- 3. Tasa de asistencia por evento
SELECT event_id, event_name, registered, attended, attendance_rate_pct
FROM vw_event_performance ORDER BY event_date;

-- 4. Participantes nuevos vs recurrentes por evento
SELECT event_id, event_name, new_participants, recurrent_participants
FROM vw_event_performance ORDER BY event_date;

-- 4b. Totales globales de la base: personas NEW (1 evento) vs RECURRENT (2+)
SELECT loyalty_status, COUNT(*) AS participants
FROM vw_participant_analysis GROUP BY loyalty_status ORDER BY loyalty_status;

-- 5. Participación por actividad
SELECT event_name, activity_name, type, unique_participants, total_interactions, participation_rate_pct
FROM vw_activity_analysis ORDER BY event_id, popularity_rank;

-- 6. Actividad más popular (global, por participantes únicos)
SELECT a.name AS activity_name, e.name AS event_name, COUNT(DISTINCT i.participant_id) AS unique_participants
FROM interactions i
JOIN activities a ON a.id = i.activity_id
JOIN events e ON e.id = i.event_id
GROUP BY a.id, a.name, e.name
ORDER BY unique_participants DESC LIMIT 1;

-- 6b. Participación promedio por actividad
SELECT ROUND(AVG(unique_participants), 1) AS avg_participants_per_activity FROM vw_activity_analysis;

-- 7. Productos más interactuados (global) + rating promedio
SELECT p.name AS product_name,
       COUNT(*) AS total_interactions,
       COUNT(*) FILTER (WHERE pi.tried) AS total_tried,
       ROUND(AVG(pi.rating), 2) AS avg_rating
FROM product_interactions pi
JOIN products p ON p.id = pi.product_id
GROUP BY p.id, p.name
ORDER BY total_interactions DESC;

-- 8. Distribución de intención de compra
SELECT purchase_intent, COUNT(*) AS total,
       ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 1) AS pct
FROM product_interactions WHERE purchase_intent IS NOT NULL
GROUP BY purchase_intent ORDER BY CASE purchase_intent WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END;

-- 8b. Producto con mayor interés (score de intención 1-3, mín. 5 evaluaciones)
SELECT p.name AS product_name, COUNT(pi.purchase_intent) AS evaluations,
       ROUND(AVG(CASE pi.purchase_intent WHEN 'LOW' THEN 1 WHEN 'MEDIUM' THEN 2 WHEN 'HIGH' THEN 3 END), 2) AS avg_intent_score
FROM product_interactions pi JOIN products p ON p.id = pi.product_id
GROUP BY p.id, p.name HAVING COUNT(pi.purchase_intent) >= 5
ORDER BY avg_intent_score DESC, evaluations DESC LIMIT 1;

-- 9. Total de conversiones y por tipo
SELECT conversion_type, COUNT(*) AS total FROM conversions GROUP BY conversion_type ORDER BY total DESC;
SELECT COUNT(*) AS total_conversions FROM conversions;

-- 10. Tasa de conversión por evento (asistentes con >= 1 conversión / asistentes)
SELECT event_name, attended, converted_participants, total_conversions, conversion_rate_pct
FROM vw_event_performance ORDER BY event_date;

-- 10b. Conversiones por campaña
SELECT campaign_name, events_count, attended, total_conversions, conversion_rate_pct
FROM vw_campaign_performance ORDER BY campaign_id;

-- 11. Satisfacción promedio y detalle de experiencia
SELECT ROUND(AVG(overall_satisfaction), 2) AS avg_satisfaction,
       ROUND(AVG(organization_score), 2)   AS avg_organization,
       ROUND(AVG(service_score), 2)        AS avg_service,
       ROUND(AVG(experience_score), 2)     AS avg_experience,
       ROUND(AVG(product_score), 2)        AS avg_product
FROM surveys;

-- 12. NPS global
SELECT COUNT(*) AS responses,
       COUNT(*) FILTER (WHERE nps_score >= 9) AS promoters,
       COUNT(*) FILTER (WHERE nps_score BETWEEN 7 AND 8) AS passives,
       COUNT(*) FILTER (WHERE nps_score <= 6) AS detractors,
       ROUND(100.0 * (COUNT(*) FILTER (WHERE nps_score >= 9) - COUNT(*) FILTER (WHERE nps_score <= 6)) / COUNT(*), 1) AS nps
FROM surveys;

-- 13. Comparación entre eventos
SELECT event_name, event_date, campaign_name, registered, attended, attendance_rate_pct,
       participation_rate_pct, conversion_rate_pct, avg_product_rating,
       avg_overall_satisfaction, nps, cost_per_attendee
FROM vw_event_performance ORDER BY event_date;

-- 14. Quién es nuevo/recurrente en un evento concreto (ejemplo: evento 3)
SELECT h.participant_id, p.first_name, p.last_name, h.attendance_number, h.participant_type
FROM vw_participant_event_history h JOIN participants p ON p.id = h.participant_id
WHERE h.event_id = 3 ORDER BY h.attendance_number DESC, h.participant_id;
