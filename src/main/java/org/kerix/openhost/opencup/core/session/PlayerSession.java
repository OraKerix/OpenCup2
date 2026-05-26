package org.kerix.openhost.opencup.core.session;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.kerix.openhost.opencup.api.player.GamePlayer;
import org.kerix.openhost.opencup.api.player.PlayerRole;

import java.util.UUID;

/**
 * Pre-game state snapshot + current in-game state for one player.
 * Created on enroll, destroyed on discharge.
 * <p>
 * The saved fields (location, gameMode, inventory) are used to restore
 * the player exactly to where they were before the game started.
 *
 * @param savedLocation Pre-game snapshot accessors (used by PlayerSessionManager.discharge) Pre-game snapshot
 */
public record PlayerSession(UUID uuid, String sessionId, GamePlayer gamePlayer, Location savedLocation,
                            GameMode savedGameMode, ItemStack[] savedInventory, float savedExp, int savedLevel) {

    public PlayerRole getRole() {
        return gamePlayer.getRole();
    }

    public void setRole(PlayerRole r) {
        gamePlayer.setRole(r);
    }

}
