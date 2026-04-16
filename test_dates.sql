-- Test de la logique de conversion de dates

-- Aujourd'hui: 16 avril 2026 = 16042026
-- Hier: 15 avril 2026 = 15042026
-- Avant-hier: 14 avril 2026 = 14042026
-- Il y a 3 jours: 13 avril 2026 = 13042026
-- Demain: 17 avril 2026 = 17042026

-- Expression SQL pour convertir ddMMyyyy en yyyyMMdd
-- SUBSTR(date_col, 5) || SUBSTR(date_col, 3, 2) || SUBSTR(date_col, 1, 2)
-- Exemple: SUBSTR("14042026", 5) || SUBSTR("14042026", 3, 2) || SUBSTR("14042026", 1, 2)
--          = "2026" || "04" || "14" = "20260414"

-- Test: dates < 16042026 (avant aujourd'hui) doivent aller dans HISTORIQUE
SELECT
    '14042026' as date_input,
    SUBSTR('14042026', 5) || SUBSTR('14042026', 3, 2) || SUBSTR('14042026', 1, 2) as converted,
    CASE WHEN (SUBSTR('14042026', 5) || SUBSTR('14042026', 3, 2) || SUBSTR('14042026', 1, 2)) < '20260416'
         THEN 'HISTORIQUE' ELSE 'FUTURE' END as category
UNION ALL
SELECT
    '13042026',
    SUBSTR('13042026', 5) || SUBSTR('13042026', 3, 2) || SUBSTR('13042026', 1, 2),
    CASE WHEN (SUBSTR('13042026', 5) || SUBSTR('13042026', 3, 2) || SUBSTR('13042026', 1, 2)) < '20260416'
         THEN 'HISTORIQUE' ELSE 'FUTURE' END
UNION ALL
SELECT
    '16042026',
    SUBSTR('16042026', 5) || SUBSTR('16042026', 3, 2) || SUBSTR('16042026', 1, 2),
    CASE WHEN (SUBSTR('16042026', 5) || SUBSTR('16042026', 3, 2) || SUBSTR('16042026', 1, 2)) >= '20260416'
         THEN 'FUTURE' ELSE 'HISTORIQUE' END
UNION ALL
SELECT
    '17042026',
    SUBSTR('17042026', 5) || SUBSTR('17042026', 3, 2) || SUBSTR('17042026', 1, 2),
    CASE WHEN (SUBSTR('17042026', 5) || SUBSTR('17042026', 3, 2) || SUBSTR('17042026', 1, 2)) >= '20260416'
         THEN 'FUTURE' ELSE 'HISTORIQUE' END;

