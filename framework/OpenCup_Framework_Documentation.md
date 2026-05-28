# OpenCup Framework Documentation

Documentation generated from the uploaded `Opencup2` project.

> Build verification note: I inspected the source and resources directly. I attempted a Gradle build, but the sandbox could not download the Gradle distribution from `services.gradle.org`, so the project was not build-verified here.

---

## 1. What OpenCup currently is

OpenCup is a Paper 1.21.1 tournament plugin split into two Gradle modules:

```text
opencup/
├─ framework/   # reusable tournament/minigame backend
└─ plugin/      # Paper JavaPlugin entrypoint + concrete minigame registration
```

The framework is designed around this idea:

> A minigame should only know about `MinigameContext`, `Arena`, `GamePlayer`, `Team`, timers, and result objects. It should not directly control the tournament engine, global scoring service, session manager, arena manager, or persistence layer.

The `framework` module contains the reusable skeleton/backend:

- minigame API
- arena API and YAML loading
- tournament engine
- game session lifecycle
- player session snapshot/restore
- teams and elimination
- timers and tick orchestration
- scoreboards/sidebar UI
- tournament scoring
- async YAML persistence
- command implementations

The `plugin` module contains:

- `Main extends JavaPlugin`
- Bukkit/Paper lifecycle wiring
- framework bootstrapping
- command registration
- concrete minigame registrations
- currently one empty `BlockPartyMinigame`

---

## 2. Module structure

### `framework`

The framework module should behave like a library. It should not have its own `plugin.yml` or its own `JavaPlugin` class.

Important packages:

```text
org.kerix.openhost.opencup.api
├─ arena       # Arena, SpawnPoint, ArenaRegion, ArenaMetadata
├─ minigame    # Minigame, MinigameContext, MinigameResult, descriptor, end reasons
├─ phase       # GamePhase
├─ player      # GamePlayer, PlayerRole
├─ team        # Team, TeamColor, TeamResult
├─ timer       # GameTimer, TimerCallback
└─ ui          # SidebarView, SidebarLine

org.kerix.openhost.opencup.bootstrap
├─ Bootstrap
└─ ServiceRegistry

org.kerix.openhost.opencup.config
├─ ArenaConfigLoader
├─ TournamentConfigLoader
└─ schema records

org.kerix.openhost.opencup.core
├─ arena       # ArenaManager, ArenaImpl, ArenaAccessor
├─ command     # AdminCommand, TournamentCommand
├─ context     # MinigameContextImpl
├─ elimination # EliminationService
├─ engine      # TournamentEngine, GameSession, GameStateMachine
├─ event       # internal GameEventBus and records
├─ listener    # global framework Bukkit listener
├─ registry    # MinigameRegistry
├─ scoring     # ScoringService, LeaderboardService
├─ session     # PlayerSessionManager
├─ team        # TeamManager, TeamImpl
├─ tick        # TickOrchestrator
├─ timer       # TimerService
├─ tournament  # TournamentConfig, State, Entry, ScoringTable
└─ ui          # ScoreboardManager, SidebarRenderer

org.kerix.openhost.opencup.persistence
├─ PlayerStatsRepository
├─ TournamentRepository
├─ async
└─ impl
```

### `plugin`

The plugin module is the actual Paper plugin that gets loaded by the server.

```text
plugin/src/main/java/org/kerix/openhost/opencup/Main.java
plugin/src/main/java/org/kerix/openhost/opencup/minigame/blockparty/BlockPartyMinigame.java
plugin/src/main/resources/plugin.yml
plugin/src/main/resources/tournament.yml
```

---

## 3. Runtime boot sequence

The entrypoint is:

```java
org.kerix.openhost.opencup.Main
```

`Main.onEnable()` does the following:

1. Stores a static `Main` instance.
2. Creates `new Bootstrap(this)`.
3. Calls `bootstrap.boot()`.
4. Gets the `ServiceRegistry` from the bootstrap.
5. Registers `FrameworkListener` as a Bukkit listener.
6. Registers Paper Brigadier commands:
   - `/opencup`, alias `/oc`
   - `/tournament`, aliases `/t`, `/tour`
7. Registers minigames:
   - `ExampleMinigame.class`
   - `BlockPartyMinigame.class`
8. Logs that OpenCup is enabled.

`Main.onDisable()` calls `bootstrap.shutdown()`.

### Bootstrap service registration order

`Bootstrap.boot()` wires the backend manually through `ServiceRegistry`.

Current order:

1. `GameEventBus`
2. `AsyncPersistenceWorker`
3. `PlayerStatsRepository` → `YamlPlayerStatsRepository`
4. `TournamentRepository` → `YamlTournamentRepository`
5. `ScoringService`
6. `LeaderboardService`
7. `TimerService`
8. `TickOrchestrator`
9. `PlayerSessionManager`
10. `TournamentConfig`
11. `ArenaManager`
12. `MinigameRegistry`
13. `ArenaAccessor`
14. `ScoreboardManager`
15. ProtocolLib lookup
16. `TournamentEngine`

`ServiceRegistry.bind(...)` automatically calls `start()` on services implementing `Startable`. Shutdown happens in reverse registration order for services implementing `Stoppable`.

### Internal dependency flow

