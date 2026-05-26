package org.kerix.openhost.opencup.config.schema;

import java.util.HashMap;
import java.util.Map;

/**
 * Points awarded per placement. "default" is applied to all placements
 * not explicitly listed.
 * <p>
 * <p>Example YAML:
 *  <p> scoring:
 *     <p>1st: 10
 *     <p>2nd: 7
 *     <p>3rd: 5
 *     <p>default: 1
 */
public record ScoringTableSchema(Map<Integer, Integer> placementPoints, int defaultPoints) {

    public int pointsForPlacement(int placement) {
        return placementPoints.getOrDefault(placement, defaultPoints);
    }

    /** Parse from a YAML section where keys are "1st","2nd"... or plain integers. */
    public static ScoringTableSchema fromMap(Map<?, ?> raw) {
        Map<Integer, Integer> pts = new HashMap<>();
        int def = 0;
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            String key = e.getKey().toString().replace("st","").replace("nd","")
                    .replace("rd","").replace("th","").trim();
            int val = Integer.parseInt(e.getValue().toString());
            if (key.equals("default")) def = val;
            else pts.put(Integer.parseInt(key), val);
        }
        return new ScoringTableSchema(Map.copyOf(pts), def);
    }
}
