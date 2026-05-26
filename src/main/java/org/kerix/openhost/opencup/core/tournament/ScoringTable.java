package org.kerix.openhost.opencup.core.tournament;

import org.kerix.openhost.opencup.config.schema.ScoringTableSchema;

/**
 * Runtime wrapper around ScoringTableSchema. Exists so the tournament
 * system can be tested without loading YAML.
 */
public final class ScoringTable {

    private final ScoringTableSchema schema;

    public ScoringTable(ScoringTableSchema schema) {
        this.schema = schema;
    }

    public int pointsForPlacement(int placement) {
        return schema.pointsForPlacement(placement);
    }
}