```text
Main.onEnable()
  └─ Bootstrap.boot()
       ├─ loads tournament.yml
       ├─ loads arenas/*.yml
       ├─ creates service graph
       ├─ starts Startable services
       └─ creates TournamentEngine

Main registers:
  ├─ FrameworkListener
  ├─ /opencup command
  ├─ /tournament command
  └─ concrete minigames
```

---

## 4. ServiceRegistry

`ServiceRegistry` is a small manual dependency injection container.

Core behavior:

- binds a service under a class/interface key
- throws if a binding already exists
- starts services immediately if they implement `Startable`
- stops services in reverse order if they implement `Stoppable`
- exposes `get`, `find`, `isBound`, `unbind`, and `logBindings`

Example:

```java
registry.bind(PlayerStatsRepository.class,
        new YamlPlayerStatsRepository(plugin, worker));

PlayerStatsRepository repo = registry.get(PlayerStatsRepository.class);
```

Important design rule:

> Only `Bootstrap` should use `ServiceRegistry` directly. Minigames should not use it. They receive controlled access through `MinigameContext`.

---

## 5. Minigame API

Every game extends:

```java
public abstract class Minigame
```

Required rules:

1. Annotate the class with `@MinigameDescriptor`.
2. Provide a public no-arg constructor.
3. Implement `onStart()`.
4. Implement `onEnd(EndReason reason)` and return a non-null `MinigameResult`.

Example descriptor:

```java
@MinigameDescriptor(
    id = "blockparty",
    displayName = "Block Party",
    minPlayers = 2,
    maxPlayers = 64,
    supportsRounds = false,
    supportsTeams = false
)
public final class BlockPartyMinigame extends Minigame {
    @Override
    public void onStart() {
        // game setup
    }

    @Override
    public MinigameResult onEnd(EndReason reason) {
        return MinigameResult.builder(ctx().getSessionId(), "blockparty")
                .reason(reason)
                .rankedPlayers(ctx().getRankedPlayers().stream()
                        .map(GamePlayer::getUuid)
                        .toList())
                .build();
    }
}
```

### Lifecycle hooks

The lifecycle is:

```text
injectContext(ctx)
  ↓
onLoad()
  ↓
WAITING phase
  ↓
onWaiting()
  ↓
COUNTDOWN phase
  ↓
onCountdownStart(seconds)
  ↓
onCountdownTick(remaining) once per second
  ↓
PLAYING phase
  ↓
onStart()
  ↓
onTick(globalTick) every server tick while PLAYING
  ↓
ROUND_END phase, if a round ends
  ↓
onRoundReset(), if more rounds remain
  ↓
onStart(), for the next round
  ↓
onEnd(reason)
  ↓
onDestroy()
  ↓
POST_GAME teardown
```

`onStart()` is called at the start of active gameplay and again after each round reset in multi-round games.

`onEnd()` must build the final rankings. The tournament scoring system cannot award points without a valid `MinigameResult`.

### Protected helpers available inside a minigame

`Minigame` exposes these helper methods:

```java
ctx()           // MinigameContext
arena()         // current Arena
participants()  // all GamePlayer objects in this session
alive()         // alive GamePlayer objects
teams()         // current teams
tick()          // current global tick
participant(Player bukkitPlayer) // Optional<GamePlayer>
```

---

## 6. MinigameContext

`MinigameContext` is the main framework API for minigames.

Minigames use it for:

- accessing players
- eliminating players or teams
- awarding in-game points
- ending games
- creating timers
- reading arena data
- creating teams
- setting sidebars
- sending messages/actionbars/titles
- registering session-scoped listeners
- using a session-seeded random
- optionally accessing ProtocolLib

### Important methods

#### Participants

```java
ctx().getParticipants();
ctx().getAlivePlayers();
ctx().getSpectators();
ctx().getPlayer(uuid);
```

#### Elimination

```java
ctx().eliminate(player);
ctx().eliminate(player, "fell into void");
ctx().eliminateTeam(team, "team wiped");
```

When a player is eliminated:

1. Their `GamePlayer.eliminationRank` is set.
2. Their role becomes `ELIMINATED`.
3. `PlayerEliminatedEvent` is published internally.
4. The minigame hook `onPlayerEliminated(player)` is called.
5. In team games, the framework checks whether the entire team is now eliminated.
6. If one player/team remains, the framework auto-declares a winner.

#### In-game points

These are **not tournament points**. They only exist inside the current game/session.

```java
ctx().awardPoints(player, 5, "captured hill");
ctx().awardPoints(team, 10, "team objective");
ctx().getPoints(player);
ctx().getRankedPlayers();
```

Tournament points are awarded later by `ScoringService`, using the `MinigameResult` and the scoring table from `tournament.yml`.

#### Ending a game

```java
ctx().declareWinner(player);
ctx().declareWinner(team);
ctx().declareDraw();
ctx().endGame(customResult);
```

Use `declareWinner` or `declareDraw` when the normal framework round logic should continue.

Use `endGame(result)` when the minigame computes its own final result immediately, such as a race, sudoku, bingo, or build competition.

#### Timers

```java
GameTimer timer = ctx().createTimer(60, new TimerCallback() {
    @Override
    public void onTick(int remainingSeconds) {
        ctx().broadcastActionBar(Component.text("Time: " + remainingSeconds));
    }

    @Override
    public void onFinish() {
        ctx().declareDraw();
    }
});
```

