# SkipBo backend

Java 21, Maven, Javalin and raw WebSockets. All players and rooms live in this JVM's memory; restarting it resets them. The backend implements the waiting lobby, server-authoritative SkipBo gameplay, personalized game views, and reconnect restoration.

## Build and tests

```sh
mvn clean compile
mvn test
```

`LobbyFoundationTest` keeps the original 25 focused lobby checks. The game test classes additionally cover engine rules, starting games, privacy, gameplay messages, conservation of all 162 cards, concurrent actions, and in-game reconnects. They cover READY, profile locking through both transports, membership, capacity races, stale socket messages, reconnects, disconnect expiry, host transfer, validation, bounded chat, broadcast failures and public room-list updates.

The tests use a controlled scheduler: advancing it simulates the 15-second deadline, including a canceled callback that was already queued. Socket and HTTP test doubles exercise the real Javalin contexts, JSON mapper and handlers without binding a network port.

If Maven cannot fetch its plugins or JUnit, the same checks have a plain Java entry point: `org.skipbo.LobbyChecks`. Compile `src/main/java`, `LobbyTestSupport.java` and `LobbyChecks.java` with Java 21 and the cached application dependencies on the classpath, then run that entry point. This fallback does not validate Maven or the JUnit runner.

## Lobby behavior

- Identity comes from the server-side `playerID` session attribute and `PlayerStore`, using the browser's `JSESSIONID` cookie. Action JSON does not select a player.
- One player belongs to at most one room. Joining or creating another room removes all old memberships, cancelling an old active game back to WAITING first. Normal removal handles host transfer, socket cleanup and empty-room deletion. Reconnect to the same room keeps that membership. Each player has one authoritative socket in their current room. A replacement socket takes authority immediately. Old messages and old close events cannot change the replacement's room state.
- An unexpected WAITING socket close reserves membership, READY and host status for 15 seconds. Reconnect cancels expiry. Expiry removes membership and READY, transfers host to the first remaining member, deletes an empty room and broadcasts fresh room/global snapshots.
- Explicit `LEAVE_ROOM` removes membership immediately and acknowledges `LEFT_ROOM`. Socket close afterward does not start another timeout.
- A creator that never opens its lobby socket also expires after 15 seconds.
- HTTP and WebSocket profile updates use the same validation and lock. Profiles can change outside rooms or while WAITING and not ready. Invalid values return HTTP 400; profile/membership conflicts return 409. Invalid WebSocket actions are logged and ignored.
- READY is a server-side toggle allowed only in WAITING rooms. The server broadcasts `ROOM_STATE`; there is no client-supplied ready value.
- Room names are trimmed and must contain 1–20 characters. Usernames contain 1–8 ASCII letters, numbers, `_` or `-`. Avatars are 1–10. Targets are ANY (`1`) or fixed 2–6; capacity never exceeds six.
- Chat is trimmed, contains 1–200 characters, and uses a server-generated UUID and session player's profile. Each room keeps its latest 100 messages. A player can send one accepted chat every 250 ms; earlier messages are ignored. Throttle entries are removed on leave/expiry/room closure.
- Room creation returns a small `CreateRoomResponse`. Its `id` alias preserves the existing frontend navigation; `roomId` is the full UUID and `roomCode` is only the first eight characters for display. Internal membership, READY maps and future game state are not serialized.

`RoomService` serializes shared lobby transitions, including the membership check-plus-add operation and reconnect-versus-expiry. Mutable `Room` collections and snapshots also share the room's monitor. All application membership changes should go through `RoomService`. Broadcasting is synchronous and ordered with these transitions, which keeps this single-process v1 simple.

`ROOM_LIST` contains only WAITING rooms with an available seat. Creation, joins, leaves, expiration and deletion broadcast the current list. `startRoom` and `closeRoom` wrap the model's existing lifecycle transitions and also refresh the list. Closing a room releases its connections and timers and deletes it. `START_GAME` delegates to `startRoom`, which validates membership, host, WAITING state, readiness, player count and absence of an existing game. It creates the game in membership order, starts the first turn, attaches it, and changes the room to IN_GAME. There is no close-room WebSocket action.

Both room and global broadcasters isolate failed sends. A failed current room socket enters the same waiting-lobby grace flow even if a close callback never arrives. A failure from an old socket cannot remove its replacement. Canceled timers are removed from the scheduler queue; completed deadlines release their tracking entries. The scheduler shuts down when Javalin stops.

## Game messages

Send intentions on the existing `/ws/rooms/{roomId}` connection. Player identity always comes from the session, never from message JSON. The existing lobby actions remain `UPDATE_USERNAME`, `UPDATE_AVATAR`, `TOGGLE_READY`, `CHAT_MESSAGE`, and `LEAVE_ROOM`.

