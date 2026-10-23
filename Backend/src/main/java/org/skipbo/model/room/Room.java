package org.skipbo.model.room;

import org.skipbo.dto.room.ChatMessage;
import org.skipbo.model.game.GameState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Room
{
    private static final int MIN_PLAYERS = 2;

    private static final int MAX_PLAYERS = 6;

    private final String id;

    private final String name;

    private String hostId;

    private final int playerTarget;

    private final List<String> playerIds;

    private final Map<String, Boolean> readyStates;

    private final List<ChatMessage> chatMessages;

    private RoomState state;

    private GameState gameState;

    public Room(String id, String name, String hostId, int playerTarget)
    {
        this.id = id;
        this.name = name;
        this.hostId = hostId;
        this.playerTarget = playerTarget;
        this.playerIds = new ArrayList<>();
        this.readyStates = new HashMap<>();
        this.chatMessages = new ArrayList<>();
        this.state = RoomState.WAITING;
        addPlayer(hostId);
    }

    public String getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    public synchronized String getHostId()
    {
        return hostId;
    }

    public int getPlayerTarget()
    {
        return playerTarget;
    }

    public synchronized RoomState getState()
    {
        return state;
    }

    public synchronized GameState getGameState()
    {
        return gameState;
    }

    public synchronized void setGameState(GameState gameState)
    {
        this.gameState = gameState;
    }

    public synchronized List<String> getPlayerIds()
    {
        return List.copyOf(playerIds);
    }

    public synchronized int getPlayerCount()
    {
        return playerIds.size();
    }

    public synchronized boolean hasPlayer(String playerId)
    {
        return playerIds.contains(playerId);
    }

    public synchronized boolean addPlayer(String playerId)
    {
        if (state != RoomState.WAITING)
        {
            return false;
        }

        if (hasPlayer(playerId))
        {
            return false;
        }

        if (isFull())
        {
            return false;
        }

        playerIds.add(playerId);

        readyStates.put(playerId, false);

        return true;
    }

    public synchronized boolean removePlayer(String playerId)
    {
        boolean removed = playerIds.remove(playerId);

        if (removed)
        {
            readyStates.remove(playerId);

            if (playerId.equals(hostId))
            {
                hostId = null;

                if (!playerIds.isEmpty())
                {
                    hostId = playerIds.getFirst();
                }
            }
        }
        return removed;
    }

    public synchronized boolean setReady(String playerId, boolean ready)
    {
        if (state != RoomState.WAITING || !hasPlayer(playerId))
        {
            return false;
        }

        readyStates.put(playerId, ready);
        return true;
    }

    public synchronized void toggleReady(String playerId)
    {
        if (state != RoomState.WAITING)
        {
            throw new IllegalStateException("Room is not waiting");
        }

        if (!hasPlayer(playerId))
        {
            throw new IllegalArgumentException("Player is not in this room");
        }

        boolean currentReadyState = isReady(playerId);

        readyStates.put(playerId, !currentReadyState);
    }

    public synchronized boolean isReady(String playerId)
    {
        return readyStates.getOrDefault(playerId, false);
    }

    public synchronized boolean areAllPlayersReady()
    {
        for (String playerId : playerIds)
        {
            if (!isReady(playerId)) { return false; }
        }
        return true;
    }

    public synchronized boolean isFull()
    {
        if (playerTarget == 1) { return playerIds.size() >= MAX_PLAYERS; }
        return playerIds.size() >= playerTarget;
    }

    public synchronized boolean canStart()
    {
        if (state != RoomState.WAITING)
        {
            return false;
        }

        if (playerIds.size() < MIN_PLAYERS || playerIds.size() > MAX_PLAYERS)
        {
            return false;
        }

        if (!areAllPlayersReady())
        {
            return false;
        }

        if (playerTarget == 1)
        {
            return true;
        }

        return playerIds.size() == playerTarget;
    }

    public synchronized void startGame()
    {
        if (!canStart())
        {
            throw new IllegalStateException("Room cannot start yet");
        }

        state = RoomState.IN_GAME;
    }

    public synchronized void returnToWaiting()
    {
        if (state != RoomState.IN_GAME)
        {
            throw new IllegalStateException("Room is not in game");
        }

        gameState = null;
        state = RoomState.WAITING;

        for (String playerId : playerIds)
        {
            readyStates.put(playerId, false);
        }
    }

    public synchronized void close()
    {
        state = RoomState.CLOSED;
    }

    public synchronized void addChatMessage(ChatMessage message)
    {
        if (message == null || !hasPlayer(message.playerId()) || message.message() == null)
        {
            throw new IllegalArgumentException("Invalid chat message");
        }

        String text = message.message().trim();

        if (text.isEmpty() || text.length() > 200)
        {
            throw new IllegalArgumentException("Invalid chat message");
        }

        if (chatMessages.size() >= 100)
        {
            chatMessages.removeFirst();
        }

        chatMessages.add(message);
    }

    public synchronized List<ChatMessage> getChatMessages()
    {
        return List.copyOf(chatMessages);
    }
}
