package org.kerix.openhost.opencup.core.event.events;

import org.kerix.openhost.opencup.api.player.PlayerRole;

import java.util.UUID;

public record PlayerRoleChangedEvent(UUID uuid, PlayerRole newRole) {}
