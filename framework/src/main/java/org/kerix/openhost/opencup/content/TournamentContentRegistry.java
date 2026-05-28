package org.kerix.openhost.opencup.content;

import org.kerix.openhost.opencup.config.schema.ArenaSchema;
import org.kerix.openhost.opencup.config.schema.GameEntrySchema;
import org.kerix.openhost.opencup.config.schema.TournamentSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TournamentContentRegistry {

    private final String tournamentName;
    private final int roundResetDelayTicks;
    private final int postGameDelayTicks;

    private final List<MinigameContentSchema> entries = new ArrayList<>();

    public TournamentContentRegistry(
            String tournamentName,
            int roundResetDelayTicks,
            int postGameDelayTicks
    ) {
        this.tournamentName = tournamentName;
        this.roundResetDelayTicks = roundResetDelayTicks;
        this.postGameDelayTicks = postGameDelayTicks;
    }

    public void add(MinigameContentSchema schema) {
        entries.add(schema);
    }

    public List<MinigameContentSchema> entries() {
        return Collections.unmodifiableList(entries);
    }

    public TournamentSchema tournamentSchema() {
        List<GameEntrySchema> games = entries.stream()
                .map(MinigameContentSchema::gameEntry)
                .toList();

        return new TournamentSchema(
                tournamentName,
                roundResetDelayTicks,
                postGameDelayTicks,
                games
        );
    }

    public List<ArenaSchema> arenaSchemas() {
        return entries.stream()
                .flatMap(entry -> entry.arenas().stream())
                .toList();
    }
}
