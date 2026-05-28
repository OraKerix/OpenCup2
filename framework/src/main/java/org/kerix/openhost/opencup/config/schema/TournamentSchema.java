package org.kerix.openhost.opencup.config.schema;

import java.util.List;

/**
 * Full tournament configuration as parsed from tournament.yml.
 * Immutable after load. Passed into TournamentEngine.
 */
public record TournamentSchema(
        String name,
        int roundResetDelayTicks,
        int postGameDelayTicks,
        List<GameEntrySchema> games
) {}