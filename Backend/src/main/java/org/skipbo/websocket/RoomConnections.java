package org.skipbo.websocket;

import io.javalin.websocket.WsContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RoomConnections
{
    private final Map<String, Map<String, WsContext>> connectionsByRoomId = new ConcurrentHashMap<>();

    public synchronized void addConnection(String roomId, String playerId, WsContext ctx)
    {
        Map<String, WsContext> roomConnections = connectionsByRoomId.get(roomId);

        if (roomConnections == null)
        {
            roomConnections = new ConcurrentHashMap<>();
            connectionsByRoomId.put(roomId, roomConnections);
        }

        roomConnections.put(playerId, ctx);
    }

    public synchronized boolean isCurrentConnection(String roomId, String playerId, WsContext ctx)
    {
        Map<String, WsContext> roomConnections = connectionsByRoomId.get(roomId);

        if (roomConnections == null)
        {
            return false;
        }

        WsContext connection = roomConnections.get(playerId);
        return connection != null && connection.session == ctx.session;
    }

    public synchronized WsContext getConnection(String roomId, String playerId)
    {
        Map<String, WsContext> roomConnections = connectionsByRoomId.get(roomId);

        if (roomConnections == null)
        {
            return null;
        }

        return roomConnections.get(playerId);
    }

    public synchronized boolean hasConnection(String roomId, String playerId)
    {
        Map<String, WsContext> roomConnections = connectionsByRoomId.get(roomId);
        return roomConnections != null && roomConnections.containsKey(playerId);
    }

    public synchronized boolean removeConnection(String roomId, String playerId, WsContext ctx)
    {
        if (!isCurrentConnection(roomId, playerId, ctx))
        {
            return false;
        }

        Map<String, WsContext> roomConnections = connectionsByRoomId.get(roomId);
        // Close/message contexts differ from the connect context, but share its Jetty session.
        roomConnections.remove(playerId);

        if (roomConnections.isEmpty())
        {
            connectionsByRoomId.remove(roomId);
        }

        return true;
    }

    public synchronized Collection<WsContext> getConnections(String roomId)
    {
        return List.copyOf(getRoomConnections(roomId).values());
    }

    private synchronized Map<String, WsContext> getRoomConnections(String roomId)
    {
        Map<String, WsContext> roomConnections = connectionsByRoomId.get(roomId);

        if (roomConnections == null)
        {
            return Map.of();
        }

        return new HashMap<>(roomConnections);
    }

    public synchronized int getConnectionCount(String roomId)
    {
        return getRoomConnections(roomId).size();
    }

    public List<String> broadcast(String roomId, Object message)
    {
        List<String> disconnectedPlayers = new ArrayList<>();
        Map<String, WsContext> roomConnections = getRoomConnections(roomId);

        for (Map.Entry<String, WsContext> entry : roomConnections.entrySet())
        {
            try
            {
                entry.getValue().send(message);
            }
            catch (Exception exception)
            {
                System.err.println("Room broadcast failed | Room: " + roomId + " | Player: " + entry.getKey());
                exception.printStackTrace();

                if (removeConnection(roomId, entry.getKey(), entry.getValue()))
                {
                    disconnectedPlayers.add(entry.getKey());
                }
            }
        }

        return disconnectedPlayers;
    }
}
