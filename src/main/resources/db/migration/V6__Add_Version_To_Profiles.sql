-- Adds a version column used for optimistic locking on player profiles.
ALTER TABLE nexus_profiles
    ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT 'Utilisé pour le verrouillage optimiste';