Repeating timer:

```java
ctx().createRepeatingTimer(5, new TimerCallback() {
    @Override
    public void onTick(int elapsed) {
        // called every 5 ticks
    }
});
```

All timers created through context are session-scoped and cancelled automatically when the game ends.

#### Arena

```java
Arena arena = ctx().getArena();
World world = arena.getWorld();
SpawnPoint spawn = arena.getSpawnPoint("center");
List<SpawnPoint> players = arena.getSpawnPoints("players");
```

#### Teams

```java
Team red = ctx().createTeam("Red", TeamColor.RED, redPlayers);
Team blue = ctx().createTeam("Blue", TeamColor.BLUE, bluePlayers);

ctx().awardPoints(red, 10, "objective");
ctx().declareWinner(red);
```

#### UI

```java
ctx().setSidebarProvider(viewer -> List.of(
    new SidebarLine("§6§lBlock Party", 99),
    new SidebarLine("§7Score: §a" + ctx().getPoints(viewer), 98)
));
```

#### Listener registration

```java
ctx().registerListener(this);
```

Listeners registered through `ctx().registerListener(...)` are automatically unregistered during session teardown.

---

## 7. Game phases and state machine

Phases are defined by `GamePhase`:

```text
IDLE
WAITING
COUNTDOWN
PLAYING
ROUND_END
POST_GAME
```

Only `GameStateMachine` changes phases. Legal transitions are:

```text
IDLE       → WAITING
WAITING    → COUNTDOWN, IDLE
COUNTDOWN  → PLAYING, IDLE
PLAYING    → ROUND_END, POST_GAME
ROUND_END  → PLAYING, POST_GAME
POST_GAME  → IDLE
```

Every phase transition publishes a `PhaseChangedEvent` to the internal `GameEventBus`.

Main consumers include:

- `ScoreboardManager`
- any future internal service subscribed to phase changes

---

## 8. Tournament engine

`TournamentEngine` owns the full tournament flow.

It knows:

- the tournament schedule
- active session
- current tournament state
- how to launch the next configured game
- how to apply tournament points after a minigame ends
- when to finish the tournament

Public methods:

```java
startTournament();
skipCurrentGame();
endTournament();
isRunning();
hasActiveGame();
getActiveSession();
getStatusComponent();
```

### Starting a tournament

`/opencup start` calls `TournamentEngine.startTournament()`.

Flow:

```text
startTournament()
  ├─ load existing player stats
  ├─ create TournamentState
  ├─ collect Bukkit.getOnlinePlayers()
  ├─ register display names in ScoringService
  └─ launchNext(participants)
```

### Launching a game

`launchNext(...)` does the following:

1. Reads current `TournamentEntry`.
2. Instantiates the minigame by ID from `MinigameRegistry`.
3. Checks out the configured arena from `ArenaManager`.
4. Creates a unique session ID.
5. Creates per-session services:
   - `GameStateMachine`
   - `EliminationService`
   - `TeamManager`
   - `MinigameContextImpl`
   - `GameSession`
6. Injects the `GameSession` into the context.
7. Opens the session.
8. Publishes `TournamentAdvancedEvent`.
9. Starts countdown after 40 ticks.

### When a minigame ends

`GameSession` publishes `MinigameEndedEvent`.

`TournamentEngine` receives it and:

1. Applies tournament points with `ScoringService`.
2. Saves the result with `YamlTournamentRepository`.
3. Clears `activeSession`.
4. Advances the `TournamentState`.
5. Either finishes the tournament or launches the next game after `post_game_delay_ticks`.

---

## 9. GameSession

`GameSession` is the runtime frame around one minigame execution.

It owns:

- session ID
- minigame instance
- tournament entry config
- phase state machine
- minigame context
- elimination service
- player list
- round counter
- timers/listeners cleanup route
- arena return route
- tick registration

### Important implementation detail: shared player list

`TournamentEngine` creates one `List<GamePlayer>` and gives the exact same list to both:

- `GameSession`
- `MinigameContextImpl`

`GameSession.open(...)` fills this list. `MinigameContextImpl` reads it. This is why `ctx().getParticipants()` can return the real players without the context needing to query the session manager.

### Session open flow

```text
open(initialPlayers)
  ├─ enroll each Bukkit Player into PlayerSessionManager
  ├─ add GamePlayer wrappers to shared list
  ├─ register session with ScoreboardManager
  ├─ register session as Tickable in TickOrchestrator
  ├─ inject context into minigame
  ├─ minigame.onLoad()
  ├─ phase: IDLE → WAITING
  └─ minigame.onWaiting()
```

### Countdown flow

```text
startCountdown()
  ├─ phase: WAITING → COUNTDOWN
  ├─ minigame.onCountdownStart(seconds)
  └─ TimerService countdown calls:
       ├─ minigame.onCountdownTick(remaining)
       └─ beginPlaying() at zero
```

### Playing flow

```text
beginPlaying()
  ├─ elimination.reset()
  ├─ phase: COUNTDOWN → PLAYING
  ├─ create timeout countdown
  └─ minigame.onStart()
```

### Round end flow

