-- #################################################################################
-- ## Migration V8: Ajout des Clés Étrangères et des Contraintes de Validation    ##
-- #################################################################################

-- ## Contrainte de validation pour l'économie ##
-- S'assurer que le solde d'un joueur ne peut jamais être négatif au niveau de la DB.
ALTER TABLE nexus_economy
ADD CONSTRAINT chk_balance_non_negative CHECK (balance >= 0);

-- ## Relation : nexus_profiles -> nexus_players ##
ALTER TABLE nexus_profiles
ADD CONSTRAINT fk_profiles_players
FOREIGN KEY (player_uuid) REFERENCES nexus_players(player_uuid)
ON DELETE CASCADE;

-- ## Relation : nexus_economy -> nexus_players ##
ALTER TABLE nexus_economy
ADD CONSTRAINT fk_economy_players
FOREIGN KEY (player_uuid) REFERENCES nexus_players(player_uuid)
ON DELETE CASCADE;

-- ## Relation : nexus_match_participants -> nexus_matches ##
ALTER TABLE nexus_match_participants
ADD CONSTRAINT fk_participants_matches
FOREIGN KEY (match_id) REFERENCES nexus_matches(match_id)
ON DELETE CASCADE;

-- ## Relation : nexus_match_participants -> nexus_players ##
ALTER TABLE nexus_match_participants
ADD CONSTRAINT fk_participants_players
FOREIGN KEY (player_uuid) REFERENCES nexus_players(player_uuid)
ON DELETE CASCADE;

-- ## Relation : nexus_player_classes -> nexus_players ##
ALTER TABLE nexus_player_classes
ADD CONSTRAINT fk_player_classes_players
FOREIGN KEY (player_uuid) REFERENCES nexus_players(player_uuid)
ON DELETE CASCADE;

-- ## Relation : nexus_player_cosmetics -> nexus_players ##
ALTER TABLE nexus_player_cosmetics
ADD CONSTRAINT fk_player_cosmetics_players
FOREIGN KEY (player_uuid) REFERENCES nexus_players(player_uuid)
ON DELETE CASCADE;

-- ## Relation : nexus_player_quests -> nexus_players ##
ALTER TABLE nexus_player_quests
ADD CONSTRAINT fk_player_quests_players
FOREIGN KEY (player_uuid) REFERENCES nexus_players(player_uuid)
ON DELETE CASCADE;

-- ## Relation : nexus_player_achievements -> nexus_players ##
ALTER TABLE nexus_player_achievements
ADD CONSTRAINT fk_player_achievements_players
FOREIGN KEY (player_uuid) REFERENCES nexus_players(player_uuid)
ON DELETE CASCADE;
