-- #####################################################################
-- ## Migration V2: Ajout des index pour l'optimisation des requêtes ##
-- #####################################################################

-- ## Table: nexus_profiles ##
-- Pour accélérer la recherche et le tri pour le classement (leaderboard)
CREATE INDEX idx_profiles_elo_rating ON nexus_profiles(elo_rating DESC);

-- ## Table: nexus_matches ##
-- Pour rechercher rapidement les parties par date
CREATE INDEX idx_matches_start_timestamp ON nexus_matches(start_timestamp);

-- ## Table: nexus_match_participants ##
-- Pour retrouver rapidement toutes les parties d'un joueur spécifique
CREATE INDEX idx_match_participants_player_uuid ON nexus_match_participants(player_uuid);

-- ## Table: nexus_player_classes ##
-- Pour optimiser la recherche des classes d'un joueur
-- (Bien que la clé primaire couvre déjà cela, un index explicite peut être utile si on recherche seulement par UUID)
-- Ce n'est pas strictement nécessaire si les recherches incluent toujours class_id, mais c'est une bonne pratique.

-- ## Table: nexus_analytics_log (défini dans T-019) ##
-- Ces index étaient déjà dans la définition, s'assurer qu'ils sont bien là.
-- CREATE INDEX idx_event_type ON nexus_analytics_log(event_type);
-- CREATE INDEX idx_event_timestamp ON nexus_analytics_log(event_timestamp);
