package org.kerix.openhost.opencup.config;

import org.kerix.openhost.opencup.config.schema.ArenaSchema;
import org.kerix.openhost.opencup.config.schema.TournamentSchema;

import java.io.IOException;
import java.util.Collection;
import java.util.logging.Logger;

public final class DefaultConfigProvisioner {

    private final TournamentConfigAccessor tournamentConfig;
    private final ArenaConfigAccessor arenaConfig;
    private final Logger log;

    public DefaultConfigProvisioner(
            TournamentConfigAccessor tournamentConfig,
            ArenaConfigAccessor arenaConfig,
            Logger log
    ) {
        this.tournamentConfig = tournamentConfig;
        this.arenaConfig = arenaConfig;
        this.log = log;
    }

    public void provision(
            TournamentSchema tournament,
            Collection<ArenaSchema> arenas
    ) throws IOException {
        tournamentConfig.saveIfMissing(tournament);

        for (ArenaSchema arena : arenas) {
            arenaConfig.saveArenaIfMissing(arena);
        }

        log.info("[Config] Default tournament and arena config provisioned.");
    }

    public void overwrite(
            TournamentSchema tournament,
            Collection<ArenaSchema> arenas
    ) throws IOException {
        tournamentConfig.save(tournament);

        for (ArenaSchema arena : arenas) {
            arenaConfig.saveArena(arena);
        }

        log.warning("[Config] Tournament and arena config were overwritten from presets.");
    }
}
