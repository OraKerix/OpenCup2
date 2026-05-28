package org.kerix.openhost.opencup.api.ui;

/**
 * One rendered line on a Bukkit sidebar scoreboard.
 *
 * @param content Formatted text (Adventure MiniMessage or legacy §-codes).
 * @param score   Bukkit sidebar score — higher = displayed higher. Use
 *                descending values (99, 98, 97 ...) for top-to-bottom order.
 */
public record SidebarLine(String content, int score) {}
