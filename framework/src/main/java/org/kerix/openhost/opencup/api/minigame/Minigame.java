package org.kerix.openhost.opencup.api.minigame;

import org.bukkit.entity.Player;
import org.kerix.openhost.opencup.api.arena.Arena;
import org.kerix.openhost.opencup.api.player.GamePlayer;
import org.kerix.openhost.opencup.api.team.Team;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The base class every minigame extends. Contains ONLY lifecycle hooks and
 * protected helpers via the injected MinigameContext. No scheduler calls,
 * no world code, no scoreboard manipulation lives here.
 * <p>
 * Subclasses must:
 *  1. Be annotated with @MinigameDescriptor
 *  2. Have a public no-arg constructor (used by MinigameRegistry via reflection)
 *  3. Implement onStart() and onEnd(EndReason)
 * <p>
 * Everything else is optional. Override only what you need.
 * <p>
 */
public abstract class Minigame {

    private MinigameContext ctx;

    // ── Framework injection — do not override ─────────────────────────────────

    /** Called once by GameSession before any lifecycle hook. */
    public final void injectContext(MinigameContext ctx) {
        this.ctx = ctx;
    }

    // ── Lifecycle hooks (override as needed) ──────────────────────────────────

    /** Called once after context injection, before WAITING. Pre-load data here. */
    public void onLoad() {}

    /** Called when the lobby phase starts. Players may be joining. */
    public void onWaiting() {}

    /** Called when the countdown begins. */
    public void onCountdownStart(int seconds) {}

    /** Called once per second during countdown. */
    public void onCountdownTick(int remaining) {}

    /**
     * Called when active gameplay begins (or when a new round begins after reset).
     * This is where minigames set up the arena, give items, and start timers.
     */
    public abstract void onStart();

    /**
     * Called every server tick while the FSM is in PLAYING state.
     * Prefer event-driven logic to polling here — only override if
     * you need high-frequency state checks (e.g. physics, progress bars).
     */
    public void onTick(long globalTick) {}

    /**
     * Called between rounds in a multi-round game. The player list is preserved;
     * only internal game state (scores, objectives) should be reset here.
     * The framework handles role resets automatically.
     */
    public void onRoundReset() {}

    /**
     * Called when the game ends for any reason.
     * MUST return a MinigameResult — the framework cannot award points without it.
     * <p>
     * Use ctx().getRankedPlayers() as the base for your ranking if you use
     * the session points system, or build your own ordering for race-style games.
     */
    public abstract MinigameResult onEnd(EndReason reason);

    /** Called after onEnd(), before the session is destroyed. Clean up entities,
     *  packet listeners, and any state this minigame owns. */
    public void onDestroy() {}

    // ── Player hooks ──────────────────────────────────────────────────────────

    protected void onPlayerJoin(GamePlayer player)       {}
    public void onPlayerLeave(GamePlayer player)      {}
    public void onPlayerEliminated(GamePlayer player) {}
    /**
     * Called when every member of a team has been eliminated.\
     * The framework fires this automatically after the last member of the\
     * team is eliminated via ctx.eliminate().\
     * Override to play sounds, display titles, or trigger game logic.\
     */
    public void onTeamEliminated(Team team) {}

    protected Optional<GamePlayer> participant(Player player) {
        UUID uuid = player.getUniqueId();

        return participants().stream()
                .filter(gamePlayer -> gamePlayer.getUuid().equals(uuid))
                .findFirst();
    }

    // ── Protected helpers ─────────────────────────────────────────────────────

    protected final MinigameContext ctx()               { return ctx; }
    protected final Arena arena()                       { return ctx.getArena(); }
    protected final List<GamePlayer> participants()     { return ctx.getParticipants(); }
    protected final List<GamePlayer> alive()            { return ctx.getAlivePlayers(); }
    protected final List<Team>       teams()            { return ctx.getTeams(); }
    protected final long             tick()             { return ctx.getCurrentTick(); }
}
