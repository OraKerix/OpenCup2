package org.kerix.openhost.opencup.config.schema;


public record SpawnPointSchema(
        String name,
        String group,
        double x, double y, double z,
        float yaw, float pitch
)  {}
