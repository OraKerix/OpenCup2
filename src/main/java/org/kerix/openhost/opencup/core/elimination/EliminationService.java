package org.kerix.openhost.opencup.core.elimination;

import org.kerix.openhost.opencup.api.player.GamePlayer;
import org.kerix.openhost.opencup.api.player.PlayerRole;
import org.kerix.openhost.opencup.core.event.GameEventBus;
import org.kerix.openhost.opencup.core.event.events.PlayerEliminatedEvent;
import org.kerix.openhost.opencup.core.session.PlayerSessionManager;

import java.util.*;

/**
 * Centralised elimination tracking for a single GameSession.
 * One instance per session — instantiated by MinigameContextImpl.
 * <p>
 * Why this exists: any game needs to know elimination order to build
 * a ranked result. Without a dedicated service each minigame would
 * re-implement tracking, inevitably with bugs (double-eliminations, etc.).
 * <p>
 * eliminate() is idempotent — calling it twice for the same player is safe.
 * getRanking() returns a list usable directly in MinigameResult.builder().
 */
public final class EliminationService {

    private final String sessionId;
    private final PlayerSessionManager sessionManager;
    private final GameEventBus eventBus;

    // UUID of each eliminated player in order — index 0 eliminated first.
    private final List<UUID> eliminationOrder = new ArrayList<>();

    public EliminationService(String sessionId,
                              PlayerSessionManager sessionManager,
                              GameEventBus eventBus) {
        this.sessionId      = sessionId;
        this.sessionManager = sessionManager;
        this.eventBus       = eventBus;
    }

    /**
     * Eliminate a player. Transitions their role to SPECTATOR and records
     * their elimination rank. Publishes PlayerEliminatedEvent.
     * Idempotent — does nothing if player is already eliminated.
     */
    public void eliminate(GamePlayer player, String reason) {
        if (player.isEliminated()) return;

        int rank = eliminationOrder.size() + 1;
        eliminationOrder.add(player.getUuid());
        player.setEliminationRank(rank);
        sessionManager.applyRole(player.getUuid(), PlayerRole.ELIMINATED);

        eventBus.publish(new PlayerEliminatedEvent(player.getUuid(), reason, rank));
    }

    public void eliminate(GamePlayer player) {
        eliminate(player, "eliminated");
    }

    /**
     * Produce a final ranked player list (best → worst) from all participants.
     * <p>
     * Non-eliminated players first (sorted by session points descending),
     * then eliminated players in reverse-elimination order
     * (last eliminated = best placement among the eliminated).
     */
    public List<UUID> getRanking(List<GamePlayer> allParticipants) {
        List<UUID> alive = allParticipants.stream()
                .filter(gp -> !gp.isEliminated())
                .sorted(Comparator.comparingInt(GamePlayer::getSessionPoints).reversed())
                .map(GamePlayer::getUuid)
                .toList();

        // Reverse eliminationOrder so last-out appears first (higher placement)
        List<UUID> eliminated = new ArrayList<>(eliminationOrder);
        Collections.reverse(eliminated);

        List<UUID> result = new ArrayList<>(alive);
        result.addAll(eliminated);
        return result;
    }

    public List<UUID> getEliminationOrder() {
        return Collections.unmodifiableList(eliminationOrder);
    }

    public int getAliveCount(List<GamePlayer> participants) {
        return (int) participants.stream().filter(GamePlayer::isAlive).count();
    }

    /** Reset between rounds in a multi-round game. */
    public void reset() {
        eliminationOrder.clear();
    }
}
