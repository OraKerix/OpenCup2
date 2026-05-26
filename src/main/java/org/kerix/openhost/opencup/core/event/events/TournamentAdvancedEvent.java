package org.kerix.openhost.opencup.core.event.events;

public record TournamentAdvancedEvent(int completedIndex, int totalGames, String nextMinigameId) {}
