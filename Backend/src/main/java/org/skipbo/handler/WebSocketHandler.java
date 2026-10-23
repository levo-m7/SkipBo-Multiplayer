package org.skipbo.handler;

import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsCloseContext;
import io.javalin.websocket.WsConnectContext;
import io.javalin.websocket.WsErrorContext;
import io.javalin.websocket.WsMessageContext;
import org.skipbo.dto.websocket.RoomClientMessage;
import org.skipbo.dto.websocket.GameActionRejectedMessage;
import org.skipbo.dto.websocket.RoomNotFoundMessage;
import org.skipbo.model.Player;
import org.skipbo.model.room.Room;
import org.skipbo.service.RoomService;
import org.skipbo.service.SessionService;

public class WebSocketHandler
{
    private final RoomService roomService;
    private final SessionService sessionService;

    public WebSocketHandler(RoomService roomService, SessionService sessionService)
    {
        this.roomService = roomService;
        this.sessionService = sessionService;
    }

    public void onConnect(WsConnectContext ctx)
    {
        String roomId = ctx.pathParam("roomId");
        synchronized (roomService)
        {
            if (roomService.getRoom(roomId) == null)
            {
                sendRoomNotFound(ctx);
                return;
            }

            Player player = sessionService.getPlayer(ctx);

            if (player == null)
            {
                ctx.closeSession(4001, "Player not found");
                return;
            }

            try
            {
                roomService.connectPlayer(player.getId(), roomId, ctx);
            }
            catch (IllegalArgumentException exception)
            {
                ctx.closeSession(4004, "Room not found");
            }
            catch (IllegalStateException exception)
            {
                ctx.closeSession(4009, exception.getMessage());
            }
        }
    }

    public void onMessage(WsMessageContext ctx)
    {
        String roomId = ctx.pathParam("roomId");
        Player player = sessionService.getPlayer(ctx);

        if (player == null)
        {
            System.err.println("WebSocket message rejected: session player not found");
            rejectUnparsedGameAction(ctx);
            return;
        }

        RoomClientMessage message;

        try
        {
            message = ctx.messageAsClass(RoomClientMessage.class);
        }
        catch (Exception exception)
        {
            System.err.println("Invalid WebSocket JSON | Room: " + roomId);
            rejectUnparsedGameAction(ctx);
            return;
        }

        if (message == null || message.type() == null)
        {
            return;
        }

        // Check the socket and mutate under the same lock as reconnect and expiration.
        synchronized (roomService)
        {
            Room room = roomService.getRoom(roomId);

            if (room == null)
            {
                sendRoomNotFound(ctx);
                return;
            }

            if (!room.hasPlayer(player.getId())
                    || !roomService.isCurrentConnection(player.getId(), roomId, ctx))
            {
                System.err.println("WebSocket action rejected: missing membership or stale socket");
                if (isGameAction(message.type()))
                {
                    sendGameActionRejected(ctx, message.type());
                }
                return;
            }

            try
            {
                boolean accepted = true;

                switch (message.type())
                {
                    case "UPDATE_USERNAME" -> roomService.updateUsername(player, message.username());
                    case "UPDATE_AVATAR" -> roomService.updateAvatar(player, message.avatar());
                    case "START_GAME" -> roomService.startRoom(player.getId(), roomId);
                    case "PLAY_HAND_CARD" -> accepted = roomService.playHandCard(player.getId(), roomId, message.cardId(), message.buildingPileIndex());
                    case "PLAY_STOCK_CARD" -> accepted = roomService.playStockCard(player.getId(), roomId, message.buildingPileIndex());
                    case "PLAY_DISCARD_CARD" -> accepted = roomService.playDiscardCard(player.getId(), roomId, message.discardPileIndex(), message.buildingPileIndex());
                    case "DISCARD_HAND_CARD" -> accepted = roomService.discardHandCard(player.getId(), roomId, message.cardId(), message.discardPileIndex());
                    case "TOGGLE_READY" -> roomService.toggleReady(player.getId(), roomId);
                    case "CHAT_MESSAGE" -> roomService.sendChatMessage(player, roomId, message.message());
                    case "LEAVE_GAME" -> roomService.leaveGame(player.getId(), roomId);
                    case "LEAVE_ROOM" ->
                    {
                        roomService.leaveRoom(player.getId(), roomId);
                        ctx.send("{\"type\":\"LEFT_ROOM\"}");
                    }
                    default -> System.out.println("Unknown WebSocket message: " + message.type());
                }

                if (isGameAction(message.type()) && !accepted)
                {
                    sendGameActionRejected(ctx, message.type());
                }
            }
            catch (IllegalArgumentException | IllegalStateException exception)
            {
                System.err.println("WebSocket action rejected | Type: " + message.type() + " | Reason: " + exception.getMessage());
                if (isGameAction(message.type()))
                {
                    sendGameActionRejected(ctx, message.type());
                }
            }
            catch (Exception exception)
            {
                System.err.println("WebSocket action failed | Type: " + message.type() + " | Room: " + roomId);
                exception.printStackTrace();
            }
        }
    }

    private boolean isGameAction(String type)
    {
        return "PLAY_HAND_CARD".equals(type) || "PLAY_STOCK_CARD".equals(type) || "PLAY_DISCARD_CARD".equals(type) || "DISCARD_HAND_CARD".equals(type);
    }

    private void sendGameActionRejected(WsContext ctx, String actionType)
    {
        try
        {
            ctx.send(new GameActionRejectedMessage("GAME_ACTION_REJECTED", actionType, "ILLEGAL_MOVE"));
        }
        catch (Exception exception)
        {
            System.err.println("Could not send game action rejection");
        }
    }

    private void rejectUnparsedGameAction(WsMessageContext ctx)
    {
        try
        {
            JsonNode json = ctx.messageAsClass(JsonNode.class);

            if (json == null || !json.isObject())
            {
                return;
            }

            JsonNode type = json.get("type");

            if (type != null && type.isTextual() && isGameAction(type.asText()))
            {
                sendGameActionRejected(ctx, type.asText());
            }
        }
        catch (Exception exception)
        {
        }
    }

    private void sendRoomNotFound(WsContext ctx)
    {
        try
        {
            ctx.send(new RoomNotFoundMessage("ROOM_NOT_FOUND"));
            ctx.closeSession(4004, "Room not found");
        }
        catch (Exception exception)
        {
            System.err.println("Could not notify missing room");
        }
    }

    public void onClose(WsCloseContext ctx)
    {
        System.out.println("WebSocket closed | Code: " + ctx.status() + " | Reason: " + ctx.reason());
        Player player = sessionService.getPlayer(ctx);

        if (player != null)
        {
            roomService.disconnectPlayer(player.getId(), ctx.pathParam("roomId"), ctx);
        }
    }

    public void onError(WsErrorContext ctx)
    {
        System.err.println("WebSocket error | Room: " + ctx.pathParam("roomId"));

        if (ctx.error() != null)
        {
            ctx.error().printStackTrace();
        }
    }
}
