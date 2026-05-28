package org.kerix.openhost.opencup.core.team;

import org.kerix.openhost.opencup.api.player.GamePlayer;
import org.kerix.openhost.opencup.api.team.Team;
import org.kerix.openhost.opencup.api.team.TeamColor;

import java.util.*;
import java.util.stream.Collectors;

final class TeamImpl implements Team {

    private final String id;
    private final String name;
    private final TeamColor color;
    private final List<GamePlayer> members = new ArrayList<>();

    TeamImpl(String id, String name, TeamColor color, List<GamePlayer> initial) {
        this.id    = id;
        this.name  = name;
        this.color = color;
        this.members.addAll(initial);
    }

    @Override public String    getId()    { return id; }
    @Override public String    getName()  { return name; }
    @Override public TeamColor getColor() { return color; }

    @Override
    public List<GamePlayer> getMembers() {
        return Collections.unmodifiableList(members);
    }

    @Override
    public List<GamePlayer> getAliveMembers() {
        return members.stream()
                .filter(GamePlayer::isAlive)
                .collect(Collectors.toList());
    }

    @Override
    public boolean hasAliveMembers() {
        return members.stream().anyMatch(GamePlayer::isAlive);
    }

    @Override
    public boolean hasMember(UUID uuid) {
        return members.stream().anyMatch(gp -> gp.getUuid().equals(uuid));
    }

    @Override
    public int getTotalPoints() {
        return members.stream().mapToInt(GamePlayer::getSessionPoints).sum();
    }

    @Override
    public void addMember(GamePlayer player) { members.add(player); }

    @Override
    public void removeMember(UUID uuid) {
        members.removeIf(gp -> gp.getUuid().equals(uuid));
    }
}
