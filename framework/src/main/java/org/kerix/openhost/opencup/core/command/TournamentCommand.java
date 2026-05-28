package org.kerix.openhost.opencup.core.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jspecify.annotations.NonNull;
import org.kerix.openhost.opencup.core.engine.TournamentEngine;
import org.kerix.openhost.opencup.core.scoring.LeaderboardService;

import java.util.Collection;
import java.util.List;

/**
 * /tournament — player-facing commands.
 * Registered via Brigadier in OpenCupBootstrap (Paper lifecycle API).
 * <p>
 * /tournament status    — show current game and standings
 * /tournament top       — show leaderboard
 */
@SuppressWarnings("UnstableApiUsage")
public final class TournamentCommand implements BasicCommand {

    private final TournamentEngine engine;
    private final LeaderboardService leaderboard;

    public TournamentCommand(TournamentEngine engine, LeaderboardService leaderboard) {
        this.engine      = engine;
        this.leaderboard = leaderboard;
    }

    @Override
    public void execute(@NonNull CommandSourceStack stack, String[] args) {
        if (args.length == 0) {
            stack.getSender().sendMessage(usage());
            return;
        }
        switch (args[0].toLowerCase()) {
            case "status" -> stack.getSender().sendMessage(engine.getStatusComponent());
            case "top"    -> sendLeaderboard(stack);
            default       -> stack.getSender().sendMessage(usage());
        }
    }

    @Override
    public @NonNull Collection<String> suggest(@NonNull CommandSourceStack stack, String[] args) {
        if (args.length <= 1) return List.of("status", "top");
        return List.of();
    }

    private void sendLeaderboard(CommandSourceStack stack) {
        var top = leaderboard.getTop(10);
        if (top.isEmpty()) {
            stack.getSender().sendMessage(
                    Component.text("No scores yet.", NamedTextColor.GRAY));
            return;
        }
        stack.getSender().sendMessage(
                Component.text("─── OpenCup Leaderboard ───", NamedTextColor.GOLD));
        for (int i = 0; i < top.size(); i++) {
            var e = top.get(i);
            stack.getSender().sendMessage(Component.text(
                    (i + 1) + ". " + e.displayName() + " — " + e.points() + " pts",
                    i == 0 ? NamedTextColor.GOLD : NamedTextColor.WHITE));
        }
    }

    private Component usage() {
        return Component.text(
                "Usage: /tournament <status|top>", NamedTextColor.YELLOW);
    }
}
