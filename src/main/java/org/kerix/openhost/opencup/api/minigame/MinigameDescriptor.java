package org.kerix.openhost.opencup.api.minigame;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Required annotation on every Minigame subclass. MinigameRegistry reads
 * this at registration time. Missing annotation → RegistrationException.
 *
 * <pre>{@code
 * @MinigameDescriptor(
 *     id           = "block_party",
 *     displayName  = "Block Party",
 *     minPlayers   = 4,
 *     maxPlayers   = 24,
 *     supportsRounds = true
 * )
 * public final class BlockPartyMinigame extends Minigame { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MinigameDescriptor {
    /** Unique machine-readable ID — used in tournament.yml and commands. */
    String id();

    /** Human-readable name shown on scoreboards and in chat. */
    String displayName();

    int minPlayers()  default 2;
    int maxPlayers()  default 64;

    /** True if the minigame is played across multiple rounds. */
    boolean supportsRounds() default false;

    /** True if the minigame supports team-vs-team play. */
    boolean supportsTeams()  default false;

    /**
     * Arena type tags this minigame requires. ArenaManager only checks out
     * arenas whose metadata contains all listed tags.
     */
    String[] requiredArenaTypes() default {};
}