`ctx().declareWinner(...)` or auto-elimination calls `GameSession.handleRoundEnd(...)`.

```text
handleRoundEnd(winner, reason)
  ├─ phase: PLAYING → ROUND_END
  ├─ roundsPlayed++
  ├─ publish RoundEndedEvent
  ├─ if more rounds:
  │    ├─ wait roundResetDelayTicks
  │    ├─ reset roles/elimination ranks
  │    ├─ elimination.reset()
  │    ├─ minigame.onRoundReset()
  │    ├─ phase: ROUND_END → PLAYING
  │    └─ minigame.onStart()
  └─ else:
       └─ proceedToPostGame(reason)
```

### Teardown flow

```text
teardown(result)
  ├─ ended = true
  ├─ phase: * → POST_GAME
  ├─ cancel session timers
  ├─ unregister session listeners
  ├─ minigame.onDestroy()
  ├─ unregister scoreboard session
  ├─ discharge players and restore pre-game state
  ├─ unregister from TickOrchestrator
  ├─ return arena to ArenaManager
  ├─ phase: POST_GAME → IDLE
  └─ publish MinigameEndedEvent
```

---

## 10. Minigame registration

Minigames are registered in `Main.onEnable()`:

```java
MinigameRegistry minigameRegistry = bootstrap.minigameRegistry();

minigameRegistry.register(ExampleMinigame.class);
minigameRegistry.register(BlockPartyMinigame.class);
```

`MinigameRegistry.register(...)` validates:

- class has `@MinigameDescriptor`
- descriptor ID is unique
- class has a no-arg constructor

`TournamentEngine` later calls:

```java
minigameRegistry.instantiate(entry.minigameId());
```

This creates a fresh minigame instance for each session. Minigame instances are not reused.

---

## 11. Tournament configuration

The plugin ships with:

```text
plugin/src/main/resources/tournament.yml
```

On first boot, `TournamentConfigLoader` copies it to:

```text
plugins/OpenCup/tournament.yml
```

Current format:

```yaml
tournament:
  name: "OpenCup"

  round_reset_delay_ticks: 60
  post_game_delay_ticks: 200

  games:
    - minigame: test_minigame
      arena: test_arena
      rounds: 1
      countdown_seconds: 5
      timeout_seconds: 300
      scoring:
        1st: 10
        2nd: 7
        3rd: 5
        default: 1
```

### Fields

| Field | Meaning |
|---|---|
| `tournament.name` | Display/logical tournament name. |
| `round_reset_delay_ticks` | Delay between rounds in the same minigame. |
| `post_game_delay_ticks` | Delay between one minigame ending and the next starting. |
| `games[].minigame` | Must match `@MinigameDescriptor(id = ...)`. |
| `games[].arena` | Must match an arena YAML ID/filename. |
| `games[].rounds` | Number of rounds for this tournament entry. |
| `games[].countdown_seconds` | Countdown before gameplay starts. |
| `games[].timeout_seconds` | Forced timeout for the game/round. |
| `games[].scoring` | Placement-to-tournament-points table. |

### Scoring keys

`ScoringTableSchema.fromMap(...)` accepts keys like:

```yaml
1st: 10
2nd: 7
3rd: 5
4th: 3
default: 1
```

It strips ordinal suffixes and stores integer placements internally.

---

## 12. Arena system

Arena files live in:

```text
plugins/OpenCup/arenas/*.yml
```

Each arena file becomes one `ArenaSchema`, then one runtime `ArenaImpl`.

### Arena YAML format

```yaml
id: test_arena
world: world

region:
  min:
    x: 0
    y: 60
    z: 0
  max:
    x: 100
    y: 120
    z: 100

type_tags:
  - duel
  - flat

reset_strategy: NONE
schematic_file: null

spawn_groups:
  players:
    - name: player_a
      x: 10.5
      y: 65.0
      z: 10.5
      yaw: 0
      pitch: 0
    - name: player_b
      x: 20.5
      y: 65.0
      z: 20.5
      yaw: 180
      pitch: 0

metadata:
  floor_y: "64"
  theme: "laboratory"
```

### Runtime Arena API

Minigames receive an `Arena` through `arena()` or `ctx().getArena()`.

```java
arena().getId();
arena().getWorld();
arena().getSpawnPoints("players");
arena().getSpawnPoint("player_a");
arena().getRegion();
arena().getMetadata();
```

### Spawn points

`SpawnPoint` is a record:

```java
SpawnPoint(String name, String group, double x, double y, double z, float yaw, float pitch)
```

It can convert to Bukkit `Location`:

```java
Location loc = spawn.toLocation(arena().getWorld());
```

### Arena checkout

`ArenaManager.checkout(arenaId)`:

- throws if the arena ID is unknown
- throws if the arena is already occupied
- marks the arena occupied

`ArenaManager.returnArena(arenaId)`:

- uses a `WorldResetter`
- marks the arena available when reset completes

Currently only `NoOpResetterStrategy` is implemented, so `reset_strategy` effectively does nothing unless more resetters are added.

---

## 13. Player session system

`PlayerSessionManager` is the authoritative map of which players are currently in a game session.

On enroll, it snapshots:

- UUID
- session ID
- `GamePlayer`
- location
- game mode
- inventory contents
- exp
- level

On discharge, it restores:

