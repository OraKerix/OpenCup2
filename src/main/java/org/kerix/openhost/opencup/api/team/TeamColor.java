package org.kerix.openhost.opencup.api.team;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;

@SuppressWarnings("deprecation")
/*
  Canonical colour palette for teams. Carries all representations so no
  system needs to do its own colour mapping.
 */
public enum TeamColor {
    RED    (NamedTextColor.RED,    ChatColor.RED,    DyeColor.RED),
    BLUE   (NamedTextColor.BLUE,   ChatColor.BLUE,   DyeColor.BLUE),
    GREEN  (NamedTextColor.GREEN,  ChatColor.GREEN,  DyeColor.GREEN),
    YELLOW (NamedTextColor.YELLOW, ChatColor.YELLOW, DyeColor.YELLOW),
    AQUA   (NamedTextColor.AQUA,   ChatColor.AQUA,   DyeColor.CYAN),
    PINK   (NamedTextColor.LIGHT_PURPLE, ChatColor.LIGHT_PURPLE, DyeColor.PINK),
    ORANGE (NamedTextColor.GOLD,   ChatColor.GOLD,   DyeColor.ORANGE),
    WHITE  (NamedTextColor.WHITE,  ChatColor.WHITE,  DyeColor.WHITE);

    private final NamedTextColor adventure;
    private final ChatColor legacy;
    private final DyeColor dye;

    TeamColor(NamedTextColor adventure, ChatColor legacy, DyeColor dye) {
        this.adventure = adventure;
        this.legacy    = legacy;
        this.dye       = dye;
    }

    public NamedTextColor adventure() { return adventure; }
    public ChatColor      legacy()    { return legacy; }
    public DyeColor       dye()       { return dye; }
}
