package org.kerix.openhost.opencup.api.ui;

import org.kerix.openhost.opencup.api.player.GamePlayer;

import java.util.List;

/**
 * Implemented by minigames to declare what their sidebar shows.
 * ScoreboardManager calls getLines() on every scoreboard refresh.
 * <p>
 * The viewer parameter lets you show different content to each player
 * (e.g. hide opponents' health, show personal timer).
 * <p>
 * Register with: ctx().setSidebarProvider(viewer -> { ... });
 */
@FunctionalInterface
public interface SidebarView {
    List<SidebarLine> getLines(GamePlayer viewer);
}
