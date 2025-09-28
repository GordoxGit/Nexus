package com.heneria.nexus.service.core;

import com.heneria.nexus.api.MapDefinition;
import com.heneria.nexus.api.MapValidatorService;
import com.heneria.nexus.api.ValidationReport;
import com.heneria.nexus.api.map.MapBlueprint;
import com.heneria.nexus.api.map.MapBlueprint.MapAsset;
import com.heneria.nexus.api.map.MapBlueprint.MapNexus;
import com.heneria.nexus.api.map.MapBlueprint.MapRules;
import com.heneria.nexus.api.map.MapBlueprint.MapTeam;
import com.heneria.nexus.api.map.MapBlueprint.MapVector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Default implementation performing structural validations on loaded maps.
 */
public final class MapValidatorServiceImpl implements MapValidatorService {

    @Override
    public ValidationReport validate(MapDefinition definition, MapBlueprint blueprint) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(blueprint, "blueprint");
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        Path folder = definition.folder();
        if (!Files.exists(folder)) {
            errors.add("Le dossier de la carte est introuvable : " + folder);
            return ValidationReport.failure(warnings, errors);
        }

        if (!blueprint.configurationPresent()) {
            errors.add("map.yml est manquant dans " + folder);
            return ValidationReport.failure(warnings, errors);
        }

        validateAsset(definition, blueprint.asset(), warnings, errors);
        validateRules(blueprint.rules(), errors);
        validateTeams(definition, blueprint.teams(), warnings, errors);

        if (errors.isEmpty()) {
            return ValidationReport.success(warnings);
        }
        return ValidationReport.failure(warnings, errors);
    }

    private void validateAsset(MapDefinition definition,
                               MapAsset asset,
                               List<String> warnings,
                               List<String> errors) {
        if (asset == null || asset.file() == null || asset.file().isBlank()) {
            errors.add("[" + definition.id() + "] Section asset invalide : fichier non défini");
            return;
        }
        Path assetPath = definition.folder().resolve(asset.file());
        if (!Files.exists(assetPath)) {
            errors.add("[" + definition.id() + "] Le fichier asset '" + asset.file()
                    + "' est introuvable dans " + assetPath.getParent());
        }
    }

    private void validateRules(MapRules rules, List<String> errors) {
        if (rules == null) {
            errors.add("Section rules manquante");
            return;
        }
        Integer minPlayers = rules.minPlayers();
        Integer maxPlayers = rules.maxPlayers();
        if (minPlayers == null) {
            errors.add("Le champ rules.min_players est requis");
        }
        if (maxPlayers == null) {
            errors.add("Le champ rules.max_players est requis");
        }
        if (minPlayers != null && maxPlayers != null && minPlayers > maxPlayers) {
            errors.add("rules.min_players ne peut pas être supérieur à rules.max_players");
        }
    }

    private void validateTeams(MapDefinition definition,
                               List<MapTeam> teams,
                               List<String> warnings,
                               List<String> errors) {
        if (teams == null || teams.isEmpty()) {
            errors.add("[" + definition.id() + "] Section teams manquante ou vide");
            return;
        }
        Set<String> teamNames = new HashSet<>();
        for (MapTeam team : teams) {
            String display = team.displayName() == null || team.displayName().isBlank()
                    ? team.id()
                    : team.displayName();
            String normalized = display.toLowerCase(Locale.ROOT);
            if (!teamNames.add(normalized)) {
                errors.add("[" + definition.id() + "] Nom d'équipe dupliqué : " + display);
            }

            validateSpawn(definition, team, errors);
            validateNexus(definition, team, errors);
        }
    }

    private void validateSpawn(MapDefinition definition, MapTeam team, List<String> errors) {
        MapVector spawn = team.spawn();
        if (spawn == null) {
            errors.add("[" + definition.id() + "] Spawn manquant pour l'équipe " + team.id());
            return;
        }
        if (!spawn.hasCoordinates() || !areValidCoordinates(spawn)) {
            errors.add("[" + definition.id() + "] Coordonnées de spawn invalides pour l'équipe " + team.id());
        }
    }

    private void validateNexus(MapDefinition definition, MapTeam team, List<String> errors) {
        MapNexus nexus = team.nexus();
        if (nexus == null) {
            errors.add("[" + definition.id() + "] Section nexus manquante pour l'équipe " + team.id());
            return;
        }
        MapVector position = nexus.position();
        if (position == null || !position.hasCoordinates() || !areValidCoordinates(position)) {
            errors.add("[" + definition.id() + "] Coordonnées du nexus invalides pour l'équipe " + team.id());
        }
        Integer hp = nexus.hitPoints();
        if (hp == null) {
            errors.add("[" + definition.id() + "] Le nexus de l'équipe " + team.id() + " doit définir hp");
        } else if (hp <= 0) {
            errors.add("[" + definition.id() + "] Le nexus de l'équipe " + team.id() + " doit avoir un hp positif");
        }
    }

    private boolean areValidCoordinates(MapVector vector) {
        return isFinite(vector.x()) && isFinite(vector.y()) && isFinite(vector.z());
    }

    private boolean isFinite(Double value) {
        return value != null && Double.isFinite(value);
    }
}
