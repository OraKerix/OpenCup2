package org.kerix.openhost.opencup.core.ui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import org.kerix.openhost.opencup.api.player.GamePlayer;
import org.kerix.openhost.opencup.api.ui.SidebarLine;
import org.kerix.openhost.opencup.api.ui.SidebarView;

import java.util.List;

/**
 * Renders a SidebarView into a per-player Bukkit Scoreboard.
 * Each player gets their own Scoreboard instance so per-viewer content
 * (different lines per player) works without conflicts.
 * <p>
 * Called by ScoreboardManager — never directly by anything else.
 */
final class SidebarRenderer {

    private static final String OBJECTIVE_NAME = "opencup_sidebar";
    private static final String TITLE = "§6§lOpenCup";

    void render(Player player, GamePlayer gamePlayer, SidebarView view) {
        Scoreboard board = player.getScoreboard();
        if (board == Bukkit.getScoreboardManager().getMainScoreboard()) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(board);
        }

        Objective obj = board.getObjective(OBJECTIVE_NAME);
        if (obj == null) {
            obj = board.registerNewObjective(OBJECTIVE_NAME, "dummy", TITLE);
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        // Clear all old scores
        for (String entry : board.getEntries()) { board.resetScores(entry); }

        // Render new lines
        List<SidebarLine> lines = view.getLines(gamePlayer);
        for (SidebarLine line : lines) {
            obj.getScore(line.content()).setScore(line.score());
        }
    }

    void clear(Player player) {
        Scoreboard board = player.getScoreboard();
        Objective obj = board.getObjective(OBJECTIVE_NAME);
        if (obj != null) obj.unregister();
    }
}
