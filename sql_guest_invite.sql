-- ============================================================
-- Tables à créer dans MariaDB pour la fonctionnalité "Mode invité partagé"
-- À exécuter sur la base distante (Freebox Delta / MariaDB externe)
-- ============================================================

CREATE TABLE IF NOT EXISTS invite_tokens (
    id INT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(64) NOT NULL UNIQUE,
    id_personne INT,
    nom_invite VARCHAR(255),
    expires_at BIGINT NOT NULL,
    used TINYINT(1) NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS invite_responses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(64) NOT NULL,
    aime_ingredient TEXT,
    aime_pas_ingredient TEXT,
    aime_plat TEXT,
    aime_pas_plat TEXT,
    submitted_at BIGINT NOT NULL
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
