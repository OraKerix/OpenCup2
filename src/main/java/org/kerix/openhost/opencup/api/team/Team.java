package org.kerix.openhost.opencup.api.team;

import org.kerix.openhost.opencup.api.player.GamePlayer;

import java.util.List;
import java.util.UUID;

/**
 * A logical team within a single game session.
 * Created by minigames via MinigameContext.createTeam(...).
 * Destroyed automatically when the session ends.
 */
public interface Team {

    String getId();
    String getName();
    TeamColor getColor();

    /** All members — including eliminated ones. */
    List<GamePlayer> getMembers();

    /** Members whose role is still PARTICIPANT (not eliminated/spectator). */
    List<GamePlayer> getAliveMembers();

    /** True if at least one member has not been eliminated. */
    boolean hasAliveMembers();

    boolean hasMember(UUID uuid);

    /** Sum of all member session points (alive and eliminated). */
    int getTotalPoints();

    void addMember(GamePlayer player);
    void removeMember(UUID uuid);
}
