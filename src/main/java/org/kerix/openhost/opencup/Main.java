package org.kerix.openhost.opencup;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {

    @Getter
    private Main main;

    @Override
    public void onEnable() {
        this.main = this;
    }

    @Override
    public void onDisable() {

        super.onDisable();
    }
}