- inventory contents
- game mode
- exp
- level
- location

Methods:

```java
enroll(Player bukkit, String sessionId, PlayerRole initialRole);
discharge(UUID uuid);
dischargeAll(String sessionId);
applyRole(UUID uuid, PlayerRole role);
getSession(UUID uuid);
isInGame(UUID uuid);
isSpectator(UUID uuid);
getPlayersInSession(String sessionId);
```

### GamePlayer

`GamePlayer` is the framework wrapper around a Bukkit player identity.

It stores:

- UUID
- name
- role
- session points
- elimination rank
- arbitrary data map

Roles are:

```text
PARTICIPANT
SPECTATOR
ELIMINATED
```

Useful methods:

```java
player.toBukkit();
player.isParticipant();
player.isSpectator();
player.isAlive();
player.addSessionPoints(amount);
player.isEliminated();
player.setData("key", value);
player.getData("key", Type.class);
```

---

## 14. Elimination system

`EliminationService` is created per `GameSession`.

It tracks:

- elimination order
- elimination rank
- optional `TeamManager`

Calling `ctx().eliminate(player, reason)` delegates into this service.

Solo ranking logic:

1. Alive players sorted by session points descending.
2. Eliminated players in reverse elimination order.

This means the last eliminated player ranks higher than the first eliminated player.

Team ranking logic:

1. Alive teams sorted by total team points descending.
2. Eliminated teams in reverse elimination order.

---

## 15. Team system

Teams are session-scoped and created by minigames:

```java
Team red = ctx().createTeam("Red", TeamColor.RED, redPlayers);
Team blue = ctx().createTeam("Blue", TeamColor.BLUE, bluePlayers);
```

`TeamManager` creates a Bukkit scoreboard team for name coloring and tracks team elimination.

The public API is `Team`:

```java
team.getId();
team.getName();
team.getColor();
team.getMembers();
team.getAliveMembers();
team.hasAliveMembers();
team.hasMember(uuid);
team.getTotalPoints();
team.addMember(player);
team.removeMember(uuid);
```

### Team results

For team games, `MinigameResult` should include `TeamResult` objects:

```java
return MinigameResult.builder(ctx().getSessionId(), "my_team_game")
        .reason(reason)
        .rankedTeams(List.of(
                TeamResult.of(winningTeam),
                TeamResult.of(losingTeam)
        ))
        .build();
```

`ScoringService` awards every member of a ranked team the same placement points.

---

## 16. Scoring system

There are two scoring concepts:

### 1. In-game score

Stored on `GamePlayer.sessionPoints`.

Used for:

- minigame logic
- local ranking
- sidebars
- generating `MinigameResult`

Awarded through:

```java
ctx().awardPoints(player, amount, reason);
ctx().awardPoints(team, amount, reason);
```

### 2. Tournament points

Stored in `ScoringService.tournamentPoints`.

Awarded only after a minigame ends:

```text
MinigameResult + ScoringTable → tournament points
```

For solo games:

```text
rankedPlayers[0] gets 1st-place points
rankedPlayers[1] gets 2nd-place points
...
```

For team games:

```text
rankedTeams[0].memberUuids() all get 1st-place points
rankedTeams[1].memberUuids() all get 2nd-place points
...
```

### Manual point adjustment

Admin command:

```text
/opencup addpoints <player> <amount> <reason>
```

Calls:

```java
ScoringService.adjustPoints(uuid, delta, reason)
```

---

## 17. Leaderboard system

`LeaderboardService` subscribes to `ScoreChangedEvent`.

It caches leaderboard entries sorted by tournament points.

Exposed methods:

```java
getTop(limit);
getAll();
getPlacement(uuid);
```

The `/tournament top` command uses `LeaderboardService.getTop(10)`.

`ScoreboardManager` also uses it for the built-in leaderboard sidebar.

---

## 18. Timer system

`TimerService` is the only scheduler wrapper for managed game timers.

It provides:

```java
createCountdown(sessionId, seconds, callback);
createRepeating(sessionId, intervalTicks, callback);
createDelay(sessionId, delayTicks, action);
cancelAll(sessionId);
stop();
```

All timers are stored under a session ID, so session teardown can cancel them all.

### Countdown detail

`GameTimerImpl.tick()` decrements before calling `onTick`.

A 5-second timer emits:

```text
4, 3, 2, 1, 0
```

Then it calls `onFinish()`.

This is important for countdown displays. If you want to show the original number, display it from `onCountdownStart(seconds)` or adjust your text.

---

## 19. Tick system

`TickOrchestrator` is a single global per-tick runner.

It is scheduled once in `Bootstrap`:

```java
Bukkit.getScheduler().runTaskTimer(plugin, tickOrchestrator, 1L, 1L);
```

Any object implementing `Tickable` can register.

Currently, active `GameSession` objects register themselves. During `PLAYING`, `GameSession.tick(...)` calls:

```java
minigame.onTick(globalTick);
```

This avoids every minigame creating its own repeating scheduler for per-tick logic.

---

## 20. UI and scoreboard system

`ScoreboardManager` subscribes to internal framework events:

- `PhaseChangedEvent`
- `LeaderboardRefreshedEvent`
- `PlayerRoleChangedEvent`
- `MinigameEndedEvent`

