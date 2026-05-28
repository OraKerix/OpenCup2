package org.kerix.openhost.opencup.core.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jspecify.annotations.NonNull;
import org.kerix.openhost.opencup.core.engine.TournamentEngine;
import org.kerix.openhost.opencup.core.scoring.ScoringService;

import java.util.Collection;
import java.util.List;

/**
 * /opencup — admin commands. Requires opencup.admin permission.
 * <p>
 * /opencup start        — start the tournament
 * /opencup skip         — skip the current game (force end)
 * /opencup end          — end the tournament immediately
 * /opencup addpoints <player> <amount> <reason>
 */
@SuppressWarnings("UnstableApiUsage")
public final class AdminCommand implements BasicCommand {

    private final TournamentEngine engine;
    private final ScoringService scoring;

    public AdminCommand(TournamentEngine engine, ScoringService scoring) {
        this.engine  = engine;
        this.scoring = scoring;
    }

    @Override
    public void execute(CommandSourceStack stack, String @NonNull [] args) {
        if (!stack.getSender().hasPermission("opencup.admin")) {
            stack.getSender().sendMessage(
                    Component.text("No permission.", NamedTextColor.RED)); return;
        }
        if (args.length == 0) { stack.getSender().sendMessage(usage()); return; }

        switch (args[0].toLowerCase()) {
            case "start" -> {
                engine.startTournament();
                stack.getSender().sendMessage(
                        Component.text("Tournament started!", NamedTextColor.GREEN));
            }
            case "skip" -> {
                engine.skipCurrentGame();
                stack.getSender().sendMessage(
                        Component.text("Skipped current game.", NamedTextColor.YELLOW));
            }
            case "end" -> {
                engine.endTournament();
                stack.getSender().sendMessage(
                        Component.text("Tournament ended.", NamedTextColor.RED));
            }
            case "addpoints" -> {
                if (args.length < 4) {
                    stack.getSender().sendMessage(
                            Component.text("Usage: /opencup addpoints <player> <amount> <reason>",
                                    NamedTextColor.RED));
                    return;
                }
                var target = org.bukkit.Bukkit.getPlayer(args[1]);
                if (target == null) {
                    stack.getSender().sendMessage(
                            Component.text("Player not found.", NamedTextColor.RED)); return;
                }
                try {
                    int amount = Integer.parseInt(args[2]);
                    String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
                    scoring.adjustPoints(target.getUniqueId(), amount, "[admin] " + reason);
                    stack.getSender().sendMessage(
                            Component.text("Adjusted " + target.getName() + " by " + amount + " pts.",
                                    NamedTextColor.GREEN));
                } catch (NumberFormatException e) {
                    stack.getSender().sendMessage(
                            Component.text("Invalid amount.", NamedTextColor.RED));
                }
            }
            default -> stack.getSender().sendMessage(usage());
        }
    }

    @Override
    public @NonNull Collection<String> suggest(@NonNull CommandSourceStack stack, String[] args) {
        if (args.length <= 1) return List.of("start", "skip", "end", "addpoints");
        return List.of();
    }

    private Component usage() {
        return Component.text(
                "Usage: /opencup <start|skip|end|addpoints>", NamedTextColor.YELLOW);
    }
}
