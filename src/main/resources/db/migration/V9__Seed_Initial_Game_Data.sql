-- Nexus Database Schema Version 9
-- This migration introduces definition tables for gameplay data and seeds them with
-- the canonical baseline used by the Nexus plugin.

CREATE TABLE IF NOT EXISTS nexus_class_definitions (
    class_id VARCHAR(64) NOT NULL PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL,
    description TEXT,
    cost INT NOT NULL DEFAULT 0 COMMENT 'Cost in Nexus Coins',
    required_elo INT NOT NULL DEFAULT 0,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS nexus_vip_tiers (
    tier_id VARCHAR(64) NOT NULL PRIMARY KEY,
    tier_name VARCHAR(128) NOT NULL,
    permission_node VARCHAR(128) NOT NULL,
    queue_weight INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS nexus_cosmetic_definitions (
    cosmetic_id VARCHAR(128) NOT NULL PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL,
    cosmetic_type VARCHAR(64) NOT NULL,
    description TEXT,
    cost INT NOT NULL DEFAULT 0,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO nexus_class_definitions (class_id, display_name, description, cost, required_elo, is_enabled) VALUES
    ('vanguard', 'Vanguard', 'Guerrier de première ligne spécialisé dans la protection de ses alliés.', 0, 0, TRUE),
    ('spectre', 'Spectre', 'Infiltré furtif qui excelle dans les éliminations rapides.', 0, 0, TRUE),
    ('ranger', 'Ranger', 'Archer polyvalent capable de contrôler les distances.', 0, 0, TRUE),
    ('medic', 'Médecin', 'Support dévoué capable de soigner et de ressusciter ses alliés.', 0, 0, TRUE),
    ('sentinel', 'Sentinelle', 'Défenseur tactique qui verrouille les objectifs critiques.', 0, 0, TRUE),
    ('scout', 'Éclaireur', 'Messager agile qui révèle la position des ennemis.', 0, 0, TRUE),
    ('alchemist', 'Alchimiste', 'Maître des mixtures infligeant des effets de zone variés.', 2500, 0, TRUE),
    ('blacksmith', 'Forgeron', 'Renforce l''équipement allié et affaiblit l''arsenal adverse.', 2500, 0, TRUE),
    ('bard', 'Barde', 'Amplifie l''équipe grâce à ses hymnes et contrechants.', 2500, 0, TRUE),
    ('hunter', 'Chasseur', 'Traqueur expert des cibles isolées avec ses pièges.', 2500, 0, TRUE),
    ('saboteur', 'Saboteur', 'Démolit les défenses adverses à l''aide d''explosifs télécommandés.', 2500, 0, TRUE),
    ('juggernaut', 'Juggernaut', 'Tank lourd aux assauts imparables.', 7500, 1200, TRUE),
    ('marksman', 'Tireur d''élite', 'Spécialiste des tirs précis à longue distance.', 7500, 1200, TRUE),
    ('pyromancer', 'Pyromancien', 'Contrôle les flammes pour brûler des zones entières.', 7500, 1200, TRUE),
    ('engineer', 'Ingénieur', 'Déploie tourelles et structures de soutien automatisées.', 7500, 1200, TRUE),
    ('assassin', 'Assassin', 'Frappe dans l''ombre et disparaît sans laisser de traces.', 7500, 1200, TRUE),
    ('technomancer', 'Technomancien', 'Manipule l''énergie arcanique et la technologie avancée.', 15000, 1500, TRUE),
    ('paladin', 'Paladin', 'Combattant sacré combinant défense et soutien.', 15000, 1500, TRUE),
    ('necromancer', 'Nécromancien', 'Réveille les ombres pour submerger ses adversaires.', 15000, 1500, TRUE),
    ('sharpshooter', 'Franc-tireur', 'Spécialiste du tir de précision sous pression.', 15000, 1500, TRUE),
    ('illusionist', 'Illusionniste', 'Déjoue ses ennemis à l''aide de clones et distorsions.', 15000, 1500, TRUE),
    ('chronomancer', 'Chronomancien', 'Altère le temps pour renverser les engagements.', 30000, 1800, TRUE),
    ('tempest', 'Tempête', 'Canalise les éléments pour contrôler les foules.', 30000, 1800, TRUE),
    ('warlock', 'Démoniste', 'Scelle des pactes obscurs pour gagner en puissance.', 30000, 1800, TRUE),
    ('beastmaster', 'Maître des bêtes', 'Combat épaulé de compagnons féroces.', 30000, 1800, TRUE),
    ('guardian', 'Gardien', 'Protecteur ultime des objectifs critiques.', 30000, 1800, TRUE),
    ('celestial', 'Céleste', 'Incarnation d''une divinité qui transcende le champ de bataille.', 75000, 2200, TRUE),
    ('voidwalker', 'Marcheur du Vide', 'Exploite le vide pour se téléporter et perturber.', 75000, 2200, TRUE),
    ('dragonknight', 'Chevalier-dragon', 'Canalise la puissance ancestrale des dragons.', 75000, 2200, TRUE)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    description = VALUES(description),
    cost = VALUES(cost),
    required_elo = VALUES(required_elo),
    is_enabled = VALUES(is_enabled);

INSERT INTO nexus_vip_tiers (tier_id, tier_name, permission_node, queue_weight) VALUES
    ('bronze', 'VIP Bronze', 'nexus.vip.bronze', 1),
    ('silver', 'VIP Argent', 'nexus.vip.silver', 2),
    ('gold', 'VIP Or', 'nexus.vip.gold', 3),
    ('platinum', 'VIP Platine', 'nexus.vip.platinum', 4),
    ('diamond', 'VIP Diamant', 'nexus.vip.diamond', 5)
ON DUPLICATE KEY UPDATE
    tier_name = VALUES(tier_name),
    permission_node = VALUES(permission_node),
    queue_weight = VALUES(queue_weight);

INSERT INTO nexus_cosmetic_definitions (cosmetic_id, display_name, cosmetic_type, description, cost, is_enabled) VALUES
    ('flame_trail', 'Traînée de flammes', 'ARROW_TRAIL', 'Laisse une traînée de feu derrière chaque flèche tirée.', 300, TRUE),
    ('emerald_aura', 'Aura d''émeraude', 'KILL_EFFECT', 'Entoure vos éliminations d''un éclat émeraude.', 450, TRUE),
    ('starlight_wings', 'Ailes astrales', 'VICTORY_DANCE', 'Déploie des ailes lumineuses lors de vos victoires.', 1200, TRUE),
    ('shadow_veil', 'Voile d''ombre', 'DEATH_EFFECT', 'Disparaît dans un nuage d''ombre à votre élimination.', 800, TRUE),
    ('crystal_banner', 'Bannière de cristal', 'LOBBY_GADGET', 'Affiche une bannière animée dans le lobby Nexus.', 950, TRUE)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    cosmetic_type = VALUES(cosmetic_type),
    description = VALUES(description),
    cost = VALUES(cost),
    is_enabled = VALUES(is_enabled);
