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
    List<GamePlayer> getMembers();
    boolean hasMember(UUID uuid);

    /** Sum of all member session points. */
    int getTotalPoints();

    /** Adds a member at runtime (e.g. rejoin, team rebalance). */
    void addMember(GamePlayer player);
    void removeMember(UUID uuid);
}
