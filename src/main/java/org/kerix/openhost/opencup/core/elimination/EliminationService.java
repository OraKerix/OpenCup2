package org.kerix.openhost.opencup.core.elimination;

import lombok.Setter;
import org.kerix.openhost.opencup.api.player.GamePlayer;
import org.kerix.openhost.opencup.api.player.PlayerRole;
import org.kerix.openhost.opencup.core.event.GameEventBus;
import org.kerix.openhost.opencup.core.event.events.PlayerEliminatedEvent;
import org.kerix.openhost.opencup.core.session.PlayerSessionManager;

import java.util.*;

import org.kerix.openhost.opencup.api.team.Team;
import org.kerix.openhost.opencup.core.team.TeamManager;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Centralised elimination tracking for a single GameSession.
 * One instance per session.
 * <p>
 * Team awareness: after each player elimination this service asks TeamManager
 * whether that player's team is now fully eliminated. If so, TeamManager fires
 * a TeamEliminatedEvent and returns the eliminated team so MinigameContextImpl
 * can call the minigame's onTeamEliminated() hook.
 * <p>
 * eliminate() is idempotent — calling it twice for the same player is safe.
 */
public final class EliminationService {

    private final String sessionId;
    private final PlayerSessionManager sessionManager;
    private final GameEventBus eventBus;

    /**
     * Nullable — null in solo games. Set once via setTeamManager() after
     * TeamManager is created by the minigame's first createTeam() call.
     * We can't inject it at construction time because teams don't exist until
     * the minigame creates them in onStart().
     * -- SETTER --
     *  Called by MinigameContextImpl when a minigame first calls createTeam().
     *  At that point the TeamManager exists and we can start doing team checks.

     */
    @Setter
    @Nullable private TeamManager teamManager;

    private final List<UUID> eliminationOrder = new ArrayList<>();

    public EliminationService(String sessionId,
                              PlayerSessionManager sessionManager,
                              GameEventBus eventBus) {
        this.sessionId      = sessionId;
        this.sessionManager = sessionManager;
        this.eventBus       = eventBus;
    }

    /**
     * Eliminate a player. Transitions their role, records rank, publishes event.
     * Idempotent — safe to call multiple times for the same player.
     *
     * @return the team that became fully eliminated as a result of this
     *         elimination, or empty if no team was eliminated (solo game or
     *         team still has survivors).
     */
    public Optional<Team> eliminate(GamePlayer player, String reason) {
        if (player.isEliminated()) return Optional.empty();

        int rank = eliminationOrder.size() + 1;
        eliminationOrder.add(player.getUuid());
        player.setEliminationRank(rank);
        sessionManager.applyRole(player.getUuid(), PlayerRole.ELIMINATED);
        eventBus.publish(new PlayerEliminatedEvent(player.getUuid(), reason, rank));

        if (teamManager != null) {
            return teamManager.checkTeamElimination(player.getUuid());
        }
        return Optional.empty();
    }

    public Optional<Team> eliminate(GamePlayer player) {
        return eliminate(player, "eliminated");
    }

    // ── Ranking ───────────────────────────────────────────────────────────────

    /**
     * Solo ranking: non-eliminated players sorted by points, then eliminated
     * players in reverse-elimination order (last out = best placement).
     * Used by minigames to build MinigameResult.rankedPlayers().
     */
    public List<UUID> getSoloRanking(List<GamePlayer> allParticipants) {
        List<UUID> alive = allParticipants.stream()
                .filter(gp -> !gp.isEliminated())
                .sorted(Comparator.comparingInt(GamePlayer::getSessionPoints).reversed())
                .map(GamePlayer::getUuid)
                .toList();

        List<UUID> elim = new ArrayList<>(eliminationOrder);
        Collections.reverse(elim);

        List<UUID> result = new ArrayList<>(alive);
        result.addAll(elim);
        return result;
    }

    /**
     * Team ranking: surviving teams sorted by total points, then eliminated
     * teams in reverse-elimination order.
     * The framework calls this to build rankedTeams in auto-end team flows.
     */
    public List<Team> getTeamRanking(List<Team> allTeams) {
        if (teamManager == null) return List.of();

        List<Team> alive = allTeams.stream()
                .filter(Team::hasAliveMembers)
                .sorted(Comparator.comparingInt(Team::getTotalPoints).reversed())
                .toList();

        List<Team> eliminated = allTeams.stream()
                .filter(t -> !t.hasAliveMembers())
                .sorted(Comparator.comparingInt(t ->
                        t.getMembers().stream()
                                .mapToInt(GamePlayer::getEliminationRank)
                                .max().orElse(0)))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Collections.reverse(eliminated);

        List<Team> result = new ArrayList<>(alive);
        result.addAll(eliminated);
        return result;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public int getAliveCount(List<GamePlayer> participants) {
        return (int) participants.stream().filter(GamePlayer::isAlive).count();
    }

    public List<UUID> getEliminationOrder() {
        return Collections.unmodifiableList(eliminationOrder);
    }

    /** Reset between rounds. */
    public void reset() {
        eliminationOrder.clear();
    }
}