It renders different views depending on the phase:

```text
IDLE / POST_GAME       → leaderboard view
WAITING / COUNTDOWN    → waiting view
PLAYING / ROUND_END    → minigame-provided SidebarView
```

Minigames define a sidebar through:

```java
ctx().setSidebarProvider(viewer -> List.of(
    new SidebarLine("§6§lMy Game", 99),
    new SidebarLine("§7Points: §a" + ctx().getPoints(viewer), 98)
));
```

`SidebarRenderer` gives each player their own scoreboard if they are still using the main scoreboard. This allows per-player sidebar content.

---

## 21. Event system

The framework has an internal `GameEventBus`. It is separate from Bukkit events.

Bukkit events are for raw Minecraft events such as death, join, quit, food, respawn.

OpenCup internal events are for framework state changes:

```text
LeaderboardRefreshedEvent
MinigameEndedEvent
PhaseChangedEvent
PlayerEliminatedEvent
PlayerRoleChangedEvent
RoundEndedEvent
ScoreChangedEvent
TeamEliminatedEvent
TournamentAdvancedEvent
TournamentEndedEvent
```

`GameEventBus.publish(event)` calls subscribers synchronously on the same thread that published the event.

Important consequence:

> Do not do slow work inside event bus subscribers. If a subscriber is called from the main thread, it runs on the main thread.

---

## 22. Bukkit FrameworkListener

`FrameworkListener` is the global Bukkit listener.

Current behavior:

- `PlayerQuitEvent`: detects in-game quitters, but the actual disconnect handling is TODO.
- `PlayerJoinEvent`: late-join is not implemented.
- `PlayerDeathEvent`: keeps inventory and level for in-game players.
- `FoodLevelChangeEvent`: cancels hunger changes for in-game players.
- `PlayerRespawnEvent`: currently contains fallback/stub logic.

Minigame-specific events should be handled with session-scoped listeners via `ctx().registerListener(...)`.

---

## 23. Persistence

Persistence uses a single-threaded async worker:

```java
AsyncPersistenceWorker
```

This is good because YAML file I/O should not run on the main thread, and sequential writes avoid file corruption.

### Player stats

Interface:

```java
PlayerStatsRepository
```

Current implementation:

```java
YamlPlayerStatsRepository
```

File:

```text
plugins/OpenCup/player_stats.yml
```

Stored fields:

```yaml
<uuid>:
  lastName: Kerix
  tournamentPoints: 10
  gamesPlayed: 1
  wins: 1
  top3Finishes: 1
  lastPlayed: 2026-05-28T...
```

### Tournament history

Interface:

```java
TournamentRepository
```

Current implementation:

```java
YamlTournamentRepository
```

File:

```text
plugins/OpenCup/tournament_history.yml
```

Currently stores:

- minigame ID
- end reason
- endedAt timestamp
- ranked players

It does not currently store ranked teams or in-game scores.

---

## 24. Commands and permissions

### `/opencup`

Registered by `Main` using Paper lifecycle commands.

Permission:

```yaml
opencup.admin
```

Subcommands:

```text
/opencup start
/opencup skip
/opencup end
/opencup addpoints <player> <amount> <reason>
```

### `/tournament`

Player-facing command.

Subcommands:

```text
/tournament status
/tournament top
```

Aliases registered in code:

```text
/t
/tour
```

---

## 25. ProtocolLib integration

`Bootstrap` checks whether ProtocolLib is enabled:

```java
if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
    protocolManager = ProtocolLibrary.getProtocolManager();
}
```

Minigames can then call:

```java
ctx().getProtocolManager();
```

If ProtocolLib was not detected, `getProtocolManager()` throws an `IllegalStateException`.

Recommended `plugin.yml` addition if ProtocolLib is optional:

```yaml
softdepend:
  - ProtocolLib
```

Recommended if the plugin cannot run without ProtocolLib:

```yaml
depend:
  - ProtocolLib
```

Because the framework imports `com.comphenix.protocol.*` classes directly, treating ProtocolLib as truly optional is risky unless those references are isolated behind reflection or a separate adapter class.

---

## 26. How to create a new solo minigame

### Step 1: Create the class

```java
package org.kerix.openhost.opencup.minigame.race;

import org.kerix.openhost.opencup.api.minigame.*;
import org.kerix.openhost.opencup.api.player.GamePlayer;

@MinigameDescriptor(
        id = "race",
        displayName = "Race",
        minPlayers = 2,
        maxPlayers = 32
)
public final class RaceMinigame extends Minigame {

    @Override
    public void onStart() {
        for (GamePlayer player : participants()) {
            // teleport, give items, reset state
        }
    }

    @Override
    public void onTick(long globalTick) {
        // optional progress checks
    }

    @Override
    public MinigameResult onEnd(EndReason reason) {
        return MinigameResult.builder(ctx().getSessionId(), "race")
                .reason(reason)
                .rankedPlayers(ctx().getRankedPlayers().stream()
                        .map(GamePlayer::getUuid)
                        .toList())
                .build();
    }
}
```

### Step 2: Register it

In `Main.onEnable()`:

```java
minigameRegistry.register(RaceMinigame.class);
```

### Step 3: Add it to `tournament.yml`

