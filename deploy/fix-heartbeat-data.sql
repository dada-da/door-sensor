BEGIN;

WITH marked AS (
    SELECT id,
           state,
           LAG(state) OVER (PARTITION BY device_id ORDER BY occurred_at, id) AS prev_state
    FROM door_event
)
SELECT COUNT(*) AS so_ban_ghi_se_xoa
FROM marked
WHERE state = prev_state;

WITH marked AS (
    SELECT id,
           state,
           LAG(state) OVER (PARTITION BY device_id ORDER BY occurred_at, id) AS prev_state
    FROM door_event
)
DELETE FROM door_event
WHERE id IN (SELECT id FROM marked WHERE state = prev_state);

WITH recomputed AS (
    SELECT id,
           EXTRACT(EPOCH FROM (
               occurred_at - LAG(occurred_at) OVER (PARTITION BY device_id ORDER BY occurred_at, id)
           )) * 1000 AS ms
    FROM door_event
)
UPDATE door_event e
SET duration_ms = ROUND(r.ms)
FROM recomputed r
WHERE e.id = r.id
  AND r.ms IS NOT NULL
  AND r.ms >= 0;

UPDATE door_event e
SET duration_ms = NULL
WHERE NOT EXISTS (
    SELECT 1 FROM door_event p
    WHERE p.device_id = e.device_id
      AND (p.occurred_at, p.id) < (e.occurred_at, e.id)
);

COMMIT;

WITH marked AS (
    SELECT state,
           LAG(state) OVER (PARTITION BY device_id ORDER BY occurred_at, id) AS prev_state
    FROM door_event
)
SELECT COUNT(*) AS con_lai_ban_ghi_trung
FROM marked
WHERE state = prev_state;

SELECT COUNT(*)                        AS so_lan_mo,
       ROUND(AVG(duration_ms) / 1000)  AS trung_binh_giay,
       ROUND(MAX(duration_ms) / 1000)  AS lau_nhat_giay
FROM door_event
WHERE state = 'CLOSED' AND duration_ms IS NOT NULL;
