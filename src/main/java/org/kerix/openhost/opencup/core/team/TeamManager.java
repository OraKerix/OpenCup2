package org.kerix.openhost.opencup.core.team;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.scoreboard.Scoreboard;
import org.kerix.openhost.opencup.api.player.GamePlayer;
import org.kerix.openhost.opencup.api.team.Team;
import org.kerix.openhost.opencup.api.team.TeamColor;
import org.kerix.openhost.opencup.core.event.GameEventBus;
import org.kerix.openhost.opencup.core.event.events.TeamEliminatedEvent;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Creates and destroys Bukkit scoreboard teams for one GameSession.
 * Also tracks team-level elimination: when every member of a team has been
 * eliminated, markTeamEliminated() fires a TeamEliminatedEvent.
 * <p>
 * Minigames interact only via MinigameContext — never directly with this class.
 */
public final class TeamManager {

    private final String sessionId;
    private final GameEventBus eventBus;
    private final Logger log;
    private final Scoreboard scoreboard;

    private final Map<String, TeamImpl> teams = new LinkedHashMap<>();

    // Tracks how many teams have been fully eliminated so far (for elimination rank)
    private int eliminatedTeamCount = 0;

    public TeamManager(String sessionId, GameEventBus eventBus, Logger log) {
        this.sessionId  = sessionId;
        this.eventBus   = eventBus;
        this.log        = log;
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
    }

    // ── Team creation ─────────────────────────────────────────────────────────

    public Team createTeam(String name, TeamColor color, List<GamePlayer> members) {
        String id = sessionId + ":" + name.toLowerCase().replace(" ", "_");
        if (teams.containsKey(id)) {
            throw new IllegalArgumentException("Team already exists: " + name);
        }

        // Register in Bukkit scoreboard for name-colouring in-game
        org.bukkit.scoreboard.Team bukkit = scoreboard.registerNewTeam(id);
        bukkit.prefix(Component.text(color.legacy() + ""));
        bukkit.color(color.adventure());
        members.stream()
                .map(GamePlayer::toBukkit)
                .filter(Objects::nonNull)
                .forEach(p -> bukkit.addEntry(p.getName()));

        TeamImpl team = new TeamImpl(id, name, color, members);
        teams.put(id, team);
        log.fine("[TeamManager] Created team " + name + " (" + members.size() + " members)");
        return team;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public List<Team> getTeams() {
        return Collections.unmodifiableList(new ArrayList<>(teams.values()));
    }

    /**
     * Teams that still have at least one alive (non-eliminated) member.
     * Used by the auto-end check in MinigameContextImpl for team games.
     */
    public List<Team> getAliveTeams() {
        return teams.values().stream()
                .filter(Team::hasAliveMembers)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Find which team this player belongs to.
     * Returns empty if this is a solo game or the player is not on any team.
     */
    public Optional<Team> getTeamOf(UUID uuid) {
        return teams.values().stream()
                .filter(t -> t.hasMember(uuid))
                .map(t -> (Team) t)
                .findFirst();
    }

    /**
     * Called by EliminationService after a player is eliminated.
     * Checks if that player's team is now fully eliminated and if so fires
     * a TeamEliminatedEvent.
     *
     * @return the now-eliminated team, or empty if the team still has survivors
     */
    public Optional<Team> checkTeamElimination(UUID eliminatedUuid) {
        Optional<Team> teamOpt = getTeamOf(eliminatedUuid);
        if (teamOpt.isEmpty()) return Optional.empty();

        Team team = teamOpt.get();
        if (team.hasAliveMembers()) return Optional.empty(); // team still alive

        eliminatedTeamCount++;
        log.fine("[TeamManager] Team eliminated: " + team.getName()
                + " (rank " + eliminatedTeamCount + ")");

        eventBus.publish(new TeamEliminatedEvent(
                sessionId,
                team.getId(),
                team.getName(),
                eliminatedTeamCount,
                team.getMembers().stream().map(GamePlayer::getUuid).toList()
        ));

        return Optional.of(team);
    }

    /** True only if there are registered teams (i.e. this is a team-mode game). */
    public boolean isTeamGame() {
        return !teams.isEmpty();
    }

    // ── Teardown ──────────────────────────────────────────────────────────────

    /** Destroys all Bukkit teams. Called on session teardown automatically. */
    public void destroyAll() {
        new HashSet<>(scoreboard.getTeams()).forEach(org.bukkit.scoreboard.Team::unregister);
        teams.clear();
        log.fine("[TeamManager] All teams destroyed for session " + sessionId);
    }
}