```yaml
- minigame: race
  arena: race_arena_01
  rounds: 1
  countdown_seconds: 10
  timeout_seconds: 180
  scoring:
    1st: 10
    2nd: 7
    3rd: 5
    default: 1
```

### Step 4: Add an arena file

```text
plugins/OpenCup/arenas/race_arena_01.yml
```

---

## 27. How to create a team minigame

```java
@MinigameDescriptor(
        id = "team_koth",
        displayName = "Team KOTH",
        supportsTeams = true
)
public final class TeamKothMinigame extends Minigame {

    private Team red;
    private Team blue;

    @Override
    public void onStart() {
        List<GamePlayer> players = participants();

        List<GamePlayer> redPlayers = players.subList(0, players.size() / 2);
        List<GamePlayer> bluePlayers = players.subList(players.size() / 2, players.size());

        red = ctx().createTeam("Red", TeamColor.RED, redPlayers);
        blue = ctx().createTeam("Blue", TeamColor.BLUE, bluePlayers);
    }

    @Override
    public MinigameResult onEnd(EndReason reason) {
        List<Team> rankedTeams = teams().stream()
                .sorted(Comparator.comparingInt(Team::getTotalPoints).reversed())
                .toList();

        return MinigameResult.builder(ctx().getSessionId(), "team_koth")
                .reason(reason)
                .rankedTeams(rankedTeams.stream()
                        .map(TeamResult::of)
                        .toList())
                .build();
    }
}
```

For team games, make sure `rankedTeams(...)` is populated. If only `rankedPlayers(...)` is populated, the scoring service treats it as a solo game.

---

## 28. Common use patterns

### Duel / 1v1

Use:

- two spawn points in a `players` group
- `ctx().registerListener(this)` for death events
- `ctx().eliminate(player)` on death
- automatic winner declaration when one player remains

### Race

Use:

- `onTick` or movement events to detect finish line
- store finish order manually
- call `ctx().endGame(result)` when all finished or timer expires
- do not rely only on `ctx().getRankedPlayers()` unless points reflect race progress

### King of the Hill

Use:

- `onTick` or scheduled/repeating timer
- region checks from `arena().getRegion()` or metadata-defined zones
- `ctx().awardPoints(player/team, amount, reason)`
- final ranking by session points

### Bingo / objective collection

Use:

- session-scoped event listeners
- `GamePlayer.setData(...)` for per-player objective state
- custom `MinigameResult` when a player/team completes the board

### Build competition

Use:

- arena spawn groups/metadata for plots
- manual admin/judge scoring
- `ctx().endGame(...)` with a custom ranking

### Block Party

Use:

- `ctx().createRepeatingTimer(...)` for round pulses
- `arena().getMetadata()` for floor Y, radius, block palette
- eliminate players who stand on the wrong block
- either use round flow or custom timers inside one game

---

## 29. Current implementation issues and risks found

These are not theoretical. They are based on the uploaded source.

### 1. `framework/build.gradle.kts` still applies old Shadow plugin

Current framework file still has:

```kotlin
id("com.github.johnrengelman.shadow") version "8.1.1"
```

The framework does not need Shadow if it is only a library module. Recommended:

```kotlin
plugins {
    java
}
```

Keep Shadow only in the final `plugin` module.

### 2. ProtocolLib is compile-only but not declared in `plugin.yml`

`plugin.yml` currently has no `depend` or `softdepend` for ProtocolLib.

Add one of:

```yaml
softdepend:
  - ProtocolLib
```

or:

```yaml
depend:
  - ProtocolLib
```

If ProtocolLib is truly optional, isolate direct `com.comphenix.protocol.*` usage behind an adapter or reflection to avoid runtime classloading failures on servers without ProtocolLib.

### 3. `BlockPartyMinigame.onEnd()` returns `null`

Current code:

```java
@Override
public MinigameResult onEnd(EndReason reason) {
    return null;
}
```

If `blockparty` is placed in `tournament.yml`, the session will eventually publish a `MinigameEndedEvent` with a null result, causing scoring/history code to fail.

Return a real `MinigameResult` before using it in a tournament.

### 4. Result IDs are inconsistent in `ExampleMinigame`

Descriptor ID:

```java
@MinigameDescriptor(id = "test_minigame", ...)
```

But result builder uses:

```java
MinigameResult.builder(ctx().getSessionId(), "testMinigame")
```

These should match. Use `test_minigame` consistently.

### 5. `skipCurrentGame()` uses minigame ID `"skipped"`

Current forced-end result uses:

```java
MinigameResult.builder(activeSession.getId(), "skipped")
```

This loses the actual minigame ID. It would be better to use the current `TournamentEntry.minigameId()`.

### 6. Min/max players are not enforced

`@MinigameDescriptor` contains:

```java
minPlayers()
maxPlayers()
```

But `TournamentEngine` currently starts games with all online players and does not enforce those limits.

### 7. Required arena types are not enforced

`@MinigameDescriptor.requiredArenaTypes()` exists, and arena YAML has `type_tags`, but `ArenaManager.checkout(arenaId)` simply checks out the explicit arena ID. It does not verify that the arena has the required tags.

### 8. Team scoreboard cleanup is not called

