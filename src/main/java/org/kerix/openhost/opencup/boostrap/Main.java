package org.kerix.openhost.opencup.boostrap;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;
import org.kerix.openhost.opencup.core.command.AdminCommand;
import org.kerix.openhost.opencup.core.command.TournamentCommand;
import org.kerix.openhost.opencup.core.engine.TournamentEngine;
import org.kerix.openhost.opencup.core.listener.FrameworkListener;
import org.kerix.openhost.opencup.core.scoring.LeaderboardService;
import org.kerix.openhost.opencup.core.scoring.ScoringService;
import org.kerix.openhost.opencup.core.session.PlayerSessionManager;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class Main extends JavaPlugin {

    private static Main main;
    private Bootstrap bootstrap;

    @Override
    public void onEnable() {
        main = this;

        this.bootstrap = new Bootstrap(this);

        try{
            bootstrap.boot();
        } catch (Exception e){
            getLogger().severe("OpenCup failed to boot: " + e.getMessage());
            e.printStackTrace();

            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ServiceRegistry registry = bootstrap.registry();


        getServer().getPluginManager().registerEvents(
                new FrameworkListener(
                        registry.get(PlayerSessionManager.class),
                        registry.get(TournamentEngine.class)
                ), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();

            registrar.register(
                    "opencup",
                    "OpenCup admin commands",
                    List.of("oc"),
                    new AdminCommand(
                            registry.get(TournamentEngine.class),
                            registry.get(ScoringService.class)
                    )
            );


            registrar.register(
                    "tournament",
                    "View tournament status and leaderboard",
                    List.of("t", "tour"),
                    new TournamentCommand(
                            registry.get(TournamentEngine.class),
                            registry.get(LeaderboardService.class)
                    )
            );
        });

        getLogger().info("OpenCup enabled. Use /opencup start to begin the tournament.");
    }

    @Override
    public void onDisable() {
        if (bootstrap != null) {
            bootstrap.shutdown();
        }
        getLogger().info("OpenCup disabled.");

    }


    public static Main getInstance()        {return main;}
}
