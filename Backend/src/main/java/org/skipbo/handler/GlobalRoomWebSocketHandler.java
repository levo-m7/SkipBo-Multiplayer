package org.skipbo.handler;

import io.javalin.websocket.WsCloseContext;
import io.javalin.websocket.WsConnectContext;
import io.javalin.websocket.WsErrorContext;
import org.skipbo.dto.websocket.RoomListMessage;
import org.skipbo.service.RoomService;
import org.skipbo.websocket.GlobalRoomConnections;

public class GlobalRoomWebSocketHandler
{
    private final RoomService roomService;
    private final GlobalRoomConnections globalRoomConnections;

    public GlobalRoomWebSocketHandler(RoomService roomService, GlobalRoomConnections globalRoomConnections)
    {
        this.roomService = roomService;
        this.globalRoomConnections = globalRoomConnections;
    }

    public void onConnect(WsConnectContext ctx)
    {
        globalRoomConnections.addConnection(ctx);

        synchronized (roomService)
        {
            try
            {
                ctx.send(new RoomListMessage("ROOM_LIST", roomService.getRoomSummaries()));
            }
            catch (Exception exception)
            {
                globalRoomConnections.removeConnection(ctx);
                System.err.println("Could not send initial room list");
                exception.printStackTrace();
            }
        }

        System.out.println("Global room-list WebSocket connected");
    }

    public void onClose(WsCloseContext ctx)
    {
        globalRoomConnections.removeConnection(ctx);

        System.out.println("Global room-list WebSocket disconnected");
    }

    public void onError(WsErrorContext ctx)
    {
        if (ctx.error() != null)
        {
            ctx.error().printStackTrace();
        }
    }
}