package org.kerix.openhost.opencup.core.team;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.scoreboard.Scoreboard;
import org.kerix.openhost.opencup.api.player.GamePlayer;
import org.kerix.openhost.opencup.api.team.Team;
import org.kerix.openhost.opencup.api.team.TeamColor;
import org.kerix.openhost.opencup.core.event.GameEventBus;

import java.util.*;
import java.util.logging.Logger;

/**
 * Creates and destroys Bukkit scoreboard teams for one GameSession.
 * Minigames call ctx.createTeam(...) which delegates here.
 * All teams are destroyed automatically on session teardown.
 */
public final class TeamManager {

    private final String sessionId;
    private final GameEventBus eventBus;
    private final Logger log;
    private final Scoreboard scoreboard;

    private final Map<String, TeamImpl> teams = new LinkedHashMap<>();

    public TeamManager(String sessionId, GameEventBus eventBus, Logger log) {
        this.sessionId  = sessionId;
        this.eventBus   = eventBus;
        this.log        = log;
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
    }

    public Team createTeam(String name, TeamColor color, List<GamePlayer> members) {
        String id = sessionId + ":" + name.toLowerCase().replace(" ", "_");
        if (teams.containsKey(id)) throw new IllegalArgumentException("Team already exists: " + name);

        // Register in Bukkit scoreboard for name-colouring
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

    public List<Team> getTeams() {
        return Collections.unmodifiableList(new ArrayList<>(teams.values()));
    }

    public Optional<Team> getTeamOf(UUID uuid) {
        return teams.values().stream()
                .filter(t -> t.hasMember(uuid))
                .map(t -> (Team) t)
                .findFirst();
    }

    /** Destroys all Bukkit teams. Called on session teardown. */
    public void destroyAll() {
        scoreboard.getTeams().forEach(org.bukkit.scoreboard.Team::unregister);
        teams.clear();
        log.fine("[TeamManager] All teams destroyed for session " + sessionId);
    }
}
