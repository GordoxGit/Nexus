package fr.heneria.nexus.game.repository;

import fr.heneria.nexus.game.model.Match;
import fr.heneria.nexus.game.model.Team;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.UUID;

public class JdbcMatchRepository implements MatchRepository {

    private final DataSource dataSource;

    public JdbcMatchRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void saveMatchResult(Match match, int winningTeamId) {
        String sql = "INSERT INTO matches (id, arena_id, start_time, end_time, winning_team_id) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, match.getMatchId().toString());
            stmt.setInt(2, match.getArena().getId());
            Instant start = match.getStartTime() != null ? match.getStartTime() : Instant.now();
            Instant end = match.getEndTime() != null ? match.getEndTime() : Instant.now();
            stmt.setTimestamp(3, Timestamp.from(start));
            stmt.setTimestamp(4, Timestamp.from(end));
            stmt.setInt(5, winningTeamId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void saveMatchParticipants(Match match) {
        String sql = "INSERT INTO match_participants (match_id, player_uuid, team_id, kills, deaths, assists) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (UUID playerId : match.getPlayers()) {
                stmt.setString(1, match.getMatchId().toString());
                stmt.setString(2, playerId.toString());
                Team team = match.getTeamOfPlayer(playerId);
                int teamId = team != null ? team.getTeamId() : 0;
                stmt.setInt(3, teamId);
                stmt.setInt(4, match.getKills(playerId));
                stmt.setInt(5, match.getDeaths(playerId));
                stmt.setInt(6, 0);
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
