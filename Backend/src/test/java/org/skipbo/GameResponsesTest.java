package org.skipbo;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.skipbo.dto.websocket.GameActionRejectedMessage;
import org.skipbo.dto.websocket.RoomStateMessage;
import org.skipbo.handler.WebSocketHandler;
import org.skipbo.model.card.Card;
import org.skipbo.model.card.CardType;
import org.skipbo.model.game.GamePlayerState;
import org.skipbo.model.game.GameState;
import org.skipbo.model.room.Room;
import org.skipbo.model.room.RoomState;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.skipbo.LobbyTestSupport.*;

public class GameResponsesTest
{
    private Room runningRoom(Lobby lobby)
    {
        Room room = lobby.room(lobby.player("P1"), 1);
        for (String id : List.of("P2", "P3", "P4"))
        {
            lobby.service.joinRoom(lobby.player(id).getId(), room.getId());
        }
        for (String id : room.getPlayerIds())
        {
            lobby.service.toggleReady(id, room.getId());
        }
        lobby.service.startRoom("P1", room.getId());
        return room;
    }

    @Test
    void rejectedMovesImmediatelyReplyOnlyToActorWithoutMutation() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = runningRoom(lobby);
            GameState game = new GameState(room.getPlayerIds());
            game.setCurrentPlayerId("P1");
            GamePlayerState player = game.getPlayers().getFirst();
            Card handCard = new Card(9001, CardType.NUMBER, 9);
            Card stockCard = new Card(9002, CardType.NUMBER, 9);
            Card discardCard = new Card(9003, CardType.NUMBER, 9);
            player.getHand().add(handCard);
            player.getStockPile().add(stockCard);
            player.getDiscardPiles().get(0).add(discardCard);
            room.setGameState(game);
            TestSocket actor = lobby.connect(lobby.players.findById("P1"), room);
            TestSocket other = lobby.connect(lobby.players.findById("P2"), room);
            int otherMessages = other.messages.size();
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            for (String type : List.of("PLAY_HAND_CARD", "PLAY_STOCK_CARD", "PLAY_DISCARD_CARD", "DISCARD_HAND_CARD"))
            {
                int before = actor.messages.size();
                handler.onMessage(actor.message("{\"type\":\"" + type + "\",\"cardId\":999,\"buildingPileIndex\":0,\"discardPileIndex\":0}"));
                assertEquals(before + 1, actor.messages.size());
                GameActionRejectedMessage message = JSON.fromJsonString(actor.lastMessage(), GameActionRejectedMessage.class);
                assertEquals("GAME_ACTION_REJECTED", message.type());
                assertEquals(type, message.actionType());
                assertEquals("ILLEGAL_MOVE", message.reason());
                assertEquals(otherMessages, other.messages.size());
                assertEquals(0, actor.closeCode);
                assertEquals(List.of(handCard), player.getHand());
                assertEquals(List.of(stockCard), player.getStockPile());
                assertEquals(List.of(discardCard), player.getDiscardPiles().get(0));
                assertTrue(game.getBuildingPiles().get(0).isEmpty());
                assertEquals("P1", game.getCurrentPlayerId());
                assertNull(game.getWinnerPlayerId());
            }
        }
    }

    @Test
    void outOfTurnAndNotInGameMovesReceiveImmediateRejections() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = runningRoom(lobby);
            TestSocket actor = lobby.connect(lobby.players.findById("P2"), room);
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            String action = "{\"type\":\"PLAY_STOCK_CARD\",\"buildingPileIndex\":0}";
            int before = actor.messages.size();
            handler.onMessage(actor.message(action));
            assertEquals(before + 1, actor.messages.size());
            assertTrue(actor.lastMessage().contains("GAME_ACTION_REJECTED"));
            assertEquals("P1", room.getGameState().getCurrentPlayerId());
            lobby.service.leaveGame("P1", room.getId());
            before = actor.messages.size();
            handler.onMessage(actor.message(action));
            assertEquals(before + 1, actor.messages.size());
            assertTrue(actor.lastMessage().contains("GAME_ACTION_REJECTED"));
            assertEquals(RoomState.WAITING, room.getState());
            assertNull(room.getGameState());
            assertEquals(0, actor.closeCode);
        }
    }

    @Test
    void successfulMoveStillAcknowledgesWithPersonalizedGameState() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = runningRoom(lobby);
            GamePlayerState player = room.getGameState().getPlayers().getFirst();
            player.getHand().clear();
            player.getHand().add(new Card(9001, CardType.NUMBER, 1));
            player.getHand().add(new Card(9002, CardType.NUMBER, 9));
            TestSocket actor = lobby.connect(lobby.players.findById("P1"), room);
            TestSocket other = lobby.connect(lobby.players.findById("P2"), room);
            int actorMessages = actor.messages.size();
            int otherMessages = other.messages.size();
            new WebSocketHandler(lobby.service, lobby.sessions).onMessage(actor.message("{\"type\":\"PLAY_HAND_CARD\",\"cardId\":9001,\"buildingPileIndex\":0}"));
            assertEquals(actorMessages + 1, actor.messages.size());
            assertEquals(otherMessages + 1, other.messages.size());
            assertEquals("GAME_STATE", JSON.getMapper().readTree(actor.lastMessage()).get("type").asText());
            assertEquals("GAME_STATE", JSON.getMapper().readTree(other.lastMessage()).get("type").asText());
            assertNotEquals(actor.lastMessage(), other.lastMessage());
            assertEquals(9001, room.getGameState().getBuildingPiles().get(0).get(0).id());
        }
    }

    @Test
    void missingRoomIsReportedBeforeCloseEvenWhenRestartLostPlayerIdentity() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            lobby.player("known");
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            for (String playerId : new String[]{"known", "lost-player", null})
            {
                TestSocket socket = new TestSocket(playerId, "old-room-id");
                handler.onConnect(socket.connect());
                assertEquals(1, socket.messages.size());
                JsonNode message = JSON.getMapper().readTree(socket.lastMessage());
                assertEquals(1, message.size());
                assertEquals("ROOM_NOT_FOUND", message.get("type").asText());
                assertEquals(4004, socket.closeCode);
            }
        }
    }

    @Test
    void messageToRemovedRoomGetsRoomNotFound() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = runningRoom(lobby);
            TestSocket socket = lobby.connect(lobby.players.findById("P1"), room);
            lobby.rooms.remove(room.getId());
            new WebSocketHandler(lobby.service, lobby.sessions).onMessage(socket.message("{\"type\":\"PLAY_STOCK_CARD\",\"buildingPileIndex\":0}"));
            assertEquals("ROOM_NOT_FOUND", JSON.getMapper().readTree(socket.lastMessage()).get("type").asText());
            assertEquals(4004, socket.closeCode);
        }
    }

    @Test
    void temporaryDisconnectDoesNotReportMissingRoomOrCancelGame() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = runningRoom(lobby);
            GameState original = room.getGameState();
            TestSocket socket = lobby.connect(lobby.players.findById("P2"), room);
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            int before = socket.messages.size();
            handler.onClose(socket.closed());
            assertEquals(before, socket.messages.size());
            assertSame(original, room.getGameState());
            assertEquals(RoomState.IN_GAME, room.getState());
            assertTrue(room.hasPlayer("P2"));
            TestSocket restored = new TestSocket("P2", room.getId());
            handler.onConnect(restored.connect());
            assertEquals("GAME_STATE", JSON.getMapper().readTree(restored.lastMessage()).get("type").asText());
            assertEquals(0, restored.closeCode);
            for (String message : restored.messages)
            {
                assertFalse(message.contains("ROOM_NOT_FOUND"));
            }
        }
    }

    @Test
    void anyMemberCanLeaveGameToResetSameRoomAndBroadcastLobbyState() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = runningRoom(lobby);
            List<String> order = room.getPlayerIds();
            GameState original = room.getGameState();
            String roomId = room.getId();
            String name = room.getName();
            List<TestSocket> sockets = new ArrayList<>();
            for (String id : order)
            {
                sockets.add(lobby.connect(lobby.players.findById(id), room));
            }
            lobby.service.sendChatMessage(lobby.players.findById("P1"), roomId, "hello");
            List<?> chat = room.getChatMessages();
            TestSocket observer = new TestSocket(null, "global");
            lobby.globalConnections.addConnection(observer.connect());
            lobby.service.broadcastGlobalRoomList();
            assertTrue(observer.lastMessage().contains("\"rooms\":[]"));
            new WebSocketHandler(lobby.service, lobby.sessions).onMessage(sockets.get(2).message("{\"type\":\"LEAVE_GAME\"}"));
            assertSame(room, lobby.service.getRoom(roomId));
            assertEquals(RoomState.WAITING, room.getState());
            assertNull(room.getGameState());
            assertNull(original.getWinnerPlayerId());
            assertEquals(order, room.getPlayerIds());
            assertEquals("P1", room.getHostId());
            assertEquals(name, room.getName());
            assertEquals(roomId, room.getId());
            assertEquals(chat, room.getChatMessages());
            assertEquals(4, lobby.connections.getConnectionCount(roomId));
            for (int i = 0; i < order.size(); i++)
            {
                assertTrue(room.hasPlayer(order.get(i)));
                assertFalse(room.isReady(order.get(i)));
                assertEquals(0, sockets.get(i).closeCode);
                RoomStateMessage message = JSON.fromJsonString(sockets.get(i).lastMessage(), RoomStateMessage.class);
                assertEquals("ROOM_STATE", message.type());
                assertEquals("WAITING", message.state());
                assertEquals(order.size(), message.players().size());
                for (int j = 0; j < order.size(); j++)
                {
                    assertEquals(order.get(j), message.players().get(j).id());
                    assertFalse(message.players().get(j).ready());
                }
            }
            assertEquals(1, lobby.service.getRoomSummaries().size());
            assertTrue(observer.lastMessage().contains(roomId));
            assertTrue(observer.lastMessage().contains("WAITING"));
            assertTrue(lobby.scheduler.getQueue().isEmpty());
        }
    }

    @Test
    void staleSocketAndNonMemberCannotCancelGame() throws Exception
    {
        try (Lobby lobby = new Lobby())
        {
            Room room = runningRoom(lobby);
            TestSocket old = lobby.connect(lobby.players.findById("P2"), room);
            lobby.connect(lobby.players.findById("P2"), room);
            WebSocketHandler handler = new WebSocketHandler(lobby.service, lobby.sessions);
            handler.onMessage(old.message("{\"type\":\"LEAVE_GAME\"}"));
            TestSocket outsider = new TestSocket(lobby.player("outsider").getId(), room.getId());
            handler.onMessage(outsider.message("{\"type\":\"LEAVE_GAME\"}"));
            expectRejected(() -> lobby.service.leaveGame("outsider", room.getId()));
            assertEquals(RoomState.IN_GAME, room.getState());
            assertNotNull(room.getGameState());
        }
    }
}