`TeamManager.destroyAll()` exists, but `GameSession.teardown(...)` does not call it. Since `TeamManager` is local to the session and not exposed directly in teardown, Bukkit scoreboard teams may leak during team games.

Recommended fix: add a teardown call path for `TeamManager.destroyAll()`.

### 9. Arena reset future is ignored

`GameSession.teardown(...)` calls:

```java
arenaManager.returnArena(config.arenaId());
```

but does not wait for the `CompletableFuture<Void>`.

With `NoOpResetterStrategy`, this is harmless. With real schematic/world resetters, the next game may try to check out an arena before reset is complete.

### 10. Tournament history does not save team results

`YamlTournamentRepository.saveResult(...)` only stores `rankedPlayers`. It ignores:

- `rankedTeams`
- `inGameScores`

Team game history will be incomplete.

### 11. Framework respawn/quit behavior is unfinished

`FrameworkListener` contains TODO/stub behavior for:

- player disconnects
- late joins
- respawn handling

This is fine for a skeleton but should be completed before real tournaments.

### 12. Player restore snapshot is incomplete

`PlayerSessionManager` restores inventory contents, game mode, exp, level, and location. It does not currently snapshot/restore:

- armor contents
- offhand
- health
- food level
- saturation
- potion effects
- fire ticks
- scoreboard/team state
- flight flags

For production minigames, extend the snapshot.

### 13. Countdown displays start one second lower

Because `GameTimerImpl.tick()` decrements before `onTick`, a 5-second countdown calls `onCountdownTick(4)` first. Use `onCountdownStart(5)` to display the initial value.

---

## 30. Recommended next refactors

### Priority 1 — make it safe to run

1. Remove Shadow from `framework/build.gradle.kts`.
2. Add ProtocolLib `softdepend` or `depend` to `plugin.yml`.
3. Make every minigame return a valid `MinigameResult`.
4. Fix result minigame IDs to match descriptor IDs.
5. Complete player restore snapshot.
6. Add team cleanup in session teardown.

### Priority 2 — enforce framework contracts

1. Enforce `minPlayers` and `maxPlayers` before launch.
2. Enforce `requiredArenaTypes` against arena metadata/tags.
3. Validate `tournament.yml` references at boot:
   - unknown minigame IDs
   - unknown arena IDs
   - unsupported rounds/team settings
4. Validate arena spawn groups required by specific minigames.

### Priority 3 — improve production flexibility

1. Add a proper arena resetter implementation.
2. Add SQL-backed repositories behind the existing repository interfaces.
3. Add a registration DSL or automatic classpath scanning for minigames.
4. Add per-game config sections, not only tournament-level entries.
5. Add spectator support and reconnect handling.

---

## 31. Recommended build structure

For your current goal — “build the skeleton into the plugin jar” — the recommended setup is:

```text
framework = plain Java library module
plugin    = Paper plugin module with Shadow
```

### Root `build.gradle.kts`

```kotlin
subprojects {
    apply(plugin = "java")

    group = "org.kerix.openhost"
    version = "1.0"

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}
```

### `framework/build.gradle.kts`

```kotlin
plugins {
    java
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("net.dmulloy2:ProtocolLib:5.1.0")

    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
}
```

### `plugin/build.gradle.kts`

```kotlin
plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

dependencies {
    implementation(project(":framework"))

    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("net.dmulloy2:ProtocolLib:5.1.0")

    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("opencup-${project.version}.jar")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
```

The final server jar should come from:

```text
plugin/build/libs/opencup-1.0.jar
```

Do not put `framework/build/libs/framework-1.0.jar` into the server plugins folder separately unless you intentionally turn the framework into its own Paper plugin.

---

## 32. Quick checklist for adding a production minigame

1. Create `YourMinigame extends Minigame`.
2. Add `@MinigameDescriptor`.
3. Implement `onStart()`.
4. Implement `onEnd(...)` and return non-null `MinigameResult`.
5. Use `ctx().registerListener(...)` for Bukkit events.
6. Use `ctx().createTimer(...)` instead of unmanaged Bukkit schedulers.
7. Use `ctx().awardPoints(...)` for in-game points.
8. Use `ctx().declareWinner(...)`, `ctx().declareDraw()`, or `ctx().endGame(...)` to finish.
9. Register the minigame in `Main.onEnable()`.
10. Add it to `tournament.yml`.
11. Add an arena YAML file with the required spawn groups.
12. Test `/opencup start` with the correct player count.

---

## 33. Final architecture summary

OpenCup’s current framework is already shaped like a real reusable backend:

- `Minigame` is the extension point.
- `MinigameContext` is the safe API boundary.
- `TournamentEngine` controls the tournament sequence.
- `GameSession` controls a single minigame run.
- `GameStateMachine` prevents illegal phase transitions.
- `PlayerSessionManager` protects player state.
- `ArenaManager` controls arena availability.
- `ScoringService` separates tournament scoring from in-game scoring.
- `TimerService` and `TickOrchestrator` centralize scheduling.
- `ScoreboardManager` centralizes UI rendering.
- Persistence is already abstracted behind interfaces.

The main work left is not changing the concept. The concept is good. The next step is hardening the contracts that already exist: player count validation, arena tag validation, proper team cleanup, complete result persistence, safer optional ProtocolLib handling, and finishing the incomplete player join/quit/respawn behavior.