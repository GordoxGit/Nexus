package fr.heneria.nexus.sanction.repository;

import fr.heneria.nexus.sanction.model.Sanction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SanctionRepository {
    void save(Sanction sanction);
    Optional<Sanction> findActiveSanction(UUID playerId, String sanctionType);
    void deactivateLastSanction(UUID playerId);
    List<Sanction> findSanctionsByUuid(UUID playerUuid);
}
