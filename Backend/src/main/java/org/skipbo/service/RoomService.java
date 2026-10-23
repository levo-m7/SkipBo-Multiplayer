package org.skipbo.service;

import io.javalin.websocket.WsContext;
import org.skipbo.dto.room.ChatMessage;
import org.skipbo.dto.room.CreateRoomRequest;
import org.skipbo.dto.websocket.LobbyPlayer;
import org.skipbo.dto.websocket.RoomListMessage;
import org.skipbo.dto.websocket.RoomStateMessage;
import org.skipbo.dto.websocket.RoomSummary;
import org.skipbo.dto.websocket.game.GameStateMessage;
import org.skipbo.model.Player;
import org.skipbo.model.game.GameState;
import org.skipbo.model.room.Room;
import org.skipbo.model.room.RoomState;
import org.skipbo.store.PlayerStore;
import org.skipbo.store.RoomStore;
import org.skipbo.websocket.GlobalRoomConnections;
import org.skipbo.websocket.RoomConnections;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class RoomService implements AutoCloseable
{
    private final GameService gameService = new GameService();
    private final GameStateMessageFactory gameStateMessageFactory;
    private final RoomStore roomStore;
    private final PlayerStore playerStore;
    private final RoomConnections roomConnections;
    private final GlobalRoomConnections globalRoomConnections;
    private final ScheduledThreadPoolExecutor scheduler;
    private final long disconnectGraceMillis;
    private final Map<String, PendingDisconnect> pendingDisconnects = new HashMap<>();
    private final Map<String, Long> lastChatTimes = new HashMap<>();

    private static class PendingDisconnect
    {
        private final String roomId;
        private ScheduledFuture<?> future;

        private PendingDisconnect(String roomId)
        {
            this.roomId = roomId;
        }
    }

    public RoomService(RoomStore roomStore, PlayerStore playerStore, RoomConnections roomConnections, GlobalRoomConnections globalRoomConnections)
    {
        this(roomStore, playerStore, roomConnections, globalRoomConnections, new ScheduledThreadPoolExecutor(1), 15000);
    }

    public RoomService(RoomStore roomStore, PlayerStore playerStore, RoomConnections roomConnections, GlobalRoomConnections globalRoomConnections, ScheduledThreadPoolExecutor scheduler, long disconnectGraceMillis)
    {
        this.roomStore = roomStore;
        this.playerStore = playerStore;
        this.gameStateMessageFactory = new GameStateMessageFactory(playerStore);
        this.roomConnections = roomConnections;
        this.globalRoomConnections = globalRoomConnections;
        this.scheduler = scheduler;
        this.disconnectGraceMillis = disconnectGraceMillis;
        scheduler.setRemoveOnCancelPolicy(true);
    }

    // Membership checks and changes share this monitor, including reconnect and timeout callbacks.
    public synchronized Room createRoom(String hostId, CreateRoomRequest request)
    {
        if (request == null || request.roomName() == null)
        {
            throw new IllegalArgumentException("Invalid room name");
        }

        String roomName = request.roomName().trim();
        Integer playerTarget = request.playerTarget();

        if (roomName.isEmpty() || roomName.length() > 20)
        {
            throw new IllegalArgumentException("Invalid room name");
        }

        if (playerTarget == null || playerTarget < 1 || playerTarget > 6)
        {
            throw new IllegalArgumentException("Invalid player target");
        }

        cleanupPlayerFromOtherRooms(hostId, null);

        Room room = new Room(UUID.randomUUID().toString(), roomName, hostId, playerTarget);
        roomStore.save(room);
        // Also clean up a created room if its creator never opens the lobby socket.
        scheduleDisconnect(hostId, room);
        broadcastGlobalRoomList();
        return room;
    }

    public synchronized Room getRoom(String roomId)
    {
        return roomStore.findById(roomId);
    }

    private Room requireRoom(String roomId)
    {
        Room room = roomStore.findById(roomId);

        if (room == null)
        {
            throw new IllegalArgumentException("Room not found");
        }

        return room;
    }

    private Room requireMember(String playerId, String roomId)
    {
        Room room = requireRoom(roomId);

        if (!room.hasPlayer(playerId))
        {
            throw new IllegalStateException("Player is not in this room");
        }

        return room;
    }

    public synchronized Room joinRoom(String playerId, String roomId)
    {
        Room room = requireRoom(roomId);

        if (room.getState() == RoomState.CLOSED)
        {
            throw new IllegalStateException("Room is not waiting");
        }

        if (!room.hasPlayer(playerId) && (room.getState() != RoomState.WAITING || room.isFull()))
        {
            throw new IllegalStateException("Room is full or is not waiting");
        }

        cleanupPlayerFromOtherRooms(playerId, roomId);

        if (room.hasPlayer(playerId))
        {
            return room;
        }

        if (!room.addPlayer(playerId))
        {
            throw new IllegalStateException("Room is full or is not waiting");
        }

        scheduleDisconnect(playerId, room);
        broadcastGlobalRoomList();
        return room;
    }

    private void cleanupPlayerFromOtherRooms(String playerId, String targetRoomId)
    {
        for (Room room : roomStore.getAllRooms())
        {
            if (room.getId().equals(targetRoomId) || !room.hasPlayer(playerId))
            {
                continue;
            }

            if (room.getState() == RoomState.IN_GAME)
            {
                leaveGame(playerId, room.getId());
            }

            removePlayerFromRoom(playerId, room);
        }
    }

    public synchronized void connectPlayer(String playerId, String roomId, WsContext ctx)
    {
        Room room = joinRoom(playerId, roomId);
        roomConnections.addConnection(roomId, playerId, ctx);
        cancelDisconnect(playerId);
        broadcastRoomState(room);

        if (room.getState() == RoomState.IN_GAME)
        {
            sendGameState(room, playerId);
        }
    }

    public synchronized boolean isCurrentConnection(String playerId, String roomId, WsContext ctx)
    {
        return roomConnections.isCurrentConnection(roomId, playerId, ctx);
    }

    public synchronized void disconnectPlayer(String playerId, String roomId, WsContext ctx)
    {
        if (!roomConnections.removeConnection(roomId, playerId, ctx))
        {
            return;
        }

        Room room = roomStore.findById(roomId);

        if (room != null && room.hasPlayer(playerId))
        {
            scheduleDisconnect(playerId, room);
        }
    }

    private void cancelDisconnect(String playerId)
    {
        PendingDisconnect pendingDisconnect = pendingDisconnects.remove(playerId);

        if (pendingDisconnect != null)
        {
            pendingDisconnect.future.cancel(false);
        }
    }

    private void scheduleDisconnect(String playerId, Room room)
    {
        if (room.getState() != RoomState.WAITING || roomConnections.hasConnection(room.getId(), playerId))
        {
            return;
        }

        cancelDisconnect(playerId);
        PendingDisconnect pendingDisconnect = new PendingDisconnect(room.getId());
        pendingDisconnects.put(playerId, pendingDisconnect);
        pendingDisconnect.future = scheduler.schedule(() -> expireDisconnect(playerId, pendingDisconnect), disconnectGraceMillis, TimeUnit.MILLISECONDS);
    }

    private synchronized void expireDisconnect(String playerId, PendingDisconnect pendingDisconnect)
    {
        if (pendingDisconnects.get(playerId) != pendingDisconnect)
        {
            return;
        }

        pendingDisconnects.remove(playerId);
        Room room = roomStore.findById(pendingDisconnect.roomId);

        // In-game seats must survive a socket close; their future timeout policy is separate.
        if (room == null || room.getState() != RoomState.WAITING || !room.hasPlayer(playerId)
                || roomConnections.hasConnection(room.getId(), playerId))
        {
            return;
        }

        try
        {
            leaveRoom(playerId, room.getId());
        }
        catch (Exception exception)
        {
            System.err.println("Could not expire disconnected lobby player: " + playerId);
            exception.printStackTrace();
        }
    }

    public synchronized Room toggleReady(String playerId, String roomId)
    {
        Room room = requireMember(playerId, roomId);
        room.toggleReady(playerId);
        broadcastRoomState(room);
        return room;
    }

    public synchronized Room leaveRoom(String playerId, String roomId)
    {
        Room room = requireMember(playerId, roomId);

        if (room.getState() != RoomState.WAITING)
        {
            throw new IllegalStateException("Cannot leave room while game is running");
        }

        return removePlayerFromRoom(playerId, room);
    }

    private Room removePlayerFromRoom(String playerId, Room room)
    {
        String roomId = room.getId();
        WsContext connection = roomConnections.getConnection(roomId, playerId);

        if (connection != null)
        {
            roomConnections.removeConnection(roomId, playerId, connection);
        }

        room.removePlayer(playerId);
        cancelDisconnect(playerId);
        lastChatTimes.remove(playerId);

        if (room.getPlayerCount() == 0)
        {
            roomStore.remove(roomId);
        }
        else
        {
            broadcastRoomState(room);
        }

        broadcastGlobalRoomList();
        return room;
    }

    public synchronized void leaveGame(String playerId, String roomId)
    {
        Room room = requireMember(playerId, roomId);
        room.returnToWaiting();
        broadcastRoomState(room);
        broadcastGlobalRoomList();
    }

    private Room requireEditableProfile(String playerId)
    {
        Room room = roomStore.findByPlayerId(playerId);

        if (room != null && (room.getState() != RoomState.WAITING || room.isReady(playerId)))
        {
            throw new IllegalStateException("Profile cannot be changed while ready or outside a waiting lobby");
        }

        return room;
    }

    public synchronized void updateUsername(Player player, String username)
    {
        if (username == null || !username.matches("^[A-Za-z0-9_-]{1,8}$"))
        {
            throw new IllegalArgumentException("Invalid username");
        }

        Room room = requireEditableProfile(player.getId());
        player.setUsername(username);

        if (room != null)
        {
            broadcastRoomState(room);
        }
    }

    public synchronized void updateAvatar(Player player, Integer avatar)
    {
        if (avatar == null || avatar < 1 || avatar > 10)
        {
            throw new IllegalArgumentException("Invalid avatar");
        }

        Room room = requireEditableProfile(player.getId());
        player.setAvatar(avatar);

        if (room != null)
        {
            broadcastRoomState(room);
        }
    }

    public synchronized boolean sendChatMessage(Player player, String roomId, String text)
    {
        Room room = requireMember(player.getId(), roomId);

        if (text == null)
        {
            throw new IllegalArgumentException("Invalid chat message");
        }

        text = text.trim();

        if (text.isEmpty() || text.length() > 200)
        {
            throw new IllegalArgumentException("Invalid chat message");
        }

        long now = System.nanoTime();
        Long lastChatTime = lastChatTimes.get(player.getId());

        if (lastChatTime != null && now - lastChatTime < TimeUnit.MILLISECONDS.toNanos(250))
        {
            return false;
        }

        lastChatTimes.put(player.getId(), now);
        ChatMessage message = new ChatMessage(UUID.randomUUID().toString(), player.getId(), player.getUsername(), player.getAvatar(), text);
        room.addChatMessage(message);
        broadcastRoomState(room);
        return true;
    }


    public synchronized void startRoom(String playerId, String roomId)
    {
        Room room = requireMember(playerId, roomId);

        if (!playerId.equals(room.getHostId()))
        {
            throw new IllegalStateException("Only the host can start the room");
        }

        synchronized (room)
        {
            if (!room.canStart() || room.getGameState() != null)
            {
                throw new IllegalStateException("Room cannot start yet");
            }

            List<String> playerIds = room.getPlayerIds();
            GameState gameState = gameService.createGame(playerIds);
            gameService.startTurn(gameState);
            room.setGameState(gameState);
            room.startGame();
        }

        for (String memberId : room.getPlayerIds())
        {
            cancelDisconnect(memberId);
        }

        broadcastRoomState(room);
        broadcastGlobalRoomList();
        broadcastGameState(room);
    }

    private Room requireGameRoom(String playerId, String roomId)
    {
        Room room = requireMember(playerId, roomId);

        if (room.getState() != RoomState.IN_GAME || room.getGameState() == null)
        {
            throw new IllegalStateException("Room has no running game");
        }

        return room;
    }

    public synchronized boolean playHandCard(String playerId, String roomId, Integer cardId, Integer buildingPileIndex)
    {
        Room room = requireGameRoom(playerId, roomId);

        if (cardId == null || buildingPileIndex == null)
        {
            return false;
        }

        if (!gameService.playCardFromHand(room.getGameState(), playerId, cardId, buildingPileIndex))
        {
            return false;
        }

        broadcastGameState(room);
        return true;
    }

    public synchronized boolean playStockCard(String playerId, String roomId, Integer buildingPileIndex)
    {
        Room room = requireGameRoom(playerId, roomId);

        if (buildingPileIndex == null)
        {
            return false;
        }

        if (!gameService.playCardFromStock(room.getGameState(), playerId, buildingPileIndex))
        {
            return false;
        }

        broadcastGameState(room);
        return true;
    }

    public synchronized boolean playDiscardCard(String playerId, String roomId, Integer discardPileIndex, Integer buildingPileIndex)
    {
        Room room = requireGameRoom(playerId, roomId);

        if (discardPileIndex == null || buildingPileIndex == null)
        {
            return false;
        }

        if (!gameService.playCardFromDiscard(room.getGameState(), playerId, discardPileIndex, buildingPileIndex))
        {
            return false;
        }

        broadcastGameState(room);
        return true;
    }

    public synchronized boolean discardHandCard(String playerId, String roomId, Integer cardId, Integer discardPileIndex)
    {
        Room room = requireGameRoom(playerId, roomId);

        if (cardId == null || discardPileIndex == null)
        {
            return false;
        }

        if (!gameService.discardCardFromHand(room.getGameState(), playerId, cardId, discardPileIndex))
        {
            return false;
        }

        broadcastGameState(room);
        return true;
    }

    public synchronized void closeRoom(String playerId, String roomId)
    {
        Room room = requireMember(playerId, roomId);

        if (!playerId.equals(room.getHostId()))
        {
            throw new IllegalStateException("Only the host can close the room");
        }

        room.close();
        broadcastRoomState(room);

        for (String memberId : room.getPlayerIds())
        {
            cancelDisconnect(memberId);
            lastChatTimes.remove(memberId);
            WsContext connection = roomConnections.getConnection(roomId, memberId);

            if (connection != null)
            {
                roomConnections.removeConnection(roomId, memberId, connection);

                try
                {
                    connection.closeSession(1000, "Room closed");
                }
                catch (Exception exception)
                {
                    System.err.println("Could not close room socket: " + exception.getMessage());
                }
            }

            room.removePlayer(memberId);
        }

        roomStore.remove(roomId);
        broadcastGlobalRoomList();
    }

    public synchronized List<RoomSummary> getRoomSummaries()
    {
        List<RoomSummary> roomSummaries = new ArrayList<>();

        for (Room room : roomStore.getAllRooms())
        {
            synchronized (room)
            {
                if (room.getState() == RoomState.WAITING && !room.isFull())
                {
                    RoomSummary summary = new RoomSummary(room.getId(), room.getId().substring(0, 8), room.getName(), room.getPlayerCount(), room.getPlayerTarget(), room.getState().name(), false);
                    roomSummaries.add(summary);
                }
            }
        }

        return roomSummaries;
    }

    public synchronized void broadcastGlobalRoomList()
    {
        globalRoomConnections.broadcast(new RoomListMessage("ROOM_LIST", getRoomSummaries()));
    }

    public synchronized void broadcastRoomState(Room room)
    {
        List<LobbyPlayer> players = new ArrayList<>();
        RoomStateMessage message;

        synchronized (room)
        {
            for (String playerId : room.getPlayerIds())
            {
                Player player = playerStore.findById(playerId);

                if (player != null)
                {
                    LobbyPlayer lobbyPlayer = new LobbyPlayer(player.getId(), player.getUsername(), player.getAvatar(), room.isReady(playerId), playerId.equals(room.getHostId()));
                    players.add(lobbyPlayer);
                }
            }

            message = new RoomStateMessage("ROOM_STATE", room.getId(), room.getId().substring(0, 8), room.getName(), room.getPlayerTarget(), room.getState().name(), players, room.getChatMessages());
        }

        List<String> disconnectedPlayers = roomConnections.broadcast(room.getId(), message);

        for (String playerId : disconnectedPlayers)
        {
            scheduleDisconnect(playerId, room);
        }
    }

    public synchronized void broadcastGameState(Room room)
    {
        if (room.getState() != RoomState.IN_GAME || room.getGameState() == null)
        {
            return;
        }

        for (String playerId : room.getPlayerIds())
        {
            sendGameState(room, playerId);
        }
    }

    private void sendGameState(Room room, String playerId)
    {
        WsContext connection = roomConnections.getConnection(room.getId(), playerId);

        if (connection == null)
        {
            return;
        }

        try
        {
            GameStateMessage message = gameStateMessageFactory.createGameStateMessage(room, playerId);
            connection.send(message);
        }
        catch (Exception exception)
        {
            System.err.println("Game state send failed | Room: " + room.getId() + " | Player: " + playerId);
            exception.printStackTrace();
            disconnectPlayer(playerId, room.getId(), connection);
        }
    }

    public synchronized void close()
    {
        for (PendingDisconnect pendingDisconnect : pendingDisconnects.values())
        {
            pendingDisconnect.future.cancel(false);
        }

        pendingDisconnects.clear();
        lastChatTimes.clear();
        scheduler.shutdownNow();
    }
}