| Type | Required fields in addition to `type` |
| --- | --- |
| `START_GAME` | None |
| `PLAY_HAND_CARD` | `cardId`, `buildingPileIndex` |
| `PLAY_STOCK_CARD` | `buildingPileIndex` |
| `PLAY_DISCARD_CARD` | `discardPileIndex`, `buildingPileIndex` |
| `DISCARD_HAND_CARD` | `cardId`, `discardPileIndex` |

Card IDs and indexes must be JSON integers. Indexes are 0–3. Missing or null required fields, invalid ownership, wrong turns, illegal cards and actions after a winner are rejected without changing the game or closing the socket. Unknown fields/types and malformed JSON follow the existing logged rejection behavior. Extra known profile fields do not select the acting player. Stock and Discard plays always use the server's top card, even if a client supplies an irrelevant `cardId`.

There is no DRAW or END_TURN message. A turn starts by drawing to five. Playing the last hand card to a Building Pile draws to five again and keeps the turn. Discarding a hand card ends the turn and draws for the next player, without refilling the old player's hand. Player order wraps back to the first player.

All piles use index 0 as top. Buildings require `size + 1`, from 1 through 12; Wild substitutes for the required number. Completed buildings move into internal `completedCards`. Drawing from an empty Draw pile shuffles completed cards into Draw and continues, without generating a new deck. Playing the final Stock card sets `winnerPlayerId`, leaves the turn unchanged, and blocks further gameplay. The room remains IN_GAME and readable.

`RoomService` uses its existing synchronized methods for validation, mutation, and sending updates. Think of this as allowing one action through a door at a time: another action waits until the first has finished, so both cannot remove the same card. No extra game scheduler or turn timer is used. The concurrency test starts two requests together; each test `Future` is just a handle used to wait for one request's answer. This is test code only.

## Personalized server state

START_GAME sends the existing `ROOM_STATE` notification, then a separate `GAME_STATE` to each connected member. Each successful gameplay action sends updated personalized state. Illegal actions do not broadcast. A broken socket does not undo a valid move or stop sends to the other players.

`GameStateMessageFactory` validates the viewer's membership in both Room and GameState. It copies visible cards into `GameCard(id, type, value)` DTOs and resolves profiles from PlayerStore. Raw Room/GameState objects are not sent.

`GAME_STATE` contains:

- `type`, `roomId`, `playerCount`, `currentPlayerId`, `winnerPlayerId`, `drawPileCount`.
- `buildingPiles`: four `{topCard, size}` entries.
- `localPlayer`: `{playerId, playerNumber, username, avatar, hand, stockTopCard, stockCount, discardPiles}`. Hand is complete. The four local discard lists are complete and top-first. Stock has only its public top and count.
- `opponents`: `{playerId, playerNumber, username, avatar, handCount, stockTopCard, stockCount, discardTopCards}`. Opponents keep their original relative order. Each has four discard tops, without history.

`playerCount` is the actual GameState participant count, including in ANY rooms. Each `playerNumber` is its original GameState index + 1. Opponents stay in original order minus the viewer, without rotation or renumbering. Reconnecting keeps the same number.

Empty tops are `null`; empty local discard piles are empty lists. Opponent hands, hidden Stock cards, opponent discard histories, Draw contents and completed/recycle cards are never included. RoomStateMessage continues selecting only lobby metadata, player profiles, readiness, host and chat.

## In-game reconnect

An IN_GAME socket close preserves the Room member, GamePlayerState and cards indefinitely in this v1. The waiting-lobby 15-second timer does not remove game seats. There is no automatic forfeit or turn timeout.

The same session reconnects to the same room URL. Existing membership is required; new players cannot join an active game. The connection replaces the player's old socket, receives ROOM_STATE, then receives only that player's current personalized GAME_STATE. Reconnect does not create a game, redeal, draw, start a turn, or change the current player. Winner state is restored too.

Late messages or close events from the old socket cannot affect the replacement because the service checks the actual current socket session. Explicit in-game leave remains rejected. Existing room chat continues during and after the game. Rematches, forfeits, persistence, spectators and bots are outside this v1.

## Self-hosting

Localhost and LAN development use ordinary HTTP/WebSockets on port 7070. For public hosting, put Javalin behind Caddy or nginx with HTTPS/WSS. Configure the proxy to forward WebSocket upgrades and enforce your chosen public origin. Public deployments should also configure secure/HTTP-only/SameSite session cookies and an explicit WebSocket origin allowlist appropriate to their domain. Javalin does not terminate TLS in this project.
