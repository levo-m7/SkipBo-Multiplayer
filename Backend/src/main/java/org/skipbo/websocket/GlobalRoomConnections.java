package org.skipbo.websocket;

import io.javalin.websocket.WsContext;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GlobalRoomConnections
{
    private final Set<WsContext> connections = ConcurrentHashMap.newKeySet();

    public void addConnection(WsContext ctx)
    {
        connections.add(ctx);
    }

    public void removeConnection(WsContext ctx)
    {
        connections.remove(ctx);
    }

    public void broadcast(Object message)
    {
        for (WsContext connection : connections)
        {
            try
            {
                connection.send(message);
            }
            catch (Exception exception)
            {
                System.err.println("Global room-list broadcast failed");
                exception.printStackTrace();
                connections.remove(connection);
            }
        }
    }

    public int getConnectionCount()
    {
        return connections.size();
    }
}